package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class MercySystemTest {

    @Test
    fun `mercy hearts cap and track near misses`() {
        val system = MercySystem()

        repeat(14) { system.recordMercyMiss() }

        assertEquals(MercySystem.MAX_HEARTS, system.mercyHearts)
        assertEquals(14, system.nearMisses)
        assertEquals(14, system.kindnessChain)
    }

    @Test
    fun `kindness chain resets on hit`() {
        val system = MercySystem()

        system.recordCleanPass()
        system.recordMercyMiss()
        system.recordSpare()
        assertEquals(4, system.kindnessChain)

        system.recordHit()
        assertEquals(0, system.kindnessChain)
    }

    @Test
    fun `near miss and kindness counters saturate instead of wrapping`() {
        val system = MercySystem()
        setIntField(system, "nearMisses", Int.MAX_VALUE)
        setIntField(system, "kindnessChain", Int.MAX_VALUE)
        setIntField(system, "mercyHearts", MercySystem.MAX_HEARTS)

        system.recordMercyMiss()
        system.recordCleanPass()
        system.recordSpare()

        assertEquals(Int.MAX_VALUE, system.nearMisses)
        assertEquals(Int.MAX_VALUE, system.kindnessChain)
        assertEquals(MercySystem.MAX_HEARTS, system.mercyHearts)
    }

    @Test
    fun `spare saturates from the final representable kindness value`() {
        val system = MercySystem()
        setIntField(system, "kindnessChain", Int.MAX_VALUE - 1)

        system.recordSpare()

        assertEquals(Int.MAX_VALUE, system.kindnessChain)
    }

    @Test
    fun `reset clears every run-local Mercy value`() {
        val system = MercySystem()
        repeat(3) { system.recordMercyMiss() }
        system.recordSpare()

        system.reset()

        assertEquals(0, system.mercyHearts)
        assertEquals(0, system.nearMisses)
        assertEquals(0, system.kindnessChain)
    }

    private fun setIntField(system: MercySystem, name: String, value: Int) {
        val field = MercySystem::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.setInt(system, value)
    }
}
