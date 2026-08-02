package com.anurag9000.forestrun.engine

import android.content.Context
import android.content.SharedPreferences
import com.anurag9000.forestrun.entities.EntityType

internal enum class RunOutcomeRecoveryPhase {
    PREPARED,
    MOOD_APPLIED,
    RETURN_APPLIED,
    SUMMARY_APPLIED
}

internal data class RunOutcomeRecoveryRecord(
    val phase: RunOutcomeRecoveryPhase,
    val summary: RunSummary,
    val previousMood: ForestMoodState,
    val nextMood: ForestMoodState,
    val previousReturn: ReturnMomentState,
    val nextReturn: ReturnMomentState,
    val previousRouteTierCount: Int,
    val nextRouteTierCount: Int
)

internal sealed interface RunOutcomeRecoveryLoadResult {
    data object Empty : RunOutcomeRecoveryLoadResult
    data object Corrupt : RunOutcomeRecoveryLoadResult
    data class Pending(val record: RunOutcomeRecoveryRecord) : RunOutcomeRecoveryLoadResult
}

internal interface RunOutcomeRecoveryStore {
    fun load(): RunOutcomeRecoveryLoadResult
    fun save(record: RunOutcomeRecoveryRecord): Boolean
    fun clear(): Boolean
}

/**
 * Synchronous SharedPreferences journal scoped to the active save namespace.
 *
 * The record is replaced atomically with commit() before persistence starts.
 * Individual checkpoints may lag their corresponding state write; recovery is
 * therefore based on before/after state comparison rather than phase alone.
 */
internal class SharedPreferencesRunOutcomeRecoveryStore(
    context: Context,
    persistenceNamespace: String
) : RunOutcomeRecoveryStore {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        "forest_run_outcome_recovery_${safeNamespace(persistenceNamespace)}",
        Context.MODE_PRIVATE
    )

    override fun load(): RunOutcomeRecoveryLoadResult {
        return try {
            if (!prefs.getBoolean(KEY_PRESENT, false)) {
                RunOutcomeRecoveryLoadResult.Empty
            } else {
                val schema = prefs.getInt(KEY_SCHEMA, -1)
                if (schema != SCHEMA_VERSION) return RunOutcomeRecoveryLoadResult.Corrupt

                val phase = enumValueOrNull<RunOutcomeRecoveryPhase>(
                    prefs.getString(KEY_PHASE, null)
                ) ?: return RunOutcomeRecoveryLoadResult.Corrupt
                val summary = readSummary() ?: return RunOutcomeRecoveryLoadResult.Corrupt
                val previousMood = readMood(PREVIOUS_MOOD)
                    ?: return RunOutcomeRecoveryLoadResult.Corrupt
                val nextMood = readMood(NEXT_MOOD)
                    ?: return RunOutcomeRecoveryLoadResult.Corrupt
                val previousReturn = readReturn(PREVIOUS_RETURN)
                    ?: return RunOutcomeRecoveryLoadResult.Corrupt
                val nextReturn = readReturn(NEXT_RETURN)
                    ?: return RunOutcomeRecoveryLoadResult.Corrupt
                val previousRouteTierCount = prefs.getInt(PREVIOUS_ROUTE_TIER_COUNT, -1)
                val nextRouteTierCount = prefs.getInt(NEXT_ROUTE_TIER_COUNT, -1)
                if (previousRouteTierCount < 0 || nextRouteTierCount < 0) {
                    return RunOutcomeRecoveryLoadResult.Corrupt
                }

                RunOutcomeRecoveryLoadResult.Pending(
                    RunOutcomeRecoveryRecord(
                        phase = phase,
                        summary = summary,
                        previousMood = previousMood,
                        nextMood = nextMood,
                        previousReturn = previousReturn,
                        nextReturn = nextReturn,
                        previousRouteTierCount = previousRouteTierCount,
                        nextRouteTierCount = nextRouteTierCount
                    )
                )
            }
        } catch (_: ClassCastException) {
            RunOutcomeRecoveryLoadResult.Corrupt
        }
    }

    override fun save(record: RunOutcomeRecoveryRecord): Boolean {
        if (!isValid(record)) return false

        val editor = prefs.edit().clear()
            .putBoolean(KEY_PRESENT, true)
            .putInt(KEY_SCHEMA, SCHEMA_VERSION)
            .putString(KEY_PHASE, record.phase.name)
            .putInt(PREVIOUS_ROUTE_TIER_COUNT, record.previousRouteTierCount)
            .putInt(NEXT_ROUTE_TIER_COUNT, record.nextRouteTierCount)
        writeSummary(editor, record.summary)
        writeMood(editor, PREVIOUS_MOOD, record.previousMood)
        writeMood(editor, NEXT_MOOD, record.nextMood)
        writeReturn(editor, PREVIOUS_RETURN, record.previousReturn)
        writeReturn(editor, NEXT_RETURN, record.nextReturn)
        return editor.commit()
    }

    override fun clear(): Boolean = prefs.edit().clear().commit()

    private fun readSummary(): RunSummary? {
        val lastKiller = nullableEnumValue<EntityType>(SUMMARY_LAST_KILLER) ?: return null
        val forestMood = enumValueOrNull<ForestMood>(prefs.getString(SUMMARY_FOREST_MOOD, null))
            ?: return null
        val routeTier = enumValueOrNull<PacifistRouteTier>(
            prefs.getString(SUMMARY_ROUTE_TIER, null)
        ) ?: return null
        val quote = prefs.getString(SUMMARY_REST_QUOTE, null) ?: return null

        val summary = RunSummary(
            score = prefs.getInt(SUMMARY_SCORE, -1),
            distanceM = prefs.getFloat(SUMMARY_DISTANCE, Float.NaN),
            isNewHighScore = prefs.getBoolean(SUMMARY_NEW_HIGH, false),
            highScore = prefs.getInt(SUMMARY_HIGH_SCORE, -1),
            mercyHearts = prefs.getInt(SUMMARY_MERCY_HEARTS, -1),
            mercyMisses = prefs.getInt(SUMMARY_MERCY_MISSES, -1),
            kindnessChain = prefs.getInt(SUMMARY_KINDNESS_CHAIN, -1),
            cleanPasses = prefs.getInt(SUMMARY_CLEAN_PASSES, -1),
            sparedCount = prefs.getInt(SUMMARY_SPARED, -1),
            hitsTaken = prefs.getInt(SUMMARY_HITS, -1),
            seedsCollected = prefs.getInt(SUMMARY_SEEDS, -1),
            bloomConversions = prefs.getInt(SUMMARY_BLOOM, -1),
            lastKiller = lastKiller.value,
            restQuote = quote,
            forestMood = forestMood,
            pacifistRouteTier = routeTier
        )
        return summary.takeIf(::isValidSummary)
    }

    private fun writeSummary(editor: SharedPreferences.Editor, summary: RunSummary) {
        editor
            .putInt(SUMMARY_SCORE, summary.score)
            .putFloat(SUMMARY_DISTANCE, summary.distanceM)
            .putBoolean(SUMMARY_NEW_HIGH, summary.isNewHighScore)
            .putInt(SUMMARY_HIGH_SCORE, summary.highScore)
            .putInt(SUMMARY_MERCY_HEARTS, summary.mercyHearts)
            .putInt(SUMMARY_MERCY_MISSES, summary.mercyMisses)
            .putInt(SUMMARY_KINDNESS_CHAIN, summary.kindnessChain)
            .putInt(SUMMARY_CLEAN_PASSES, summary.cleanPasses)
            .putInt(SUMMARY_SPARED, summary.sparedCount)
            .putInt(SUMMARY_HITS, summary.hitsTaken)
            .putInt(SUMMARY_SEEDS, summary.seedsCollected)
            .putInt(SUMMARY_BLOOM, summary.bloomConversions)
            .putString(SUMMARY_LAST_KILLER, summary.lastKiller?.name ?: NULL_ENUM)
            .putString(SUMMARY_REST_QUOTE, summary.restQuote)
            .putString(SUMMARY_FOREST_MOOD, summary.forestMood.name)
            .putString(SUMMARY_ROUTE_TIER, summary.pacifistRouteTier.name)
    }

    private fun readMood(prefix: String): ForestMoodState? {
        val mood = enumValueOrNull<ForestMood>(prefs.getString("${prefix}current", null))
            ?: return null
        val state = ForestMoodState(
            currentMood = mood,
            moodStreak = prefs.getInt("${prefix}streak", -1),
            totalRuns = prefs.getInt("${prefix}total", -1),
            gentleRuns = prefs.getInt("${prefix}gentle", -1),
            recklessRuns = prefs.getInt("${prefix}reckless", -1),
            fearfulRuns = prefs.getInt("${prefix}fearful", -1),
            steadyRuns = prefs.getInt("${prefix}steady", -1)
        )
        return state.takeIf(::isValidMood)
    }

    private fun writeMood(
        editor: SharedPreferences.Editor,
        prefix: String,
        state: ForestMoodState
    ) {
        editor
            .putString("${prefix}current", state.currentMood.name)
            .putInt("${prefix}streak", state.moodStreak)
            .putInt("${prefix}total", state.totalRuns)
            .putInt("${prefix}gentle", state.gentleRuns)
            .putInt("${prefix}reckless", state.recklessRuns)
            .putInt("${prefix}fearful", state.fearfulRuns)
            .putInt("${prefix}steady", state.steadyRuns)
    }

    private fun readReturn(prefix: String): ReturnMomentState? {
        val state = ReturnMomentState(
            lastActiveAtMs = prefs.getLong("${prefix}active", Long.MIN_VALUE),
            lastGardenGreetingDay = prefs.getLong("${prefix}greeting", Long.MIN_VALUE),
            roughRunStreak = prefs.getInt("${prefix}rough", -1)
        )
        return state.takeIf(::isValidReturn)
    }

    private fun writeReturn(
        editor: SharedPreferences.Editor,
        prefix: String,
        state: ReturnMomentState
    ) {
        editor
            .putLong("${prefix}active", state.lastActiveAtMs)
            .putLong("${prefix}greeting", state.lastGardenGreetingDay)
            .putInt("${prefix}rough", state.roughRunStreak)
    }

    private fun isValid(record: RunOutcomeRecoveryRecord): Boolean =
        isValidSummary(record.summary) &&
            isValidMood(record.previousMood) &&
            isValidMood(record.nextMood) &&
            isValidReturn(record.previousReturn) &&
            isValidReturn(record.nextReturn) &&
            record.previousRouteTierCount >= 0 &&
            record.nextRouteTierCount >= 0

    private fun isValidSummary(summary: RunSummary): Boolean =
        summary.score >= 0 &&
            summary.highScore >= 0 &&
            summary.mercyHearts >= 0 &&
            summary.mercyMisses >= 0 &&
            summary.kindnessChain >= 0 &&
            summary.cleanPasses >= 0 &&
            summary.sparedCount >= 0 &&
            summary.hitsTaken >= 0 &&
            summary.seedsCollected >= 0 &&
            summary.bloomConversions >= 0 &&
            summary.restQuote.length <= MAX_QUOTE_LENGTH

    private fun isValidMood(state: ForestMoodState): Boolean =
        state.moodStreak >= 0 &&
            state.totalRuns >= 0 &&
            state.gentleRuns >= 0 &&
            state.recklessRuns >= 0 &&
            state.fearfulRuns >= 0 &&
            state.steadyRuns >= 0

    private fun isValidReturn(state: ReturnMomentState): Boolean =
        state.lastActiveAtMs != Long.MIN_VALUE &&
            state.lastGardenGreetingDay != Long.MIN_VALUE &&
            state.roughRunStreak >= 0

    private data class NullableEnum<T>(val value: T?)

    private inline fun <reified T : Enum<T>> nullableEnumValue(key: String): NullableEnum<T>? {
        val raw = prefs.getString(key, null) ?: return null
        if (raw == NULL_ENUM) return NullableEnum(null)
        return enumValueOrNull<T>(raw)?.let(::NullableEnum)
    }

    private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String?): T? =
        raw?.let { value -> runCatching { enumValueOf<T>(value) }.getOrNull() }

    private companion object {
        const val SCHEMA_VERSION = 2
        const val MAX_QUOTE_LENGTH = 8_192
        const val NULL_ENUM = "__none__"

        const val KEY_PRESENT = "present"
        const val KEY_SCHEMA = "schema"
        const val KEY_PHASE = "phase"
        const val PREVIOUS_ROUTE_TIER_COUNT = "previous_route_tier_count"
        const val NEXT_ROUTE_TIER_COUNT = "next_route_tier_count"

        const val SUMMARY_SCORE = "summary_score"
        const val SUMMARY_DISTANCE = "summary_distance"
        const val SUMMARY_NEW_HIGH = "summary_new_high"
        const val SUMMARY_HIGH_SCORE = "summary_high_score"
        const val SUMMARY_MERCY_HEARTS = "summary_mercy_hearts"
        const val SUMMARY_MERCY_MISSES = "summary_mercy_misses"
        const val SUMMARY_KINDNESS_CHAIN = "summary_kindness_chain"
        const val SUMMARY_CLEAN_PASSES = "summary_clean_passes"
        const val SUMMARY_SPARED = "summary_spared"
        const val SUMMARY_HITS = "summary_hits"
        const val SUMMARY_SEEDS = "summary_seeds"
        const val SUMMARY_BLOOM = "summary_bloom"
        const val SUMMARY_LAST_KILLER = "summary_last_killer"
        const val SUMMARY_REST_QUOTE = "summary_rest_quote"
        const val SUMMARY_FOREST_MOOD = "summary_forest_mood"
        const val SUMMARY_ROUTE_TIER = "summary_route_tier"

        const val PREVIOUS_MOOD = "previous_mood_"
        const val NEXT_MOOD = "next_mood_"
        const val PREVIOUS_RETURN = "previous_return_"
        const val NEXT_RETURN = "next_return_"

        fun safeNamespace(namespace: String): String {
            val safe = namespace.map { char ->
                if (char.isLetterOrDigit() || char == '_' || char == '-') char else '_'
            }.joinToString("").take(96)
            return safe.ifBlank { "default" }
        }
    }
}

/** Pure state transitions stored in the journal before any write begins. */
internal object RunOutcomeRecoveryTransitions {
    fun nextForestMood(previous: ForestMoodState, summary: RunSummary): ForestMoodState {
        val mood = summary.forestMood
        val nextStreak = if (previous.currentMood == mood) {
            saturatingIncrement(previous.moodStreak)
        } else {
            1
        }
        val nextTotalRuns = saturatingIncrement(previous.totalRuns)
        return when (mood) {
            ForestMood.GENTLE -> previous.copy(
                currentMood = mood,
                moodStreak = nextStreak,
                totalRuns = nextTotalRuns,
                gentleRuns = saturatingIncrement(previous.gentleRuns)
            )
            ForestMood.RECKLESS -> previous.copy(
                currentMood = mood,
                moodStreak = nextStreak,
                totalRuns = nextTotalRuns,
                recklessRuns = saturatingIncrement(previous.recklessRuns)
            )
            ForestMood.FEARFUL -> previous.copy(
                currentMood = mood,
                moodStreak = nextStreak,
                totalRuns = nextTotalRuns,
                fearfulRuns = saturatingIncrement(previous.fearfulRuns)
            )
            ForestMood.STEADY -> previous.copy(
                currentMood = mood,
                moodStreak = nextStreak,
                totalRuns = nextTotalRuns,
                steadyRuns = saturatingIncrement(previous.steadyRuns)
            )
        }
    }

    fun nextReturnMoment(
        previous: ReturnMomentState,
        summary: RunSummary,
        nowMs: Long
    ): ReturnMomentState {
        val roughRun = summary.forestMood == ForestMood.FEARFUL ||
            (summary.hitsTaken >= 2 && summary.distanceM < 650f) ||
            (summary.hitsTaken > 0 && summary.kindnessChain == 0 && summary.seedsCollected < 4)
        return previous.copy(
            lastActiveAtMs = nowMs,
            roughRunStreak = if (roughRun) {
                SafeProgressionArithmetic.saturatingIncrement(previous.roughRunStreak)
            } else {
                0
            }
        )
    }

    fun nextRouteTierCount(previous: Int, tier: PacifistRouteTier): Int =
        if (tier == PacifistRouteTier.NONE) {
            previous.coerceAtLeast(0)
        } else {
            saturatingIncrement(previous)
        }

    fun persistedSummary(summary: RunSummary): RunSummary = summary.copy(
        score = summary.score.coerceAtLeast(0),
        distanceM = summary.distanceM.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f,
        highScore = summary.highScore.coerceAtLeast(0),
        mercyHearts = summary.mercyHearts.coerceAtLeast(0),
        mercyMisses = summary.mercyMisses.coerceAtLeast(0),
        kindnessChain = summary.kindnessChain.coerceAtLeast(0),
        cleanPasses = summary.cleanPasses.coerceAtLeast(0),
        sparedCount = summary.sparedCount.coerceAtLeast(0),
        hitsTaken = summary.hitsTaken.coerceAtLeast(0),
        seedsCollected = summary.seedsCollected.coerceAtLeast(0),
        bloomConversions = summary.bloomConversions.coerceAtLeast(0)
    )

    private fun saturatingIncrement(value: Int): Int =
        if (value >= Int.MAX_VALUE) Int.MAX_VALUE else value.coerceAtLeast(0) + 1
}
