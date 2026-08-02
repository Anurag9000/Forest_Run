package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameInputAdmissionTest {

    @Test
    fun `ordinary and boundary frame inputs are accepted`() {
        val accepted = listOf(
            Float.MIN_VALUE to 0f,
            0.016f to GameConstants.BASE_SCROLL_SPEED,
            FrameInputAdmission.MAX_DELTA_SECONDS to GameConstants.MAX_SCROLL_SPEED,
            Float.MAX_VALUE to Float.MAX_VALUE
        )

        accepted.forEach { (delta, speed) ->
            assertTrue(FrameInputAdmission.accepts(delta, speed))
        }
    }

    @Test
    fun `nonfinite zero and reversing inputs are rejected`() {
        val malformed = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)

        malformed.forEach { delta ->
            assertFalse(FrameInputAdmission.accepts(delta, GameConstants.BASE_SCROLL_SPEED))
        }
        assertFalse(FrameInputAdmission.accepts(0f, GameConstants.BASE_SCROLL_SPEED))
        assertFalse(FrameInputAdmission.accepts(-0.001f, GameConstants.BASE_SCROLL_SPEED))

        malformed.forEach { speed ->
            assertFalse(FrameInputAdmission.accepts(0.016f, speed))
        }
        assertFalse(FrameInputAdmission.accepts(0.016f, -0.001f))
    }

    @Test
    fun `positive finite delta is bounded without changing ordinary steps`() {
        assertEquals(0.016f, FrameInputAdmission.boundedDeltaSeconds(0.016f), 0f)
        assertEquals(
            FrameInputAdmission.MAX_DELTA_SECONDS,
            FrameInputAdmission.boundedDeltaSeconds(FrameInputAdmission.MAX_DELTA_SECONDS),
            0f
        )
        assertEquals(
            FrameInputAdmission.MAX_DELTA_SECONDS,
            FrameInputAdmission.boundedDeltaSeconds(Float.MAX_VALUE),
            0f
        )
    }

    @Test
    fun `finite nonnegative speed is bounded to gameplay ceiling`() {
        assertEquals(0f, FrameInputAdmission.boundedScrollSpeed(0f), 0f)
        assertEquals(
            GameConstants.BASE_SCROLL_SPEED,
            FrameInputAdmission.boundedScrollSpeed(GameConstants.BASE_SCROLL_SPEED),
            0f
        )
        assertEquals(
            GameConstants.MAX_SCROLL_SPEED,
            FrameInputAdmission.boundedScrollSpeed(GameConstants.MAX_SCROLL_SPEED),
            0f
        )
        assertEquals(
            GameConstants.MAX_SCROLL_SPEED,
            FrameInputAdmission.boundedScrollSpeed(Float.MAX_VALUE),
            0f
        )
    }

    @Test
    fun `bounding functions reject misuse instead of propagating malformed values`() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { value ->
            assertRejected { FrameInputAdmission.boundedDeltaSeconds(value) }
        }
        listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { value ->
            assertRejected { FrameInputAdmission.boundedScrollSpeed(value) }
        }
    }

    private fun assertRejected(block: () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Expected malformed frame input to be rejected.", rejected)
    }
}
