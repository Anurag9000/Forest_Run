package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunModeTest {
    @Test
    fun `normal mode alone permits permanent side effects`() {
        assertTrue(RunMode.NORMAL.persistsProgress)
        assertTrue(RunMode.NORMAL.recordsGhost)
        assertTrue(RunMode.NORMAL.allowsRandomSpawns)
        assertTrue(RunMode.NORMAL.allowsOrdinaryProgressCues)
        assertTrue(RunMode.NORMAL.allowsDefaultGhostPlayback)
        assertFalse(RunMode.NORMAL.isDeterministic)
    }

    @Test
    fun `every deterministic mode suppresses permanent side effects`() {
        RunMode.entries.filter { it.isDeterministic }.forEach { mode ->
            assertFalse("$mode must not persist progression", mode.persistsProgress)
            assertFalse("$mode must not record ghosts", mode.recordsGhost)
            assertFalse("$mode must not add random spawns", mode.allowsRandomSpawns)
            assertFalse("$mode must not emit ordinary progress cues", mode.allowsOrdinaryProgressCues)
            assertFalse("$mode must not show an unrelated saved ghost", mode.allowsDefaultGhostPlayback)
        }
    }

    @Test
    fun `scenario parsing defaults unknown absent and normal requests to debug isolation`() {
        assertEquals(RunMode.DEBUG_SCENARIO, RunMode.forScenario(null))
        assertEquals(RunMode.DEBUG_SCENARIO, RunMode.forScenario(""))
        assertEquals(RunMode.DEBUG_SCENARIO, RunMode.forScenario("NOT_A_MODE"))
        assertEquals(RunMode.DEBUG_SCENARIO, RunMode.forScenario(RunMode.NORMAL.name))
    }

    @Test
    fun `scenario parsing preserves explicit capture and profile policies`() {
        assertEquals(
            RunMode.SCREENSHOT_CAPTURE,
            RunMode.forScenario(RunMode.SCREENSHOT_CAPTURE.name)
        )
        assertEquals(
            RunMode.PERFORMANCE_PROFILE,
            RunMode.forScenario(RunMode.PERFORMANCE_PROFILE.name)
        )
    }
}
