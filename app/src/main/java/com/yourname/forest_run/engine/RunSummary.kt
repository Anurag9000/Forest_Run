package com.yourname.forest_run.engine

import com.yourname.forest_run.entities.EntityType

/**
 * Canonical post-run payload shared by the rest screen and Garden carry-home UI.
 */
data class RunSummary(
    val score: Int,
    val distanceM: Float,
    val isNewHighScore: Boolean,
    val highScore: Int,
    val mercyHearts: Int,
    val mercyMisses: Int,
    val kindnessChain: Int,
    val cleanPasses: Int,
    val sparedCount: Int,
    val hitsTaken: Int,
    val seedsCollected: Int,
    val bloomConversions: Int,
    val lastKiller: EntityType?,
    val restQuote: String,
    val forestMood: ForestMood
)
