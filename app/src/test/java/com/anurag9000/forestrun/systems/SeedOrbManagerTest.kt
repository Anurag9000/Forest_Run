package com.anurag9000.forestrun.systems

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedOrbManagerTest {

    @Test
    fun `invalid spawn requests are rejected without adding state`() {
        val manager = SeedOrbManager { 0f }

        assertFalse(manager.trySpawn(Float.NaN, 400f))
        assertFalse(manager.trySpawn(400f, Float.POSITIVE_INFINITY))
        assertFalse(manager.trySpawn(400f, 400f, Float.NaN))
        assertFalse(manager.trySpawn(400f, 400f, 0f))
        assertFalse(manager.trySpawn(400f, 400f, -1f))
        assertEquals(0, manager.activeOrbCount)
    }

    @Test
    fun `manager enforces the exact active orb capacity`() {
        val manager = SeedOrbManager { 0f }

        repeat(SeedOrbManager.MAX_ORBS) {
            assertTrue(manager.trySpawn(500f + it, 700f))
        }

        assertEquals(SeedOrbManager.MAX_ORBS, manager.activeOrbCount)
        assertFalse(manager.trySpawn(900f, 700f))
        assertEquals(SeedOrbManager.MAX_ORBS, manager.activeOrbCount)
    }

    @Test
    fun `failed random chance does not consume capacity`() {
        val manager = SeedOrbManager { 1f }

        assertFalse(manager.trySpawn(500f, 700f, spawnRate = 0.5f))
        assertEquals(0, manager.activeOrbCount)
    }

    @Test
    fun `reset clears every active orb`() {
        val manager = SeedOrbManager { 0f }
        repeat(3) { assertTrue(manager.trySpawn(500f + it, 700f)) }

        manager.reset()

        assertEquals(0, manager.activeOrbCount)
    }
}
