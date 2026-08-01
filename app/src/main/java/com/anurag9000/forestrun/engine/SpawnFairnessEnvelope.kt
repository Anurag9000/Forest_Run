package com.anurag9000.forestrun.engine

/** One deterministic observation of the production random-spawn pacing chain. */
internal data class SpawnFairnessObservation(
    val distanceMetres: Float,
    val runTimeSeconds: Float,
    val scrollSpeedPxPerSec: Float,
    val readabilityGapPx: Float,
    val requiredGapPx: Float,
    val leadTimeSeconds: Float,
    val minimumSupportedLeadTimeSeconds: Float
) {
    val isFiniteAndFair: Boolean
        get() = distanceMetres.isFinite() &&
            distanceMetres >= 0f &&
            runTimeSeconds.isFinite() &&
            runTimeSeconds >= 0f &&
            scrollSpeedPxPerSec.isFinite() &&
            scrollSpeedPxPerSec in GameConstants.BASE_SCROLL_SPEED..GameConstants.MAX_SCROLL_SPEED &&
            readabilityGapPx.isFinite() &&
            readabilityGapPx in GameConstants.SPAWN_GAP_MIN_PX..GameConstants.SPAWN_GAP_MAX_PX &&
            requiredGapPx.isFinite() &&
            requiredGapPx >= readabilityGapPx &&
            leadTimeSeconds.isFinite() &&
            leadTimeSeconds + 0.0001f >= minimumSupportedLeadTimeSeconds
}

/**
 * Pure inspection boundary for the exact production speed and spawn-gap rules.
 * It does not introduce a second pacing curve or mutate gameplay state.
 */
internal object SpawnFairnessEnvelope {
    val minimumSupportedLeadTimeSeconds: Float
        get() = GameConstants.SPAWN_GAP_MIN_PX / GameConstants.MAX_SCROLL_SPEED

    fun speedAtDistance(distanceMetres: Float): Float {
        val safeDistance = distanceMetres.takeIf { it.isFinite() && it >= 0f } ?: 0f
        return (
            GameConstants.BASE_SCROLL_SPEED.toDouble() +
                safeDistance.toDouble() * GameConstants.SPEED_PER_METRE.toDouble()
            )
            .coerceIn(
                GameConstants.BASE_SCROLL_SPEED.toDouble(),
                GameConstants.MAX_SCROLL_SPEED.toDouble()
            )
            .toFloat()
    }

    fun observe(
        distanceMetres: Float,
        runTimeSeconds: Float
    ): SpawnFairnessObservation {
        val safeDistance = distanceMetres.takeIf { it.isFinite() && it >= 0f } ?: 0f
        val safeRunTime = runTimeSeconds.takeIf { it.isFinite() && it >= 0f } ?: 0f
        val speed = speedAtDistance(safeDistance)
        val readabilityGap = DifficultyScaler.getSpawnGapPx(safeDistance)
        val requiredGap = SpawnPacing.requiredGapPx(
            distanceMetres = safeDistance,
            runTimeSeconds = safeRunTime,
            scrollSpeedPxPerSec = speed
        )
        val leadTime = (requiredGap / speed)
            .takeIf { it.isFinite() && it >= 0f }
            ?: 0f
        return SpawnFairnessObservation(
            distanceMetres = safeDistance,
            runTimeSeconds = safeRunTime,
            scrollSpeedPxPerSec = speed,
            readabilityGapPx = readabilityGap,
            requiredGapPx = requiredGap,
            leadTimeSeconds = leadTime,
            minimumSupportedLeadTimeSeconds = minimumSupportedLeadTimeSeconds
        )
    }
}
