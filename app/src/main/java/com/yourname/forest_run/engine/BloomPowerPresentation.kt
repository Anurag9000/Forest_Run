package com.yourname.forest_run.engine

data class BloomPowerPresentationState(
    val tier: Int,
    val playerScaleBoost: Float,
    val auraAlpha: Int,
    val surgeStrength: Float
)

object BloomPowerPresentation {

    fun resolve(
        secondsRemaining: Float,
        conversionsInBurst: Int,
        recentSurgeFraction: Float
    ): BloomPowerPresentationState {
        val burst = conversionsInBurst.coerceAtLeast(0)
        val timeFraction = (secondsRemaining / GameConstants.BLOOM_DURATION_S).coerceIn(0f, 1f)
        val surge = recentSurgeFraction.coerceIn(0f, 1f)
        val tier = when {
            burst >= 6 -> 3
            burst >= 3 -> 2
            burst >= 1 -> 1
            else -> 0
        }
        val scaleBoost = (
            tier * 0.016f +
                timeFraction * 0.014f +
                surge * (0.016f + tier * 0.004f)
            ).coerceIn(0f, 0.11f)
        val auraAlpha = (
            80f +
                tier * 34f +
                timeFraction * 24f +
                surge * 56f
            ).toInt().coerceIn(0, 220)
        val surgeStrength = (
            0.24f +
                tier * 0.16f +
                surge * 0.34f
            ).coerceIn(0f, 1f)

        return BloomPowerPresentationState(
            tier = tier,
            playerScaleBoost = scaleBoost,
            auraAlpha = auraAlpha,
            surgeStrength = surgeStrength
        )
    }
}
