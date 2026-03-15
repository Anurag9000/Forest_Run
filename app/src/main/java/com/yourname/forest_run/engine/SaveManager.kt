package com.yourname.forest_run.engine

import android.content.Context
import com.yourname.forest_run.entities.CostumeStyle
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.systems.GhostFrame
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Persists game data that spans across multiple runs:
 *  - High score (Int) in SharedPreferences.
 *  - Lifetime seeds (Int) in SharedPreferences.
 *  - Ghost run frames (binary file in filesDir).
 *
 * All disk I/O is synchronous but cheap — called only on run end / run start.
 * High score is also written immediately on HIT (in RunResetManager.triggerDeath).
 */
object SaveManager {

    private const val PREFS_NAME     = "forest_run_prefs"
    private const val KEY_HIGH_SCORE = "high_score"
    private const val KEY_LIFETIME_SEEDS = "lifetime_seeds"
    private const val KEY_BEST_DIST  = "best_distance"
    private const val KEY_LAST_KILLER = "last_killer"
    private const val KEY_LAST_RUN_SCORE = "last_run_score"
    private const val KEY_LAST_RUN_DISTANCE = "last_run_distance"
    private const val KEY_LAST_RUN_NEW_HIGH = "last_run_new_high"
    private const val KEY_LAST_RUN_HIGH_SCORE = "last_run_high_score"
    private const val KEY_LAST_RUN_MERCY_HEARTS = "last_run_mercy_hearts"
    private const val KEY_LAST_RUN_MERCY_MISSES = "last_run_mercy_misses"
    private const val KEY_LAST_RUN_KINDNESS_CHAIN = "last_run_kindness_chain"
    private const val KEY_LAST_RUN_CLEAN_PASSES = "last_run_clean_passes"
    private const val KEY_LAST_RUN_SPARED = "last_run_spared"
    private const val KEY_LAST_RUN_HITS = "last_run_hits"
    private const val KEY_LAST_RUN_SEEDS = "last_run_seeds"
    private const val KEY_LAST_RUN_BLOOM_CONVERSIONS = "last_run_bloom_conversions"
    private const val KEY_LAST_RUN_QUOTE = "last_run_quote"
    private const val KEY_LAST_RUN_KILLER = "last_run_killer"
    private const val KEY_LAST_RUN_FOREST_MOOD = "last_run_forest_mood"
    private const val KEY_UNLOCKED_COSTUMES = "unlocked_costumes"
    private const val KEY_ACTIVE_COSTUME = "active_costume"
    private const val KEY_FOREST_MOOD = "forest_mood"
    private const val KEY_FOREST_MOOD_STREAK = "forest_mood_streak"
    private const val KEY_FOREST_TOTAL_RUNS = "forest_total_runs"
    private const val KEY_FOREST_GENTLE_RUNS = "forest_gentle_runs"
    private const val KEY_FOREST_RECKLESS_RUNS = "forest_reckless_runs"
    private const val KEY_FOREST_FEARFUL_RUNS = "forest_fearful_runs"
    private const val KEY_FOREST_STEADY_RUNS = "forest_steady_runs"
    private const val KEY_LAST_ACTIVE_AT_MS = "last_active_at_ms"
    private const val KEY_LAST_GARDEN_GREETING_DAY = "last_garden_greeting_day"
    private const val KEY_ROUGH_RUN_STREAK = "rough_run_streak"
    private const val GHOST_FILENAME = "ghost_run.bin"

    // ── High score ────────────────────────────────────────────────────────

    fun saveHighScore(context: Context, score: Int) {
        prefs(context).edit().putInt(KEY_HIGH_SCORE, score).apply()
    }

    fun loadHighScore(context: Context): Int =
        prefs(context).getInt(KEY_HIGH_SCORE, 0)

    // ── Best distance ─────────────────────────────────────────────────────

    fun saveBestDistance(context: Context, distanceM: Float) {
        prefs(context).edit().putFloat(KEY_BEST_DIST, distanceM).apply()
    }

    fun loadBestDistance(context: Context): Float =
        prefs(context).getFloat(KEY_BEST_DIST, 0f)

    // ── Lifetime seeds ────────────────────────────────────────────────────

    fun saveLifetimeSeeds(context: Context, seeds: Int) {
        prefs(context).edit().putInt(KEY_LIFETIME_SEEDS, seeds).apply()
    }

    fun loadLifetimeSeeds(context: Context): Int =
        prefs(context).getInt(KEY_LIFETIME_SEEDS, 0)

    // ── Ghost run ─────────────────────────────────────────────────────────

    /**
     * Serialize [frames] to a compact binary file.
     *
     * Format per frame (28 bytes):
     *   Float t (4), Float x (4), Float y (4), Int stateOrdinal (4),
     *   Float scaleX (4), Float scaleY (4), [padding 4] → 24 bytes
     * Actually: 6 × 4 = 24 bytes per frame.
     */
    fun saveGhostRun(context: Context, frames: List<GhostFrame>) {
        if (frames.isEmpty()) return
        val file = ghostFile(context)
        try {
            DataOutputStream(file.outputStream().buffered()).use { dos ->
                dos.writeInt(frames.size)
                for (f in frames) {
                    dos.writeFloat(f.t)
                    dos.writeFloat(f.x)
                    dos.writeFloat(f.y)
                    dos.writeInt(f.stateOrdinal)
                    dos.writeFloat(f.scaleX)
                    dos.writeFloat(f.scaleY)
                }
            }
        } catch (_: Exception) { /* Silently skip on I/O error — ghost is optional */ }
    }

    /**
     * Load the persisted ghost run. Returns empty list if no file exists or on error.
     */
    fun loadGhostRun(context: Context): List<GhostFrame> {
        val file = ghostFile(context)
        if (!file.exists()) return emptyList()
        return try {
            DataInputStream(file.inputStream().buffered()).use { dis ->
                val count = dis.readInt()
                val list  = ArrayList<GhostFrame>(count)
                repeat(count) {
                    list.add(GhostFrame(
                        t            = dis.readFloat(),
                        x            = dis.readFloat(),
                        y            = dis.readFloat(),
                        stateOrdinal = dis.readInt(),
                        scaleX       = dis.readFloat(),
                        scaleY       = dis.readFloat()
                    ))
                }
                list
            }
        } catch (_: Exception) { emptyList() }
    }

    fun hasGhostRun(context: Context): Boolean = ghostFile(context).exists()

    // ── Garden progress (Phase 23) ─────────────────────────────────────────

    private const val KEY_GARDEN = "garden_unlocked"

    fun saveGardenProgress(context: Context, unlockedCount: Int) {
        prefs(context).edit().putInt(KEY_GARDEN, unlockedCount).apply()
    }

    fun loadGardenProgress(context: Context): Int =
        prefs(context).getInt(KEY_GARDEN, 1)

    // ── Costumes ──────────────────────────────────────────────────────────

    fun saveUnlockedCostumes(context: Context, costumes: Set<CostumeStyle>) {
        val raw = costumes.map { it.name }.toSet()
        prefs(context).edit().putStringSet(KEY_UNLOCKED_COSTUMES, raw).apply()
    }

    fun loadUnlockedCostumes(context: Context): Set<CostumeStyle> {
        val raw = prefs(context).getStringSet(KEY_UNLOCKED_COSTUMES, emptySet()).orEmpty()
        return raw.mapNotNull { name -> runCatching { CostumeStyle.valueOf(name) }.getOrNull() }.toSet()
    }

    fun saveActiveCostume(context: Context, costume: CostumeStyle) {
        prefs(context).edit().putString(KEY_ACTIVE_COSTUME, costume.name).apply()
    }

    fun loadActiveCostume(context: Context): CostumeStyle =
        prefs(context).getString(KEY_ACTIVE_COSTUME, CostumeStyle.NONE.name)?.let { raw ->
            runCatching { CostumeStyle.valueOf(raw) }.getOrDefault(CostumeStyle.NONE)
        } ?: CostumeStyle.NONE

    // ── Persistent memory (Phase 28+) ─────────────────────────────────────

    fun incrementEncounterCount(context: Context, type: EntityType) {
        incrementInt(context, "encounter_${type.name.lowercase()}")
    }

    fun loadEncounterCount(context: Context, type: EntityType): Int =
        prefs(context).getInt("encounter_${type.name.lowercase()}", 0)

    fun incrementSparedCount(context: Context, type: EntityType) {
        incrementInt(context, "spared_${type.name.lowercase()}")
    }

    fun loadSparedCount(context: Context, type: EntityType): Int =
        prefs(context).getInt("spared_${type.name.lowercase()}", 0)

    fun incrementHitCount(context: Context, type: EntityType) {
        incrementInt(context, "hit_${type.name.lowercase()}")
    }

    fun loadHitCount(context: Context, type: EntityType): Int =
        prefs(context).getInt("hit_${type.name.lowercase()}", 0)

    fun saveLastKiller(context: Context, type: EntityType?) {
        prefs(context).edit().putString(KEY_LAST_KILLER, type?.name).apply()
    }

    fun loadLastKiller(context: Context): EntityType? =
        prefs(context).getString(KEY_LAST_KILLER, null)?.let { raw ->
            runCatching { EntityType.valueOf(raw) }.getOrNull()
        }

    fun saveLastRunSummary(context: Context, summary: RunSummary) {
        prefs(context).edit()
            .putInt(KEY_LAST_RUN_SCORE, summary.score)
            .putFloat(KEY_LAST_RUN_DISTANCE, summary.distanceM)
            .putBoolean(KEY_LAST_RUN_NEW_HIGH, summary.isNewHighScore)
            .putInt(KEY_LAST_RUN_HIGH_SCORE, summary.highScore)
            .putInt(KEY_LAST_RUN_MERCY_HEARTS, summary.mercyHearts)
            .putInt(KEY_LAST_RUN_MERCY_MISSES, summary.mercyMisses)
            .putInt(KEY_LAST_RUN_KINDNESS_CHAIN, summary.kindnessChain)
            .putInt(KEY_LAST_RUN_CLEAN_PASSES, summary.cleanPasses)
            .putInt(KEY_LAST_RUN_SPARED, summary.sparedCount)
            .putInt(KEY_LAST_RUN_HITS, summary.hitsTaken)
            .putInt(KEY_LAST_RUN_SEEDS, summary.seedsCollected)
            .putInt(KEY_LAST_RUN_BLOOM_CONVERSIONS, summary.bloomConversions)
            .putString(KEY_LAST_RUN_QUOTE, summary.restQuote)
            .putString(KEY_LAST_RUN_KILLER, summary.lastKiller?.name)
            .putString(KEY_LAST_RUN_FOREST_MOOD, summary.forestMood.name)
            .apply()
    }

    fun loadLastRunSummary(context: Context): RunSummary? {
        val prefs = prefs(context)
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
            forestMood = prefs.getString(KEY_LAST_RUN_FOREST_MOOD, ForestMood.STEADY.name)?.let { raw ->
                runCatching { ForestMood.valueOf(raw) }.getOrDefault(ForestMood.STEADY)
            } ?: ForestMood.STEADY
        )
    }

    fun incrementBiomeFriendship(context: Context, biome: Biome) {
        incrementInt(context, "friendship_${biome.name.lowercase()}")
    }

    fun loadBiomeFriendship(context: Context, biome: Biome): Int =
        prefs(context).getInt("friendship_${biome.name.lowercase()}", 0)

    fun saveForestMoodState(context: Context, state: ForestMoodState) {
        prefs(context).edit()
            .putString(KEY_FOREST_MOOD, state.currentMood.name)
            .putInt(KEY_FOREST_MOOD_STREAK, state.moodStreak)
            .putInt(KEY_FOREST_TOTAL_RUNS, state.totalRuns)
            .putInt(KEY_FOREST_GENTLE_RUNS, state.gentleRuns)
            .putInt(KEY_FOREST_RECKLESS_RUNS, state.recklessRuns)
            .putInt(KEY_FOREST_FEARFUL_RUNS, state.fearfulRuns)
            .putInt(KEY_FOREST_STEADY_RUNS, state.steadyRuns)
            .apply()
    }

    fun loadForestMoodState(context: Context): ForestMoodState {
        val prefs = prefs(context)
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

    fun saveReturnMomentState(context: Context, state: ReturnMomentState) {
        prefs(context).edit()
            .putLong(KEY_LAST_ACTIVE_AT_MS, state.lastActiveAtMs)
            .putLong(KEY_LAST_GARDEN_GREETING_DAY, state.lastGardenGreetingDay)
            .putInt(KEY_ROUGH_RUN_STREAK, state.roughRunStreak)
            .apply()
    }

    fun loadReturnMomentState(context: Context): ReturnMomentState {
        val prefs = prefs(context)
        return ReturnMomentState(
            lastActiveAtMs = prefs.getLong(KEY_LAST_ACTIVE_AT_MS, 0L),
            lastGardenGreetingDay = prefs.getLong(KEY_LAST_GARDEN_GREETING_DAY, -1L),
            roughRunStreak = prefs.getInt(KEY_ROUGH_RUN_STREAK, 0)
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun ghostFile(context: Context) = File(context.filesDir, GHOST_FILENAME)

    private fun incrementInt(context: Context, key: String) {
        val prefs = prefs(context)
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }
}
