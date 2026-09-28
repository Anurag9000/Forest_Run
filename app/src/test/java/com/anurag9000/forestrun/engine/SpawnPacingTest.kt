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
        val slow = SpawnPacing.requiredGapPx(1_250f, 30f, 650f)
        val fast = SpawnPacing.requiredGapPx(1_250f, 30f, 2_000f)

        assertEquals(slow, fast, 0.0001f)
        assertEquals(ReadabilityProfile.spawnGapPx(1_250f), fast, 0.0001f)
    }

    @Test
    fun `opening reaction time overrides become equivalent distance`() {
        val speed = 700f
        val gap = SpawnPacing.requiredGapPx(0f, 8f, speed)

        assertTrue(gap / speed >= 1.95f)
        assertTrue(gap >= ReadabilityProfile.spawnGapPx(0f))
    }


    @Test
    fun `maximum speed random encounter lead covers sampled full jump plus same-frame update`() {
        val speed = GameConstants.MAX_SCROLL_SPEED
        val lead = SpawnPacing.minimumRandomEncounterLeadPx(speed)
        val apex = EncounterActionFeasibility.observe(
            leadDistancePx = Float.MAX_VALUE,
            approachSpeedPxPerSec = speed,
            requiredVerticalClearancePx = 0f,
            jumpUpwardSpeedPxPerSec = -com.anurag9000.forestrun.entities.Player.MAX_JUMP_FORCE,
            gravityPxPerSecSquared = com.anurag9000.forestrun.entities.Player.GRAVITY,
            gestureDecisionSeconds = 0f
        ).maximumBallisticRisePx
        val afterCreationFrame = lead -
            speed * FrameInputAdmission.MAX_DELTA_SECONDS * 1.15f
        val observation = EncounterActionFeasibility.observe(
            leadDistancePx = afterCreationFrame,
            approachSpeedPxPerSec = speed,
            requiredVerticalClearancePx = apex,
            jumpUpwardSpeedPxPerSec = -com.anurag9000.forestrun.entities.Player.MAX_JUMP_FORCE,
            gravityPxPerSecSquared = com.anurag9000.forestrun.entities.Player.GRAVITY,
            gestureDecisionSeconds = 0.075f,
            safetyMarginSeconds = 0.08f
        )

        assertEquals(495f, apex, 0.002f)
        assertTrue(lead > GameConstants.SPAWN_GAP_MIN_PX)
        assertTrue(observation.jumpFeasible)
    }

    @Test
    fun `random encounter action lead is speed aware finite and bounded by supported ceiling`() {
        val slow = SpawnPacing.minimumRandomEncounterLeadPx(GameConstants.BASE_SCROLL_SPEED)
        val fast = SpawnPacing.minimumRandomEncounterLeadPx(GameConstants.MAX_SCROLL_SPEED)
        val excessive = SpawnPacing.minimumRandomEncounterLeadPx(Float.MAX_VALUE)
        val malformed = SpawnPacing.minimumRandomEncounterLeadPx(Float.NaN)

        assertTrue(slow.isFinite() && slow > 0f)
        assertTrue(fast > slow)
        assertEquals(fast, excessive, 0.001f)
        assertEquals(fast, malformed, 0.001f)
    }

    @Test
    fun `zero negative and non finite speed return finite safe gaps`() {
        listOf(
            0f,
            -100f,
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        ).forEach { speed ->
            val gap = SpawnPacing.requiredGapPx(
                distanceMetres = 500f,
                runTimeSeconds = 40f,
                scrollSpeedPxPerSec = speed
            )
            assertTrue(gap.isFinite())
            assertEquals(ReadabilityProfile.spawnGapPx(500f), gap, 0.0001f)
        }
    }

    @Test
    fun `invalid time and distance return opening-safe finite pacing`() {
        val expected = SpawnPacing.requiredGapPx(0f, 0f, 700f)

        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f).forEach { invalid ->
            val gap = SpawnPacing.requiredGapPx(invalid, invalid, 700f)
            assertTrue(gap.isFinite())
            assertEquals(expected, gap, 0.0001f)
        }
    }

    @Test
    fun `extreme finite speed saturates equivalent opening distance`() {
        val gap = SpawnPacing.requiredGapPx(0f, 8f, Float.MAX_VALUE)

        assertTrue(gap.isFinite())
        assertEquals(Float.MAX_VALUE, gap, 0f)
    }
}
