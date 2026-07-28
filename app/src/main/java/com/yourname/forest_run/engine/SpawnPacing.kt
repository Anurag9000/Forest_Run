package com.yourname.forest_run.engine

/** Converts the readability gap curve and opening tutorial timing into one rule. */
object SpawnPacing {
    private const val MIN_EFFECTIVE_SPEED_PX_PER_SEC = 1f

    /**
     * Required distance travelled since the previous random spawn.
     *
     * After the guided opening this equals the distance-only difficulty curve,
     * regardless of current scroll speed. During the opening, the existing
     * minimum reaction-time overrides are converted into equivalent distances
     * at the current speed.
     */
    fun requiredGapPx(
        distanceMetres: Float,
        runTimeSeconds: Float,
        scrollSpeedPxPerSec: Float
    ): Float {
        val defaultGap = DifficultyScaler.getSpawnGapPx(distanceMetres)
        val speed = scrollSpeedPxPerSec.coerceAtLeast(MIN_EFFECTIVE_SPEED_PX_PER_SEC)
        val defaultInterval = defaultGap / speed
        val adjustedInterval = OpeningReadabilityGuide.adjustedSpawnInterval(
            runTimeSeconds,
            defaultInterval
        )
        return (adjustedInterval * speed).coerceAtLeast(defaultGap)
    }
}
