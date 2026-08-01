package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicScenarioTraceTest {

    @Test
    fun `every authored deterministic input script satisfies the state contract`() {
        EncounterScenario.entries.forEach { scenario ->
            val validation = DebugScenarioInputContract.validate(
                DebugScenarioScript.stepsFor(scenario)
            )
            assertTrue(
                "${scenario.name}: ${validation.violations.joinToString()}",
                validation.isValid
            )
        }
    }

    @Test
    fun `contract rejects time reversal overlap and unbalanced actions`() {
        val validation = DebugScenarioInputContract.validate(
            listOf(
                DebugScenarioStep(1.0f, DebugScenarioAction.HOLD_JUMP_END),
                DebugScenarioStep(0.5f, DebugScenarioAction.DUCK_START),
                DebugScenarioStep(0.6f, DebugScenarioAction.HOLD_JUMP_START)
            )
        )

        assertFalse(validation.isValid)
        assertTrue(validation.violations.any { "was not active" in it })
        assertTrue(validation.violations.any { "earlier" in it })
        assertTrue(validation.violations.any { "during an active duck" in it })
        assertTrue(validation.violations.any { "still active" in it })
    }

    @Test
    fun `script records only successfully dispatched actions in exact order`() {
        val recorder = DeterministicScenarioTraceRecorder()
        val script = DebugScenarioScript()
        script.prepare(EncounterScenario.CACTUS_READ, recorder)
        val dispatched = mutableListOf<DebugScenarioAction>()

        script.advance(3.30f) { dispatched += it }
        script.advance(3.60f) { dispatched += it }
        script.advance(5.50f) { dispatched += it }

        val snapshot = recorder.snapshot()
        assertTrue(snapshot.isReplayable)
        assertEquals(DebugScenarioScript.stepsFor(EncounterScenario.CACTUS_READ).size, snapshot.events.size)
        assertEquals(dispatched, snapshot.events.map { it.action })
        assertEquals(listOf(0, 1, 2, 3), snapshot.events.map { it.sequence })
        assertTrue(snapshot.events.all { it.dispatchedAtSeconds >= it.scheduledAtSeconds })
    }

    @Test
    fun `dispatch exception leaves failing action pending and unrecorded`() {
        val recorder = DeterministicScenarioTraceRecorder()
        val script = DebugScenarioScript()
        script.prepare(EncounterScenario.CACTUS_READ, recorder)

        runCatching {
            script.advance(4f) { throw IllegalStateException("dispatch failed") }
        }

        assertEquals(4, script.pendingCountForTest())
        assertTrue(recorder.snapshot().events.isEmpty())
    }

    @Test
    fun `trace capacity fails closed without overwriting earlier evidence`() {
        val recorder = DeterministicScenarioTraceRecorder(maxEvents = 1)
        recorder.begin(EncounterScenario.CACTUS_READ)

        assertTrue(
            recorder.record(
                scenario = EncounterScenario.CACTUS_READ,
                sequence = 0,
                scheduledAtSeconds = 1f,
                dispatchedAtSeconds = 1.1f,
                action = DebugScenarioAction.TAP_JUMP
            )
        )
        assertFalse(
            recorder.record(
                scenario = EncounterScenario.CACTUS_READ,
                sequence = 1,
                scheduledAtSeconds = 2f,
                dispatchedAtSeconds = 2.1f,
                action = DebugScenarioAction.TAP_JUMP
            )
        )

        val snapshot = recorder.snapshot()
        assertTrue(snapshot.overflowed)
        assertFalse(snapshot.isReplayable)
        assertEquals(1, snapshot.events.size)
    }

    @Test
    fun `snapshot is detached from later recorder reuse`() {
        val recorder = DeterministicScenarioTraceRecorder()
        recorder.begin(EncounterScenario.CACTUS_READ)
        recorder.record(
            scenario = EncounterScenario.CACTUS_READ,
            sequence = 0,
            scheduledAtSeconds = 1f,
            dispatchedAtSeconds = 1f,
            action = DebugScenarioAction.TAP_JUMP
        )
        val first = recorder.snapshot()

        recorder.begin(EncounterScenario.EAGLE_MARK)

        assertEquals(EncounterScenario.CACTUS_READ, first.scenario)
        assertEquals(1, first.events.size)
        assertTrue(first.isReplayable)
        assertTrue(recorder.snapshot().events.isEmpty())
    }
}
