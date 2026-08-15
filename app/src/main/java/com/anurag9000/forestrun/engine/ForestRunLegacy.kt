package com.anurag9000.forestrun.engine

import android.content.Context

internal data class ForestLastRunMemory(
    val score: Int,
    val distanceM: Int,
    val routeLabel: String,
    val moodLabel: String,
    val cleanPasses: Int,
    val spared: Int,
    val hits: Int,
    val seeds: Int,
    val bloomConversions: Int,
    val restQuote: String
) {
    init {
        require(score >= 0 && distanceM >= 0) { "Last-run score and distance must be non-negative" }
        require(cleanPasses >= 0 && spared >= 0 && hits >= 0 && seeds >= 0 && bloomConversions >= 0) {
            "Last-run counters must be non-negative"
        }
        require(routeLabel.isNotBlank() && moodLabel.isNotBlank()) {
            "Last-run route and mood labels must be present"
        }
    }
}

internal data class ForestRunLegacySnapshot(
    val highScore: Int,
    val bestDistanceM: Int,
    val totalRuns: Int,
    val currentMood: ForestMood,
    val moodStreak: Int,
    val dominantMood: ForestMood,
    val gentleRuns: Int,
    val steadyRuns: Int,
    val fearfulRuns: Int,
    val recklessRuns: Int,
    val lastRun: ForestLastRunMemory?
) {
    init {
        require(highScore >= 0 && bestDistanceM >= 0 && totalRuns >= 0 && moodStreak >= 0) {
            "Run legacy headline values must be non-negative"
        }
        require(gentleRuns >= 0 && steadyRuns >= 0 && fearfulRuns >= 0 && recklessRuns >= 0) {
            "Run legacy mood counters must be non-negative"
        }
    }
}

/** Read-only long-horizon run projection for the Journal. */
internal object ForestRunLegacyComposer {
    fun snapshot(context: Context): ForestRunLegacySnapshot {
        val appContext = context.applicationContext
        val mood = sanitizedMoodState(SaveManager.loadForestMoodState(appContext))
        val last = SaveManager.loadLastRunSummary(appContext)?.let { summary ->
            ForestLastRunMemory(
                score = summary.score.coerceAtLeast(0),
                distanceM = safeDistanceMetres(summary.distanceM),
                routeLabel = routeLabel(summary.pacifistRouteTier),
                moodLabel = summary.forestMood.displayName,
                cleanPasses = summary.cleanPasses.coerceAtLeast(0),
                spared = summary.sparedCount.coerceAtLeast(0),
                hits = summary.hitsTaken.coerceAtLeast(0),
                seeds = summary.seedsCollected.coerceAtLeast(0),
                bloomConversions = summary.bloomConversions.coerceAtLeast(0),
                restQuote = summary.restQuote.trim()
            )
        }
        return ForestRunLegacySnapshot(
            highScore = SaveManager.loadHighScore(appContext).coerceAtLeast(0),
            bestDistanceM = safeDistanceMetres(SaveManager.loadBestDistance(appContext)),
            totalRuns = mood.totalRuns,
            currentMood = mood.currentMood,
            moodStreak = mood.moodStreak,
            dominantMood = mood.dominantMood,
            gentleRuns = mood.gentleRuns,
            steadyRuns = mood.steadyRuns,
            fearfulRuns = mood.fearfulRuns,
            recklessRuns = mood.recklessRuns,
            lastRun = last
        )
    }

    internal fun sanitizedMoodState(state: ForestMoodState): ForestMoodState = state.copy(
        moodStreak = state.moodStreak.coerceAtLeast(0),
        totalRuns = state.totalRuns.coerceAtLeast(0),
        gentleRuns = state.gentleRuns.coerceAtLeast(0),
        recklessRuns = state.recklessRuns.coerceAtLeast(0),
        fearfulRuns = state.fearfulRuns.coerceAtLeast(0),
        steadyRuns = state.steadyRuns.coerceAtLeast(0)
    )

    internal fun safeDistanceMetres(distanceM: Float): Int =
        distanceM.takeIf { it.isFinite() && it > 0f }
            ?.coerceAtMost(Int.MAX_VALUE.toFloat())
            ?.toInt()
            ?: 0

    private fun routeLabel(tier: PacifistRouteTier): String = when (tier) {
        PacifistRouteTier.NONE -> "Unmarked path"
        PacifistRouteTier.KIND -> "Kind Path"
        PacifistRouteTier.MERCIFUL -> "Merciful Path"
        PacifistRouteTier.PEACEFUL -> "Peaceful Path"
    }
}
