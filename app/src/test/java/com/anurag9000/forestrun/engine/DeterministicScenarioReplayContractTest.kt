package com.anurag9000.forestrun.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicScenarioReplayContractTest {

    @Test
    fun `complete authored script is accepted`() {
        assertTrue(
            DeterministicScenarioReplayContract.matches(
                snapshotFor(EncounterScenario.CACTUS_READ)
            )
        )
    }

    @Test
    fun `unscripted scenario cannot produce evidence`() {
        val snapshot = DeterministicScenarioTraceSnapshot(
            scenario = EncounterScenario.GHOST_READABILITY,
            events = emptyList(),
            overflowed = false
        )

        assertTrue(snapshot.isReplayable)
        assertFalse(DeterministicScenarioReplayContract.matches(snapshot))
        assertFalse(
            DeterministicScenarioTraceEvidenceCodec.encode(
                snapshot,
                "a".repeat(40),
                "b".repeat(64),
                1L
            ) != null
        )
    }

    @Test
    fun `missing extra rescheduled and substituted actions are rejected`() {
        val valid = snapshotFor(EncounterScenario.CACTUS_READ)
        assertFalse(
            DeterministicScenarioReplayContract.matches(
                valid.copy(events = valid.events.dropLast(1))
            )
        )
        assertFalse(
            DeterministicScenarioReplayContract.matches(
                valid.copy(events = valid.events + valid.events.last().copy(sequence = 4))
            )
        )
        assertFalse(
            DeterministicScenarioReplayContract.matches(
                valid.copy(
                    events = valid.events.toMutableList().also { events ->
                        events[0] = events[0].copy(scheduledAtSeconds = 3.17f)
                    }
                )
            )
        )
        assertFalse(
            DeterministicScenarioReplayContract.matches(
                valid.copy(
                    events = valid.events.toMutableList().also { events ->
                        events[0] = events[0].copy(action = DebugScenarioAction.TAP_JUMP)
                    }
                )
            )
        )
    }

    private fun snapshotFor(scenario: EncounterScenario): DeterministicScenarioTraceSnapshot =
        DeterministicScenarioTraceSnapshot(
            scenario = scenario,
            events = DebugScenarioScript.stepsFor(scenario).mapIndexed { index, step ->
                DeterministicScenarioTraceEvent(
                    scenario = scenario,
                    sequence = index,
                    scheduledAtSeconds = step.atSeconds,
                    dispatchedAtSeconds = step.atSeconds + 0.02f,
                    action = step.action
                )
            },
            overflowed = false
        )
}
