package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpawnPacingTest {
    @Test
    fun `spawn gap tightens monotonically and stays bounded`() {
        val start = ReadabilityProfile.spawnGapPx(0f)
        val middle = ReadabilityProfile.spawnGapPx(1_000f)
        val full = ReadabilityProfile.spawnGapPx(2_000f)
        val beyond = ReadabilityProfile.spawnGapPx(20_000f)

        assertEquals(GameConstants.SPAWN_GAP_MAX_PX, start, 0.0001f)
        assertTrue(middle < start)
        assertTrue(middle > full)
        assertEquals(GameConstants.SPAWN_GAP_MIN_PX, full, 0.0001f)
        assertEquals(full, beyond, 0.0001f)
    }

    @Test
    fun `post tutorial gap is independent of scroll speed`() {
        val slow = SpawnPacing.requiredGapPx(
            distanceMetres = 1_250f,
            runTimeSeconds = 30f,
            scrollSpeedPxPerSec = 650f
        )
        val fast = SpawnPacing.requiredGapPx(
            distanceMetres = 1_250f,
            runTimeSeconds = 30f,
            scrollSpeedPxPerSec = 2_000f
        )

        assertEquals(slow, fast, 0.0001f)
        assertEquals(ReadabilityProfile.spawnGapPx(1_250f), fast, 0.0001f)
    }

    @Test
    fun `opening reaction time overrides become equivalent distance`() {
        val speed = 700f
        val gap = SpawnPacing.requiredGapPx(
            distanceMetres = 0f,
            runTimeSeconds = 8f,
            scrollSpeedPxPerSec = speed
        )

        assertTrue(gap / speed >= 1.95f)
        assertTrue(gap >= ReadabilityProfile.spawnGapPx(0f))
    }

    @Test
    fun `zero or invalidly low speed still returns finite safe gap`() {
        val gap = SpawnPacing.requiredGapPx(
            distanceMetres = 500f,
            runTimeSeconds = 40f,
            scrollSpeedPxPerSec = 0f
        )

        assertTrue(gap.isFinite())
        assertEquals(ReadabilityProfile.spawnGapPx(500f), gap, 0.0001f)
    }
}
