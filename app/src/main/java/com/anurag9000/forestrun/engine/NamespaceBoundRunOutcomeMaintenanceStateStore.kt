package com.anurag9000.forestrun.engine

import android.content.Context
import android.content.SharedPreferences
import com.anurag9000.forestrun.entities.EntityType

/**
 * Run-outcome recovery state permanently bound to one captured preference namespace.
 *
 * Maintenance recovery must remain stable even when debug or compatibility tooling
 * switches [SaveManager]'s active namespace after the maintenance instance exists.
 * This adapter mirrors the relevant SaveManager read/repair semantics while using
 * synchronous commits for explicit recovery writes.
 */
internal class NamespaceBoundRunOutcomeMaintenanceStateStore(
    context: Context,
    persistenceNamespace: String
) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        persistenceNamespace,
        Context.MODE_PRIVATE
    )

    fun loadBestDistanceM(): Float = prefs.getFloat(KEY_BEST_DISTANCE, 0f)

    fun loadForestMoodState(): ForestMoodState {
        val currentMood = prefs.getString(KEY_FOREST_MOOD, ForestMood.STEADY.name)?.let { raw ->
            runCatching { ForestMood.valueOf(raw) }.getOrDefault(ForestMood.STEADY)
        } ?: ForestMood.STEADY
        return ForestMoodState(
            currentMood = currentMood,
            moodStreak = prefs.getInt(KEY_FOREST_MOOD_STREAK, 0),
            totalRuns = prefs.getInt(KEY_FOREST_TOTAL_RUNS, 0),
            gentleRuns = prefs.getInt(KEY_FOREST_GENTLE_RUNS, 0),
            recklessRuns = prefs.getInt(KEY_FOREST_RECKLESS_RUNS, 0),
            fearfulRuns = prefs.getInt(KEY_FOREST_FEARFUL_RUNS, 0),
            steadyRuns = prefs.getInt(KEY_FOREST_STEADY_RUNS, 0)
        )
    }

    fun saveForestMoodState(state: ForestMoodState): Boolean =
        prefs.edit()
            .putString(KEY_FOREST_MOOD, state.currentMood.name)
            .putInt(KEY_FOREST_MOOD_STREAK, state.moodStreak.coerceAtLeast(0))
            .putInt(KEY_FOREST_TOTAL_RUNS, state.totalRuns.coerceAtLeast(0))
            .putInt(KEY_FOREST_GENTLE_RUNS, state.gentleRuns.coerceAtLeast(0))
            .putInt(KEY_FOREST_RECKLESS_RUNS, state.recklessRuns.coerceAtLeast(0))
            .putInt(KEY_FOREST_FEARFUL_RUNS, state.fearfulRuns.coerceAtLeast(0))
            .putInt(KEY_FOREST_STEADY_RUNS, state.steadyRuns.coerceAtLeast(0))
            .commit()

    fun loadReturnMomentState(): ReturnMomentState = ReturnMomentState(
        lastActiveAtMs = prefs.getLong(KEY_LAST_ACTIVE_AT_MS, 0L).coerceAtLeast(0L),
        lastGardenGreetingDay = prefs.getLong(
            KEY_LAST_GARDEN_GREETING_DAY,
            -1L
        ).coerceAtLeast(-1L),
        roughRunStreak = prefs.getInt(KEY_ROUGH_RUN_STREAK, 0).coerceAtLeast(0)
    )

    fun saveReturnMomentState(state: ReturnMomentState): Boolean {
        val previousRoughStreak = prefs.getInt(KEY_ROUGH_RUN_STREAK, 0).coerceAtLeast(0)
        val safeRoughStreak = when {
            state.roughRunStreak >= 0 -> state.roughRunStreak
            previousRoughStreak == Int.MAX_VALUE -> Int.MAX_VALUE
            else -> 0
        }
        return prefs.edit()
            .putLong(KEY_LAST_ACTIVE_AT_MS, state.lastActiveAtMs.coerceAtLeast(0L))
            .putLong(
                KEY_LAST_GARDEN_GREETING_DAY,
                state.lastGardenGreetingDay.coerceAtLeast(-1L)
            )
            .putInt(KEY_ROUGH_RUN_STREAK, safeRoughStreak)
            .commit()
    }

    fun loadLastRunSummary(): RunSummary? {
        if (!prefs.contains(KEY_LAST_RUN_SCORE) || !prefs.contains(KEY_LAST_RUN_QUOTE)) {
            return null
        }
        return RunSummary(
            score = prefs.getInt(KEY_LAST_RUN_SCORE, 0),
            distanceM = prefs.getFloat(KEY_LAST_RUN_DISTANCE, 0f),
            isNewHighScore = prefs.getBoolean(KEY_LAST_RUN_NEW_HIGH, false),
            highScore = prefs.getInt(KEY_LAST_RUN_HIGH_SCORE, 0),
            mercyHearts = prefs.getInt(KEY_LAST_RUN_MERCY_HEARTS, 0),
            mercyMisses = prefs.getInt(KEY_LAST_RUN_MERCY_MISSES, 0),
            kindnessChain = prefs.getInt(KEY_LAST_RUN_KINDNESS_CHAIN, 0),
            cleanPasses = prefs.getInt(KEY_LAST_RUN_CLEAN_PASSES, 0),
            sparedCount = prefs.getInt(KEY_LAST_RUN_SPARED, 0),
            hitsTaken = prefs.getInt(KEY_LAST_RUN_HITS, 0),
            seedsCollected = prefs.getInt(KEY_LAST_RUN_SEEDS, 0),
            bloomConversions = prefs.getInt(KEY_LAST_RUN_BLOOM_CONVERSIONS, 0),
            lastKiller = prefs.getString(KEY_LAST_RUN_KILLER, null)?.let { raw ->
                runCatching { EntityType.valueOf(raw) }.getOrNull()
            },
            restQuote = prefs.getString(KEY_LAST_RUN_QUOTE, "") ?: "",
            forestMood = prefs.getString(
                KEY_LAST_RUN_FOREST_MOOD,
                ForestMood.STEADY.name
            )?.let { raw ->
                runCatching { ForestMood.valueOf(raw) }.getOrDefault(ForestMood.STEADY)
            } ?: ForestMood.STEADY,
            pacifistRouteTier = prefs.getString(
                KEY_LAST_RUN_PACIFIST_ROUTE,
                PacifistRouteTier.NONE.name
            )?.let { raw ->
                runCatching { PacifistRouteTier.valueOf(raw) }
                    .getOrDefault(PacifistRouteTier.NONE)
            } ?: PacifistRouteTier.NONE
        )
    }

    fun loadRouteTierCount(tier: PacifistRouteTier): Int =
        (prefs.all[routeTierKey(tier)] as? Int)
            ?.coerceIn(0, MAX_RECOVERABLE_ROUTE_TIER_COUNT)
            ?: 0

    private fun routeTierKey(tier: PacifistRouteTier): String = when (tier) {
        PacifistRouteTier.NONE -> KEY_ROUTE_NONE_RUNS
        PacifistRouteTier.KIND -> KEY_ROUTE_KIND_RUNS
        PacifistRouteTier.MERCIFUL -> KEY_ROUTE_MERCIFUL_RUNS
        PacifistRouteTier.PEACEFUL -> KEY_ROUTE_PEACEFUL_RUNS
    }

    private companion object {
        const val KEY_BEST_DISTANCE = "best_distance"

        const val KEY_FOREST_MOOD = "forest_mood"
        const val KEY_FOREST_MOOD_STREAK = "forest_mood_streak"
        const val KEY_FOREST_TOTAL_RUNS = "forest_total_runs"
        const val KEY_FOREST_GENTLE_RUNS = "forest_gentle_runs"
        const val KEY_FOREST_RECKLESS_RUNS = "forest_reckless_runs"
        const val KEY_FOREST_FEARFUL_RUNS = "forest_fearful_runs"
        const val KEY_FOREST_STEADY_RUNS = "forest_steady_runs"

        const val KEY_LAST_ACTIVE_AT_MS = "last_active_at_ms"
        const val KEY_LAST_GARDEN_GREETING_DAY = "last_garden_greeting_day"
        const val KEY_ROUGH_RUN_STREAK = "rough_run_streak"

        const val KEY_LAST_RUN_SCORE = "last_run_score"
        const val KEY_LAST_RUN_DISTANCE = "last_run_distance"
        const val KEY_LAST_RUN_NEW_HIGH = "last_run_new_high"
        const val KEY_LAST_RUN_HIGH_SCORE = "last_run_high_score"
        const val KEY_LAST_RUN_MERCY_HEARTS = "last_run_mercy_hearts"
        const val KEY_LAST_RUN_MERCY_MISSES = "last_run_mercy_misses"
        const val KEY_LAST_RUN_KINDNESS_CHAIN = "last_run_kindness_chain"
        const val KEY_LAST_RUN_CLEAN_PASSES = "last_run_clean_passes"
        const val KEY_LAST_RUN_SPARED = "last_run_spared"
        const val KEY_LAST_RUN_HITS = "last_run_hits"
        const val KEY_LAST_RUN_SEEDS = "last_run_seeds"
        const val KEY_LAST_RUN_BLOOM_CONVERSIONS = "last_run_bloom_conversions"
        const val KEY_LAST_RUN_QUOTE = "last_run_quote"
        const val KEY_LAST_RUN_KILLER = "last_run_killer"
        const val KEY_LAST_RUN_FOREST_MOOD = "last_run_forest_mood"
        const val KEY_LAST_RUN_PACIFIST_ROUTE = "last_run_pacifist_route"

        const val KEY_ROUTE_NONE_RUNS = "route_none_runs"
        const val KEY_ROUTE_KIND_RUNS = "route_kind_runs"
        const val KEY_ROUTE_MERCIFUL_RUNS = "route_merciful_runs"
        const val KEY_ROUTE_PEACEFUL_RUNS = "route_peaceful_runs"
    }
}
