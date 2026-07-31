package com.anurag9000.forestrun.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestRequestGateTest {
    @Test
    fun `new request invalidates every older token`() {
        val gate = LatestRequestGate()
        val first = gate.begin()
        val second = gate.begin()
        val third = gate.begin()

        assertFalse(gate.isCurrent(first))
        assertFalse(gate.isCurrent(second))
        assertTrue(gate.isCurrent(third))
    }

    @Test
    fun `cancel invalidates the current request`() {
        val gate = LatestRequestGate()
        val token = gate.begin()

        gate.cancel()

        assertFalse(gate.isCurrent(token))
    }

    @Test
    fun `repeated ownership changes never revive an old token`() {
        val gate = LatestRequestGate()
        val oldest = gate.begin()

        repeat(10_000) { gate.begin() }

        assertFalse(gate.isCurrent(oldest))
    }
}
