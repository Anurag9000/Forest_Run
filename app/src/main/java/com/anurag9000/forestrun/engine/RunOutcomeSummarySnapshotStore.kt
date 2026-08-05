package com.anurag9000.forestrun.engine

import android.content.Context
import android.content.SharedPreferences

internal interface RunOutcomeSummarySnapshotStore {
    fun save(summary: RunSummary, routeTierCount: Int): Boolean
}

/**
 * Writes the completed summary and its route-tier counter in one synchronous
 * SharedPreferences transaction. This avoids replaying the legacy summary
 * writer's hidden counter side effect during crash recovery.
 */
internal class SharedPreferencesRunOutcomeSummarySnapshotStore(
    context: Context,
    persistenceNamespace: String
) : RunOutcomeSummarySnapshotStore {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        persistenceNamespace,
        Context.MODE_PRIVATE
    )

    override fun save(summary: RunSummary, routeTierCount: Int): Boolean {
        val persisted = RunOutcomeRecoveryTransitions.persistedSummary(summary)
        val editor = prefs.edit()
            .putInt(KEY_LAST_RUN_SCORE, persisted.score)
            .putFloat(KEY_LAST_RUN_DISTANCE, persisted.distanceM)
            .putBoolean(KEY_LAST_RUN_NEW_HIGH, persisted.isNewHighScore)
            .putInt(KEY_LAST_RUN_HIGH_SCORE, persisted.highScore)
            .putInt(KEY_LAST_RUN_MERCY_HEARTS, persisted.mercyHearts)
            .putInt(KEY_LAST_RUN_MERCY_MISSES, persisted.mercyMisses)
            .putInt(KEY_LAST_RUN_KINDNESS_CHAIN, persisted.kindnessChain)
            .putInt(KEY_LAST_RUN_CLEAN_PASSES, persisted.cleanPasses)
            .putInt(KEY_LAST_RUN_SPARED, persisted.sparedCount)
            .putInt(KEY_LAST_RUN_HITS, persisted.hitsTaken)
            .putInt(KEY_LAST_RUN_SEEDS, persisted.seedsCollected)
            .putInt(KEY_LAST_RUN_BLOOM_CONVERSIONS, persisted.bloomConversions)
            .putString(KEY_LAST_RUN_QUOTE, persisted.restQuote)
            .putString(KEY_LAST_RUN_KILLER, persisted.lastKiller?.name)
            .putString(KEY_LAST_RUN_FOREST_MOOD, persisted.forestMood.name)
            .putString(KEY_LAST_RUN_PACIFIST_ROUTE, persisted.pacifistRouteTier.name)

        routeTierKey(persisted.pacifistRouteTier)?.let { key ->
            editor.putInt(
                key,
                routeTierCount.coerceIn(0, MAX_RECOVERABLE_ROUTE_TIER_COUNT)
            )
        }
        return editor.commit()
    }

    private fun routeTierKey(tier: PacifistRouteTier): String? = when (tier) {
        PacifistRouteTier.NONE -> null
        PacifistRouteTier.KIND -> KEY_ROUTE_KIND_RUNS
        PacifistRouteTier.MERCIFUL -> KEY_ROUTE_MERCIFUL_RUNS
        PacifistRouteTier.PEACEFUL -> KEY_ROUTE_PEACEFUL_RUNS
    }

    private companion object {
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
        const val KEY_ROUTE_KIND_RUNS = "route_kind_runs"
        const val KEY_ROUTE_MERCIFUL_RUNS = "route_merciful_runs"
        const val KEY_ROUTE_PEACEFUL_RUNS = "route_peaceful_runs"
    }
}
