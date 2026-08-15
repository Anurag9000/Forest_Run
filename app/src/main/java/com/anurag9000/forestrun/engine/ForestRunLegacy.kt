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
        val mood = SaveManager.loadForestMoodState(appContext)
        val last = SaveManager.loadLastRunSummary(appContext)?.let { summary ->
            ForestLastRunMemory(
                score = summary.score.coerceAtLeast(0),
                distanceM = summary.distanceM
                    .takeIf { it.isFinite() }
                    ?.coerceAtLeast(0f)
                    ?.toInt()
                    ?: 0,
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
            bestDistanceM = SaveManager.loadBestDistance(appContext)
                .takeIf { it.isFinite() }
                ?.coerceAtLeast(0f)
                ?.toInt()
                ?: 0,
            totalRuns = mood.totalRuns.coerceAtLeast(0),
            currentMood = mood.currentMood,
            moodStreak = mood.moodStreak.coerceAtLeast(0),
            dominantMood = mood.dominantMood,
            gentleRuns = mood.gentleRuns.coerceAtLeast(0),
            steadyRuns = mood.steadyRuns.coerceAtLeast(0),
            fearfulRuns = mood.fearfulRuns.coerceAtLeast(0),
            recklessRuns = mood.recklessRuns.coerceAtLeast(0),
            lastRun = last
        )
    }

    private fun routeLabel(tier: PacifistRouteTier): String = when (tier) {
        PacifistRouteTier.NONE -> "Unmarked path"
        PacifistRouteTier.KIND -> "Kind Path"
        PacifistRouteTier.MERCIFUL -> "Merciful Path"
        PacifistRouteTier.PEACEFUL -> "Peaceful Path"
    }
}
