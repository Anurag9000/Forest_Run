package com.yourname.forest_run.engine

import android.content.Context
import android.graphics.Color

enum class ForestMood(
    val displayName: String,
    val gardenLine: String,
    val restLine: String,
    val skyTopColor: Int,
    val skyBottomColor: Int,
    val groundColor: Int,
    val accentColor: Int
) {
    GENTLE(
        displayName = "Gentle",
        gardenLine = "The grove exhales around you.",
        restLine = "The path felt gentle.",
        skyTopColor = Color.rgb(108, 172, 122),
        skyBottomColor = Color.rgb(212, 240, 184),
        groundColor = Color.rgb(78, 154, 84),
        accentColor = Color.rgb(206, 255, 196)
    ),
    RECKLESS(
        displayName = "Reckless",
        gardenLine = "The wind still remembers your rush.",
        restLine = "The grove is still catching its breath.",
        skyTopColor = Color.rgb(176, 110, 68),
        skyBottomColor = Color.rgb(242, 190, 118),
        groundColor = Color.rgb(146, 96, 52),
        accentColor = Color.rgb(255, 218, 132)
    ),
    FEARFUL(
        displayName = "Fearful",
        gardenLine = "The willow keeps the quiet close.",
        restLine = "The forest noticed your hurry.",
        skyTopColor = Color.rgb(70, 92, 124),
        skyBottomColor = Color.rgb(154, 174, 196),
        groundColor = Color.rgb(66, 96, 88),
        accentColor = Color.rgb(198, 218, 255)
    ),
    STEADY(
        displayName = "Steady",
        gardenLine = "The grove keeps an even pulse.",
        restLine = "The trail held steady.",
        skyTopColor = Color.rgb(60, 140, 230),
        skyBottomColor = Color.rgb(180, 230, 160),
        groundColor = Color.rgb(80, 160, 70),
        accentColor = Color.rgb(232, 246, 212)
    );
}

data class ForestMoodState(
    val currentMood: ForestMood = ForestMood.STEADY,
    val moodStreak: Int = 0,
    val totalRuns: Int = 0,
    val gentleRuns: Int = 0,
    val recklessRuns: Int = 0,
    val fearfulRuns: Int = 0,
    val steadyRuns: Int = 0
) {
    val dominantMood: ForestMood
        get() = listOf(
            ForestMood.GENTLE to gentleRuns,
            ForestMood.RECKLESS to recklessRuns,
            ForestMood.FEARFUL to fearfulRuns,
            ForestMood.STEADY to steadyRuns
        ).maxWithOrNull(compareBy<Pair<ForestMood, Int>> { it.second }.thenBy { it.first.ordinal })
            ?.first ?: currentMood
}

object ForestMoodSystem {

    fun classifyRun(
        score: Int,
        distanceM: Float,
        mercyHearts: Int,
        kindnessChain: Int,
        cleanPasses: Int,
        sparedCount: Int,
        hitsTaken: Int,
        seedsCollected: Int,
        bloomConversions: Int
    ): ForestMood {
        if (sparedCount > 0 || (mercyHearts >= 4 && hitsTaken == 0)) {
            return ForestMood.GENTLE
        }
        if (hitsTaken >= 2 && cleanPasses <= 2 && distanceM < 750f) {
            return ForestMood.FEARFUL
        }
        if (bloomConversions >= 2 || (hitsTaken > 0 && seedsCollected >= GameConstants.BLOOM_SEED_COUNT && score >= 1_200)) {
            return ForestMood.RECKLESS
        }
        if (kindnessChain >= 3 || cleanPasses >= 4 || distanceM >= 900f) {
            return ForestMood.STEADY
        }
        return if (hitsTaken > 0) ForestMood.FEARFUL else ForestMood.STEADY
    }

    fun classifyRun(summary: RunSummary): ForestMood =
        classifyRun(
            score = summary.score,
            distanceM = summary.distanceM,
            mercyHearts = summary.mercyHearts,
            kindnessChain = summary.kindnessChain,
            cleanPasses = summary.cleanPasses,
            sparedCount = summary.sparedCount,
            hitsTaken = summary.hitsTaken,
            seedsCollected = summary.seedsCollected,
            bloomConversions = summary.bloomConversions
        )

    fun recordRun(context: Context, summary: RunSummary): ForestMoodState {
        val mood = summary.forestMood
        val previous = SaveManager.loadForestMoodState(context.applicationContext)
        val updated = when (mood) {
            ForestMood.GENTLE -> previous.copy(
                currentMood = mood,
                moodStreak = if (previous.currentMood == mood) previous.moodStreak + 1 else 1,
                totalRuns = previous.totalRuns + 1,
                gentleRuns = previous.gentleRuns + 1
            )
            ForestMood.RECKLESS -> previous.copy(
                currentMood = mood,
                moodStreak = if (previous.currentMood == mood) previous.moodStreak + 1 else 1,
                totalRuns = previous.totalRuns + 1,
                recklessRuns = previous.recklessRuns + 1
            )
            ForestMood.FEARFUL -> previous.copy(
                currentMood = mood,
                moodStreak = if (previous.currentMood == mood) previous.moodStreak + 1 else 1,
                totalRuns = previous.totalRuns + 1,
                fearfulRuns = previous.fearfulRuns + 1
            )
            ForestMood.STEADY -> previous.copy(
                currentMood = mood,
                moodStreak = if (previous.currentMood == mood) previous.moodStreak + 1 else 1,
                totalRuns = previous.totalRuns + 1,
                steadyRuns = previous.steadyRuns + 1
            )
        }
        SaveManager.saveForestMoodState(context.applicationContext, updated)
        return updated
    }

    fun currentState(context: Context): ForestMoodState =
        SaveManager.loadForestMoodState(context.applicationContext)
}
