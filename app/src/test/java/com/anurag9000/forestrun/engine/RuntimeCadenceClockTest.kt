package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeCadenceClockTest {

    @Test
    fun `one elapsed second is invariant to update partitioning`() {
        for (updatesPerSecond in listOf(30, 60, 120)) {
            val clock = RuntimeCadenceClock()
            var polls = 0
            repeat(updatesPerSecond) {
                clock.advance(1f / updatesPerSecond)
                if (clock.consumeAccessibilityPoll()) polls++
            }

            assertEquals(
                "updates=$updatesPerSecond elapsed",
                1f,
                clock.elapsedSeconds,
                0.0001f
            )
            assertEquals("updates=$updatesPerSecond polls", 2, polls)
        }
    }

    @Test
    fun `poll is unavailable before half a second and consumed exactly once`() {
        val clock = RuntimeCadenceClock()
        clock.advance(0.24f)
        assertFalse(clock.consumeAccessibilityPoll())
        clock.advance(0.25f)
        assertFalse(clock.consumeAccessibilityPoll())
        clock.advance(0.01f)
        assertTrue(clock.consumeAccessibilityPoll())
        assertFalse(clock.consumeAccessibilityPoll())
    }

    @Test
    fun `malformed deltas are no ops and reset clears both clocks`() {
        val clock = RuntimeCadenceClock()
        clock.advance(0.4f)
        val before = clock.elapsedSeconds
        for (invalid in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            clock.advance(invalid)
        }
        assertEquals(before, clock.elapsedSeconds, 0f)
        assertFalse(clock.consumeAccessibilityPoll())

        clock.advance(0.1f)
        assertTrue(clock.consumeAccessibilityPoll())
        clock.reset()
        assertEquals(0f, clock.elapsedSeconds, 0f)
        assertFalse(clock.consumeAccessibilityPoll())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `nonpositive accessibility interval is rejected`() {
        RuntimeCadenceClock(0f)
    }
}
