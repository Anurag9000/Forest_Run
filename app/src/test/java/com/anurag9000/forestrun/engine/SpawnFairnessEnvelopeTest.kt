package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpawnFairnessEnvelopeTest {

    @Test
    fun `worst supported pacing retains the authored reaction floor`() {
        assertEquals(
            0.39f,
            SpawnFairnessEnvelope.minimumSupportedLeadTimeSeconds,
            0.0001f
        )

        val observation = SpawnFairnessEnvelope.observe(
            distanceMetres = 100_000f,
            runTimeSeconds = 120f
        )
        assertEquals(GameConstants.MAX_SCROLL_SPEED, observation.scrollSpeedPxPerSec, 0f)
        assertEquals(GameConstants.SPAWN_GAP_MIN_PX, observation.readabilityGapPx, 0.0001f)
        assertEquals(GameConstants.SPAWN_GAP_MIN_PX, observation.requiredGapPx, 0.0001f)
        assertEquals(0.39f, observation.leadTimeSeconds, 0.0001f)
        assertTrue(observation.isFiniteAndFair)
    }

    @Test
    fun `production distance and opening-time grid remains finite and fair`() {
        val runTimes = floatArrayOf(0f, 6.75f, 7f, 12f, 19.9f, 20f, 27.9f, 28f, 120f)
        var distance = 0f
        while (distance <= 20_000f) {
            for (runTime in runTimes) {
                val observation = SpawnFairnessEnvelope.observe(distance, runTime)
                assertTrue(
                    "distance=$distance time=$runTime observation=$observation",
                    observation.isFiniteAndFair
                )
                assertTrue(observation.requiredGapPx + 0.0001f >= observation.readabilityGapPx)
            }
            distance += 5f
        }
    }

    @Test
    fun `opening guide can only add reaction time and expires exactly`() {
        val distances = floatArrayOf(0f, 500f, 2_000f, 10_000f)
        for (distance in distances) {
            val early = SpawnFairnessEnvelope.observe(distance, 7f)
            val middle = SpawnFairnessEnvelope.observe(distance, 13f)
            val lateGuided = SpawnFairnessEnvelope.observe(distance, 21f)
            val expired = SpawnFairnessEnvelope.observe(distance, 28f)

            assertTrue(early.leadTimeSeconds + 0.0001f >= 1.95f)
            assertTrue(middle.leadTimeSeconds + 0.0001f >= 1.78f)
            assertTrue(lateGuided.leadTimeSeconds + 0.0001f >= 1.58f)
            assertEquals(expired.readabilityGapPx, expired.requiredGapPx, 0.0002f)
        }
    }

    @Test
    fun `speed curve is monotonic bounded and saturating`() {
        var previous = GameConstants.BASE_SCROLL_SPEED
        var distance = 0f
        while (distance <= 20_000f) {
            val speed = SpawnFairnessEnvelope.speedAtDistance(distance)
            assertTrue(speed >= previous)
            assertTrue(speed in GameConstants.BASE_SCROLL_SPEED..GameConstants.MAX_SCROLL_SPEED)
            previous = speed
            distance += 1f
        }
        assertEquals(
            GameConstants.MAX_SCROLL_SPEED,
            SpawnFairnessEnvelope.speedAtDistance(Float.MAX_VALUE),
            0f
        )
    }

    @Test
    fun `malformed inputs fail closed to the safest opening observation`() {
        val baseline = SpawnFairnessEnvelope.observe(0f, 0f)
        for (malformed in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f)) {
            assertEquals(baseline, SpawnFairnessEnvelope.observe(malformed, malformed))
            assertEquals(
                GameConstants.BASE_SCROLL_SPEED,
                SpawnFairnessEnvelope.speedAtDistance(malformed),
                0f
            )
        }
    }
}
