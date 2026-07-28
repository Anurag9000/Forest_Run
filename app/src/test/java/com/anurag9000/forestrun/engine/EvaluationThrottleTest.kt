package com.anurag9000.forestrun.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationThrottleTest {

    @Test
    fun `first evaluation runs and interval gates subsequent calls`() {
        val throttle = EvaluationThrottle(intervalNs = 100L)

        assertTrue(throttle.tryAcquire(1_000L))
        assertFalse(throttle.tryAcquire(1_000L))
        assertFalse(throttle.tryAcquire(1_099L))
        assertTrue(throttle.tryAcquire(1_100L))
    }

    @Test
    fun `forced evaluation advances the gate`() {
        val throttle = EvaluationThrottle(intervalNs = 100L)

        assertTrue(throttle.tryAcquire(1_000L))
        assertTrue(throttle.tryAcquire(1_010L, force = true))
        assertFalse(throttle.tryAcquire(1_109L))
        assertTrue(throttle.tryAcquire(1_110L))
    }

    @Test
    fun `clock reset and explicit reset reopen the gate`() {
        val throttle = EvaluationThrottle(intervalNs = 100L)

        assertTrue(throttle.tryAcquire(1_000L))
        assertTrue(throttle.tryAcquire(900L))
        assertFalse(throttle.tryAcquire(950L))

        throttle.reset()
        assertTrue(throttle.tryAcquire(950L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative interval is rejected`() {
        EvaluationThrottle(intervalNs = -1L)
    }
}
