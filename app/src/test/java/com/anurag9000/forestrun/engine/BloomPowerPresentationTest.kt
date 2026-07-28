package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BloomPowerPresentationTest {

    @Test
    fun `conversion streak increases bloom power tier and player boost`() {
        val low = BloomPowerPresentation.resolve(
            secondsRemaining = GameConstants.BLOOM_DURATION_S * 0.8f,
            conversionsInBurst = 1,
            recentSurgeFraction = 0.2f
        )
        val high = BloomPowerPresentation.resolve(
            secondsRemaining = GameConstants.BLOOM_DURATION_S * 0.8f,
            conversionsInBurst = 6,
            recentSurgeFraction = 0.2f
        )

        assertEquals(1, low.tier)
        assertEquals(3, high.tier)
        assertTrue(high.playerScaleBoost > low.playerScaleBoost)
        assertTrue(high.auraAlpha > low.auraAlpha)
        assertTrue(high.surgeStrength > low.surgeStrength)
    }

    @Test
    fun `recent surge strengthens the current bloom presentation`() {
        val calm = BloomPowerPresentation.resolve(
            secondsRemaining = GameConstants.BLOOM_DURATION_S * 0.5f,
            conversionsInBurst = 3,
            recentSurgeFraction = 0f
        )
        val surging = BloomPowerPresentation.resolve(
            secondsRemaining = GameConstants.BLOOM_DURATION_S * 0.5f,
            conversionsInBurst = 3,
            recentSurgeFraction = 1f
        )

        assertEquals(calm.tier, surging.tier)
        assertTrue(surging.playerScaleBoost > calm.playerScaleBoost)
        assertTrue(surging.auraAlpha > calm.auraAlpha)
        assertTrue(surging.surgeStrength > calm.surgeStrength)
    }
}
