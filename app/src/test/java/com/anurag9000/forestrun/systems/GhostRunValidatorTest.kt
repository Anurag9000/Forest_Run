package com.anurag9000.forestrun.systems

import com.anurag9000.forestrun.entities.PlayerState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GhostRunValidatorTest {
    @Test
    fun `valid monotonic finite run is accepted`() {
        assertTrue(
            GhostRunValidator.isValid(
                listOf(
                    frame(t = 0f),
                    frame(t = GhostRecorder.SAMPLE_INTERVAL_S),
                    frame(t = 2f * GhostRecorder.SAMPLE_INTERVAL_S)
                )
            )
        )
    }

    @Test
    fun `empty oversized nonmonotonic and nonfinite runs are rejected`() {
        assertFalse(GhostRunValidator.isValid(emptyList()))
        assertFalse(
            GhostRunValidator.isValid(
                List(GhostRecorder.MAX_FRAMES + 1) { index ->
                    frame(t = index * GhostRecorder.SAMPLE_INTERVAL_S)
                }
            )
        )
        assertFalse(GhostRunValidator.isValid(listOf(frame(1f), frame(0.5f))))
        assertFalse(GhostRunValidator.isValid(listOf(frame(Float.NaN))))
        assertFalse(GhostRunValidator.isValid(listOf(frame(0f, x = Float.POSITIVE_INFINITY))))
    }

    @Test
    fun `invalid state scales and excessive duration are rejected`() {
        assertFalse(GhostRunValidator.isValid(listOf(frame(0f, stateOrdinal = Int.MAX_VALUE))))
        assertFalse(GhostRunValidator.isValid(listOf(frame(0f, scaleX = 0f))))
        assertFalse(GhostRunValidator.isValid(listOf(frame(0f, scaleY = 5f))))
        assertFalse(
            GhostRunValidator.isValid(
                listOf(frame(GhostRecorder.MAX_DURATION_S.toFloat() + 1f))
            )
        )
    }

    private fun frame(
        t: Float,
        x: Float = 100f,
        y: Float = 200f,
        stateOrdinal: Int = PlayerState.RUNNING.ordinal,
        scaleX: Float = 1f,
        scaleY: Float = 1f
    ) = GhostFrame(t, x, y, stateOrdinal, scaleX, scaleY)
}
