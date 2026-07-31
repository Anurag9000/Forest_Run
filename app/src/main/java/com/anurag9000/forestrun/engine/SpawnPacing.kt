package com.anurag9000.forestrun.engine

/** Converts the readability gap curve and opening tutorial timing into one rule. */
object SpawnPacing {
    private const val MIN_EFFECTIVE_SPEED_PX_PER_SEC = 1f

    fun requiredGapPx(
        distanceMetres: Float,
        runTimeSeconds: Float,
        scrollSpeedPxPerSec: Float
    ): Float {
        val defaultGap = DifficultyScaler.getSpawnGapPx(distanceMetres)
            .takeIf { it.isFinite() && it >= 0f }
            ?: GameConstants.SPAWN_GAP_MAX_PX
        val speed = scrollSpeedPxPerSec
            .takeIf { it.isFinite() && it > 0f }
            ?.coerceAtLeast(MIN_EFFECTIVE_SPEED_PX_PER_SEC)
            ?: MIN_EFFECTIVE_SPEED_PX_PER_SEC
        val defaultInterval = (defaultGap / speed)
            .takeIf { it.isFinite() && it >= 0f }
            ?: 0f
        val adjustedInterval = OpeningReadabilityGuide.adjustedSpawnInterval(
            runTimeSeconds = runTimeSeconds,
            defaultInterval = defaultInterval
        )
        val equivalentDistance = (adjustedInterval.toDouble() * speed.toDouble())
            .coerceIn(0.0, Float.MAX_VALUE.toDouble())
            .toFloat()
        return maxOf(defaultGap, equivalentDistance)
    }
}
