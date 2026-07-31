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

    @Test
    fun `non finite inputs resolve to finite calm presentation`() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { invalid ->
            val result = BloomPowerPresentation.resolve(
                secondsRemaining = invalid,
                conversionsInBurst = -10,
                recentSurgeFraction = invalid
            )

            assertEquals(0, result.tier)
            assertEquals(0f, result.playerScaleBoost, 0f)
            assertEquals(80, result.auraAlpha)
            assertEquals(0.24f, result.surgeStrength, 0.0001f)
            assertTrue(result.playerScaleBoost.isFinite())
            assertTrue(result.surgeStrength.isFinite())
        }
    }

    @Test
    fun `extreme finite inputs remain inside authored bounds`() {
        val result = BloomPowerPresentation.resolve(
            secondsRemaining = Float.MAX_VALUE,
            conversionsInBurst = Int.MAX_VALUE,
            recentSurgeFraction = Float.MAX_VALUE
        )

        assertEquals(3, result.tier)
        assertTrue(result.playerScaleBoost in 0f..0.11f)
        assertTrue(result.auraAlpha in 0..220)
        assertTrue(result.surgeStrength in 0f..1f)
    }
}
