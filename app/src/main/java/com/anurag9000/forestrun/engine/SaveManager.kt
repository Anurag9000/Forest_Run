package com.anurag9000.forestrun.engine

import android.content.Context
import android.util.AtomicFile
import com.anurag9000.forestrun.entities.CostumeStyle
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.PlayerState
import com.anurag9000.forestrun.systems.GhostFrame
import com.anurag9000.forestrun.systems.GhostRecorder
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Persists game data that spans across multiple runs:
 *  - High score (Int) in SharedPreferences.
 *  - Lifetime seeds (Int) in SharedPreferences.
 *  - Ghost run frames (binary file in filesDir).
 *
 * SharedPreferences writes are lightweight. Ghost serialization is handled by
 * GhostPersistenceManager on a dedicated worker and committed through AtomicFile.
 */
object SaveManager {

    internal const val PREFS_NAME     = "forest_run_prefs"
    private const val COMPAT_PREFS_PREFIX = "forest_run_prefs_compat_v"

    @Volatile
    private var activePrefsName: String = PREFS_NAME

    @Volatile
    private var activeGhostFilename: String = "ghost_run.bin"

    internal val activePrefsNameForTests: String
        get() = activePrefsName

    internal val activeGhostFilenameForTests: String
        get() = activeGhostFilename

    internal fun usePrimaryPreferences() {
        activePrefsName = PREFS_NAME
        activeGhostFilename = GHOST_FILENAME
    }

    internal fun useCompatibilityPreferences(schemaVersion: Int) {
        val safeVersion = schemaVersion.coerceAtLeast(0)
        activePrefsName = "$COMPAT_PREFS_PREFIX$safeVersion"
        activeGhostFilename = "ghost_run_compat_v$safeVersion.bin"
    }
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
    private const val KEY_LAST_RUN_PACIFIST_ROUTE = "last_run_pacifist_route"
    private const val KEY_ROUTE_KIND_RUNS = "route_kind_runs"
    private const val KEY_ROUTE_MERCIFUL_RUNS = "route_merciful_runs"
    private const val KEY_ROUTE_PEACEFUL_RUNS = "route_peaceful_runs"
    private const val KEY_UNLOCKED_COSTUMES = "unlocked_costumes"
    private const val KEY_ACTIVE_COSTUME = "active_costume"
    private const val KEY_FEATURED_COSTUME = "featured_costume"
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
    private const val KEY_UNLOCKED_MEMORY_PAGES = "unlocked_memory_pages"
    private const val KEY_UNLOCKED_RELATIONSHIP_MILESTONES = "unlocked_relationship_milestones"
    private const val KEY_UNLOCKED_HISTORY_MARKS = "unlocked_history_marks"
    private const val GHOST_FILENAME = "ghost_run.bin"

    // ── High score ────────────────────────────────────────────────────────

    fun saveHighScore(context: Context, score: Int) {
        prefs(context).edit().putInt(KEY_HIGH_SCORE, score.coerceAtLeast(0)).apply()
    }

    fun loadHighScore(context: Context): Int =
        prefs(context).getInt(KEY_HIGH_SCORE, 0)

    // ── Best distance ─────────────────────────────────────────────────────

    fun saveBestDistance(context: Context, distanceM: Float) {
        val safeDistance = distanceM.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        prefs(context).edit().putFloat(KEY_BEST_DIST, safeDistance).apply()
    }

    fun loadBestDistance(context: Context): Float =
        prefs(context).getFloat(KEY_BEST_DIST, 0f)

    // ── Lifetime seeds ────────────────────────────────────────────────────

    fun saveLifetimeSeeds(context: Context, seeds: Int) {
        prefs(context).edit().putInt(KEY_LIFETIME_SEEDS, seeds.coerceAtLeast(0)).apply()
    }

    fun loadLifetimeSeeds(context: Context): Int =
        prefs(context).getInt(KEY_LIFETIME_SEEDS, 0)

    // ── Ghost run ─────────────────────────────────────────────────────────

    private const val GHOST_HEADER_BYTES = 4L
    private const val GHOST_FRAME_BYTES = 24L
    private val MAX_GHOST_FILE_BYTES =
        GHOST_HEADER_BYTES + GhostRecorder.MAX_FRAMES.toLong() * GHOST_FRAME_BYTES

    /** Serialize [frames] through [AtomicFile] so interrupted writes preserve the old ghost. */
    fun saveGhostRun(context: Context, frames: List<GhostFrame>): Boolean {
        if (frames.isEmpty() || frames.size > GhostRecorder.MAX_FRAMES) return false

        var previousTime = Float.NEGATIVE_INFINITY
        for (frame in frames) {
            if (!isValidGhostFrame(frame, previousTime)) return false
            previousTime = frame.t
        }

        val atomicFile = AtomicFile(ghostFile(context.applicationContext))
        var stream: FileOutputStream? = null
        return try {
            stream = atomicFile.startWrite()
            val output = DataOutputStream(BufferedOutputStream(stream))
            output.writeInt(frames.size)
            for (frame in frames) {
                output.writeFloat(frame.t)
                output.writeFloat(frame.x)
                output.writeFloat(frame.y)
                output.writeInt(frame.stateOrdinal)
                output.writeFloat(frame.scaleX)
                output.writeFloat(frame.scaleY)
            }
            output.flush()
            atomicFile.finishWrite(stream)
            stream = null
            true
        } catch (_: Exception) {
            stream?.let { atomicFile.failWrite(it) }
            false
        }
    }

    /**
     * Load a structurally valid ghost run. Corrupt, truncated, oversized, or
     * non-finite payloads are rejected before they can allocate unbounded state.
     */
    fun loadGhostRun(context: Context): List<GhostFrame> {
        val atomicFile = AtomicFile(ghostFile(context.applicationContext))
        if (!atomicFile.baseFile.exists() && !File(atomicFile.baseFile.path + ".bak").exists()) {
            return emptyList()
        }

        return try {
            val input = atomicFile.openRead()
            val fileSize = input.channel.size()
            if (fileSize !in GHOST_HEADER_BYTES..MAX_GHOST_FILE_BYTES) {
                input.close()
                return emptyList()
            }

            DataInputStream(input.buffered()).use { data ->
                val count = data.readInt()
                if (count !in 1..GhostRecorder.MAX_FRAMES) return emptyList()

                val expectedBytes = GHOST_HEADER_BYTES + count.toLong() * GHOST_FRAME_BYTES
                if (fileSize != expectedBytes) return emptyList()

                val frames = ArrayList<GhostFrame>(count)
                var previousTime = Float.NEGATIVE_INFINITY
                repeat(count) {
                    val frame = GhostFrame(
                        t = data.readFloat(),
                        x = data.readFloat(),
                        y = data.readFloat(),
                        stateOrdinal = data.readInt(),
                        scaleX = data.readFloat(),
                        scaleY = data.readFloat()
                    )
                    if (!isValidGhostFrame(frame, previousTime)) return emptyList()
                    previousTime = frame.t
                    frames.add(frame)
                }
                frames
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun hasGhostRun(context: Context): Boolean = ghostFile(context).exists()

    private fun isValidGhostFrame(frame: GhostFrame, previousTime: Float): Boolean =
        frame.t.isFinite() &&
            frame.t >= 0f &&
            frame.t >= previousTime &&
            frame.t <= GhostRecorder.MAX_DURATION_S.toFloat() + GhostRecorder.SAMPLE_INTERVAL_S &&
            frame.x.isFinite() &&
            frame.y.isFinite() &&
            frame.stateOrdinal in PlayerState.entries.indices &&
            frame.scaleX.isFinite() &&
            frame.scaleY.isFinite() &&
            frame.scaleX in 0.1f..4f &&
            frame.scaleY in 0.1f..4f

    // ── Garden progress (Phase 23) ─────────────────────────────────────────

    private const val KEY_GARDEN = "garden_unlocked"

    fun saveGardenProgress(context: Context, unlockedCount: Int) {
        prefs(context).edit().putInt(KEY_GARDEN, unlockedCount.coerceIn(1, 9)).apply()
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

    fun saveFeaturedCostume(context: Context, costume: CostumeStyle?) {
        prefs(context).edit().putString(KEY_FEATURED_COSTUME, costume?.name).apply()
    }

    fun loadFeaturedCostume(context: Context): CostumeStyle? =
        prefs(context).getString(KEY_FEATURED_COSTUME, null)?.let { raw ->
            runCatching { CostumeStyle.valueOf(raw) }.getOrNull()
        }

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

    fun incrementCleanPassCount(context: Context, type: EntityType) {
        incrementInt(context, "clean_pass_${type.name.lowercase()}")
    }

    fun loadCleanPassCount(context: Context, type: EntityType): Int =
        prefs(context).getInt("clean_pass_${type.name.lowercase()}", 0)

    fun incrementKindnessStreak(context: Context, type: EntityType) {
        incrementInt(context, "kindness_streak_${type.name.lowercase()}")
    }

    fun resetKindnessStreak(context: Context, type: EntityType) {
        prefs(context).edit().putInt("kindness_streak_${type.name.lowercase()}", 0).apply()
    }

    fun loadKindnessStreak(context: Context, type: EntityType): Int =
        prefs(context).getInt("kindness_streak_${type.name.lowercase()}", 0)

    fun incrementTenderStreak(context: Context, type: EntityType) {
        incrementInt(context, "tender_streak_${type.name.lowercase()}")
    }

    fun resetTenderStreak(context: Context, type: EntityType) {
        prefs(context).edit().putInt("tender_streak_${type.name.lowercase()}", 0).apply()
    }

    fun loadTenderStreak(context: Context, type: EntityType): Int =
        prefs(context).getInt("tender_streak_${type.name.lowercase()}", 0)

    fun saveLastKiller(context: Context, type: EntityType?) {
        prefs(context).edit().putString(KEY_LAST_KILLER, type?.name).apply()
    }

    fun loadLastKiller(context: Context): EntityType? =
        prefs(context).getString(KEY_LAST_KILLER, null)?.let { raw ->
            runCatching { EntityType.valueOf(raw) }.getOrNull()
        }

    fun saveLastRunSummary(context: Context, summary: RunSummary) {
        val safeDistance = summary.distanceM.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        prefs(context).edit()
            .putInt(KEY_LAST_RUN_SCORE, summary.score.coerceAtLeast(0))
            .putFloat(KEY_LAST_RUN_DISTANCE, safeDistance)
            .putBoolean(KEY_LAST_RUN_NEW_HIGH, summary.isNewHighScore)
            .putInt(KEY_LAST_RUN_HIGH_SCORE, summary.highScore.coerceAtLeast(0))
            .putInt(KEY_LAST_RUN_MERCY_HEARTS, summary.mercyHearts.coerceAtLeast(0))
            .putInt(KEY_LAST_RUN_MERCY_MISSES, summary.mercyMisses.coerceAtLeast(0))
            .putInt(KEY_LAST_RUN_KINDNESS_CHAIN, summary.kindnessChain.coerceAtLeast(0))
            .putInt(KEY_LAST_RUN_CLEAN_PASSES, summary.cleanPasses.coerceAtLeast(0))
            .putInt(KEY_LAST_RUN_SPARED, summary.sparedCount.coerceAtLeast(0))
            .putInt(KEY_LAST_RUN_HITS, summary.hitsTaken.coerceAtLeast(0))
            .putInt(KEY_LAST_RUN_SEEDS, summary.seedsCollected.coerceAtLeast(0))
            .putInt(KEY_LAST_RUN_BLOOM_CONVERSIONS, summary.bloomConversions.coerceAtLeast(0))
            .putString(KEY_LAST_RUN_QUOTE, summary.restQuote)
            .putString(KEY_LAST_RUN_KILLER, summary.lastKiller?.name)
            .putString(KEY_LAST_RUN_FOREST_MOOD, summary.forestMood.name)
            .putString(KEY_LAST_RUN_PACIFIST_ROUTE, summary.pacifistRouteTier.name)
            .apply()
        incrementRouteTierCount(context, summary.pacifistRouteTier)
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
            } ?: ForestMood.STEADY,
            pacifistRouteTier = prefs.getString(KEY_LAST_RUN_PACIFIST_ROUTE, PacifistRouteTier.NONE.name)?.let { raw ->
                runCatching { PacifistRouteTier.valueOf(raw) }.getOrDefault(PacifistRouteTier.NONE)
            } ?: PacifistRouteTier.NONE
        )
    }

    fun loadRouteTierCount(context: Context, tier: PacifistRouteTier): Int =
        prefs(context).getInt(routeTierKey(tier), 0)

    private fun incrementRouteTierCount(context: Context, tier: PacifistRouteTier) {
        if (tier == PacifistRouteTier.NONE) return
        incrementInt(context, routeTierKey(tier))
    }

    private fun routeTierKey(tier: PacifistRouteTier): String = when (tier) {
        PacifistRouteTier.NONE -> "route_none_runs"
        PacifistRouteTier.KIND -> KEY_ROUTE_KIND_RUNS
        PacifistRouteTier.MERCIFUL -> KEY_ROUTE_MERCIFUL_RUNS
        PacifistRouteTier.PEACEFUL -> KEY_ROUTE_PEACEFUL_RUNS
    }

    fun incrementBiomeFriendship(context: Context, biome: Biome) {
        incrementInt(context, "friendship_${biome.name.lowercase()}")
    }

    fun loadBiomeFriendship(context: Context, biome: Biome): Int =
        prefs(context).getInt("friendship_${biome.name.lowercase()}", 0)

    fun saveForestMoodState(context: Context, state: ForestMoodState) {
        prefs(context).edit()
            .putString(KEY_FOREST_MOOD, state.currentMood.name)
            .putInt(KEY_FOREST_MOOD_STREAK, state.moodStreak.coerceAtLeast(0))
            .putInt(KEY_FOREST_TOTAL_RUNS, state.totalRuns.coerceAtLeast(0))
            .putInt(KEY_FOREST_GENTLE_RUNS, state.gentleRuns.coerceAtLeast(0))
            .putInt(KEY_FOREST_RECKLESS_RUNS, state.recklessRuns.coerceAtLeast(0))
            .putInt(KEY_FOREST_FEARFUL_RUNS, state.fearfulRuns.coerceAtLeast(0))
            .putInt(KEY_FOREST_STEADY_RUNS, state.steadyRuns.coerceAtLeast(0))
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
            .putLong(KEY_LAST_ACTIVE_AT_MS, state.lastActiveAtMs.coerceAtLeast(0L))
            .putLong(KEY_LAST_GARDEN_GREETING_DAY, state.lastGardenGreetingDay.coerceAtLeast(-1L))
            .putInt(KEY_ROUGH_RUN_STREAK, state.roughRunStreak.coerceAtLeast(0))
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

    fun saveUnlockedMemoryPages(context: Context, pages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_UNLOCKED_MEMORY_PAGES, pages).apply()
    }

    fun loadUnlockedMemoryPages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_UNLOCKED_MEMORY_PAGES, emptySet()).orEmpty()

    fun saveRelationshipStage(context: Context, type: EntityType, stage: RelationshipStage) {
        prefs(context).edit().putString("relationship_stage_${type.name.lowercase()}", stage.name).apply()
    }

    fun loadRelationshipStage(context: Context, type: EntityType): RelationshipStage? =
        prefs(context).getString("relationship_stage_${type.name.lowercase()}", null)?.let { raw ->
            runCatching { RelationshipStage.valueOf(raw) }.getOrNull()
        }

    fun saveUnlockedRelationshipMilestones(context: Context, milestones: Set<EntityType>) {
        val raw = milestones.map { it.name }.toSet()
        prefs(context).edit().putStringSet(KEY_UNLOCKED_RELATIONSHIP_MILESTONES, raw).apply()
    }

    fun loadUnlockedRelationshipMilestones(context: Context): Set<EntityType> =
        prefs(context).getStringSet(KEY_UNLOCKED_RELATIONSHIP_MILESTONES, emptySet()).orEmpty()
            .mapNotNull { raw -> runCatching { EntityType.valueOf(raw) }.getOrNull() }
            .toSet()

    fun saveUnlockedHistoryMarks(context: Context, marks: Set<String>) {
        prefs(context).edit().putStringSet(KEY_UNLOCKED_HISTORY_MARKS, marks).apply()
    }

    fun loadUnlockedHistoryMarks(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_UNLOCKED_HISTORY_MARKS, emptySet()).orEmpty()

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun prefs(context: Context) =
        context.getSharedPreferences(activePrefsName, Context.MODE_PRIVATE)

    private fun ghostFile(context: Context) = File(context.filesDir, activeGhostFilename)

    private fun incrementInt(context: Context, key: String) {
        val prefs = prefs(context)
        val current = (prefs.all[key] as? Int)?.coerceAtLeast(0) ?: 0
        val next = if (current == Int.MAX_VALUE) Int.MAX_VALUE else current + 1
        prefs.edit().putInt(key, next).apply()
    }
}
