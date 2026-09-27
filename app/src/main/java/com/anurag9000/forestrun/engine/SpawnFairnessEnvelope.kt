package com.anurag9000.forestrun.engine

/**
 * One observation of the production random-spawn origin-gap curve.
 *
 * Validity of its numeric bounds does NOT prove pairwise encounter feasibility:
 * jump/landing/duck recovery, entity width, trajectory, and telegraph vary by
 * encounter and must be checked independently before creative fairness signoff.
 */
internal data class SpawnFairnessObservation(
    val distanceMetres: Float,
    val runTimeSeconds: Float,
    val scrollSpeedPxPerSec: Float,
    val readabilityGapPx: Float,
    val requiredGapPx: Float,
    val leadTimeSeconds: Float,
    val minimumDeclaredOriginLeadTimeSeconds: Float
) {
    val isFiniteAndWithinDeclaredBounds: Boolean
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
            leadTimeSeconds + 0.0001f >= minimumDeclaredOriginLeadTimeSeconds
}

/**
 * Pure inspection of the production speed and origin-gap arithmetic.
 * The geometric lead-time lower bound is not an action recovery guarantee.
 * No second pacing curve is introduced and gameplay state is not mutated.
 */
internal object SpawnFairnessEnvelope {
    val minimumDeclaredOriginLeadTimeSeconds: Float
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
            minimumDeclaredOriginLeadTimeSeconds = minimumDeclaredOriginLeadTimeSeconds
        )
    }
}
