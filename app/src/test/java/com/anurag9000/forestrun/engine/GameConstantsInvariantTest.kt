package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameConstantsInvariantTest {
    @Test
    fun `scroll and score tuning remains finite positive and ordered`() {
        assertTrue(GameConstants.BASE_SCROLL_SPEED.isFinite())
        assertTrue(GameConstants.MAX_SCROLL_SPEED.isFinite())
        assertTrue(GameConstants.SPEED_PER_METRE.isFinite())
        assertTrue(GameConstants.POINTS_PER_METRE.isFinite())
        assertTrue(GameConstants.BASE_SCROLL_SPEED > 0f)
        assertTrue(GameConstants.MAX_SCROLL_SPEED >= GameConstants.BASE_SCROLL_SPEED)
        assertTrue(GameConstants.SPEED_PER_METRE >= 0f)
        assertTrue(GameConstants.POINTS_PER_METRE > 0f)
    }

    @Test
    fun `Bloom and mercy tuning remains usable`() {
        assertTrue(GameConstants.BLOOM_SEED_COUNT > 0)
        assertTrue(GameConstants.BLOOM_DURATION_S.isFinite())
        assertTrue(GameConstants.BLOOM_DURATION_S > 0f)
        assertTrue(GameConstants.MERCY_WINDOW_FRAC.isFinite())
        assertTrue(GameConstants.MERCY_WINDOW_FRAC in 0f..0.5f)
    }

    @Test
    fun `spawn gaps remain positive and ordered`() {
        assertTrue(GameConstants.SPAWN_GAP_MIN_PX.isFinite())
        assertTrue(GameConstants.SPAWN_GAP_MAX_PX.isFinite())
        assertTrue(GameConstants.SPAWN_GAP_RAMP_METRES.isFinite())
        assertTrue(GameConstants.SPAWN_GAP_MIN_PX > 0f)
        assertTrue(GameConstants.SPAWN_GAP_MAX_PX >= GameConstants.SPAWN_GAP_MIN_PX)
        assertTrue(GameConstants.SPAWN_GAP_RAMP_METRES > 0f)
    }

    @Test
    fun `biome length aliases cannot diverge`() {
        assertTrue(GameConstants.BIOME_LENGTH_M > 0f)
        assertEquals(
            GameConstants.BIOME_LENGTH_M,
            GameConstants.BIOME_LENGTH_METRES,
            0f
        )
    }

    @Test
    fun `base wind remains finite and nonnegative`() {
        assertTrue(GameConstants.BASE_WIND_SPEED.isFinite())
        assertTrue(GameConstants.BASE_WIND_SPEED >= 0f)
    }
}
