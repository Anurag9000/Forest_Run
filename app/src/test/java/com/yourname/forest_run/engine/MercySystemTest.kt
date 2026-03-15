package com.yourname.forest_run.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class MercySystemTest {

    @Test
    fun `mercy hearts cap and track near misses`() {
        val system = MercySystem()

        repeat(14) { system.recordMercyMiss() }

        assertEquals(MercySystem.MAX_HEARTS, system.mercyHearts)
        assertEquals(14, system.nearMisses)
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
}
