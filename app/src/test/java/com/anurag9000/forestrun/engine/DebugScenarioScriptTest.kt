package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DebugScenarioScriptTest {
    @Test
    fun `due actions dispatch in order exactly once`() {
        val script = DebugScenarioScript()
        val observed = mutableListOf<DebugScenarioAction>()
        script.prepare(EncounterScenario.CAT_KINDNESS)

        script.advance(0.94f, observed::add)
        assertTrue(observed.isEmpty())

        script.advance(1.23f, observed::add)
        assertEquals(
            listOf(DebugScenarioAction.HOLD_JUMP_START, DebugScenarioAction.HOLD_JUMP_END),
            observed
        )

        script.advance(10f, observed::add)
        assertEquals(
            listOf(
                DebugScenarioAction.HOLD_JUMP_START,
                DebugScenarioAction.HOLD_JUMP_END,
                DebugScenarioAction.HOLD_JUMP_START,
                DebugScenarioAction.HOLD_JUMP_END
            ),
            observed
        )
        assertEquals(0, script.pendingCountForTest())

        script.advance(20f, observed::add)
        assertEquals(4, observed.size)
    }

    @Test
    fun `preparing another scenario resets progress and replaces the plan`() {
        val script = DebugScenarioScript()
        val observed = mutableListOf<DebugScenarioAction>()
        script.prepare(EncounterScenario.CACTUS_READ)
        script.advance(4f, observed::add)

        script.prepare(EncounterScenario.EAGLE_MARK)
        observed.clear()
        script.advance(1.7f, observed::add)

        assertEquals(
            listOf(DebugScenarioAction.HOLD_JUMP_START, DebugScenarioAction.HOLD_JUMP_END),
            observed
        )
        assertEquals(2, script.pendingCountForTest())
    }

    @Test
    fun `clear removes pending actions`() {
        val script = DebugScenarioScript()
        val observed = mutableListOf<DebugScenarioAction>()
        script.prepare(EncounterScenario.FOX_MIRROR)
        script.clear()
        script.advance(100f, observed::add)

        assertTrue(observed.isEmpty())
        assertEquals(0, script.pendingCountForTest())
    }

    @Test
    fun `non finite elapsed time never dispatches`() {
        val script = DebugScenarioScript()
        val observed = mutableListOf<DebugScenarioAction>()
        script.prepare(EncounterScenario.EAGLE_MARK)

        script.advance(Float.NaN, observed::add)
        script.advance(Float.POSITIVE_INFINITY, observed::add)

        assertTrue(observed.isEmpty())
        assertEquals(4, script.pendingCountForTest())
    }

    @Test
    fun `unscripted scenarios have no automation`() {
        val unscripted = EncounterScenario.entries.filter {
            it !in setOf(
                EncounterScenario.CACTUS_READ,
                EncounterScenario.CAT_KINDNESS,
                EncounterScenario.FOX_MIRROR,
                EncounterScenario.EAGLE_MARK
            )
        }

        unscripted.forEach { scenario ->
            assertTrue("$scenario should be manual", DebugScenarioScript.stepsFor(scenario).isEmpty())
        }
    }
}
