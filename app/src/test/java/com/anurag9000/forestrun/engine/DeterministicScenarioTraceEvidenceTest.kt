package com.anurag9000.forestrun.engine

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicScenarioTraceEvidenceTest {

    @Test
    fun `normal scripts trace by default and remain available after clear`() {
        val script = DebugScenarioScript()
        val dispatched = mutableListOf<DebugScenarioAction>()
        script.prepare(EncounterScenario.CAT_KINDNESS)
        script.advance(5f) { dispatched += it }
        script.clear()

        val snapshot = script.traceSnapshot()
        assertTrue(snapshot.isReplayable)
        assertTrue(DeterministicScenarioReplayContract.matches(snapshot))
        assertEquals(dispatched, snapshot.events.map { it.action })
        assertEquals(EncounterScenario.CAT_KINDNESS, snapshot.scenario)
    }

    @Test
    fun `evidence encoding is stable finite candidate bound and contract bound`() {
        val snapshot = replayableSnapshot()
        val evidence = DeterministicScenarioTraceEvidenceCodec.encode(
            snapshot = snapshot,
            candidateCommitSha = "A".repeat(40),
            artifactSha256 = "B".repeat(64),
            capturedAtUtcMs = 1_775_000_000_000L
        )

        assertNotNull(evidence)
        evidence!!
        assertEquals("a".repeat(40), evidence.candidateCommitSha)
        assertEquals("b".repeat(64), evidence.artifactSha256)
        assertEquals(4, evidence.eventCount)
        assertEquals(
            EncounterScenarioFingerprint.sha256(EncounterScenario.CACTUS_READ),
            evidence.scenarioDefinitionSha256
        )
        assertEquals(
            EncounterScenarioFingerprint.traceContractSha256(EncounterScenario.CACTUS_READ),
            evidence.traceContractSha256
        )
        assertTrue(evidence.payloadJson.startsWith("{\"schema_version\":2"))
        assertTrue(evidence.payloadJson.contains("\"scenario\":\"CACTUS_READ\""))
        assertTrue(evidence.payloadJson.contains("\"scenario_definition_sha256\":"))
        assertTrue(evidence.payloadJson.contains("\"trace_contract_sha256\":"))
        assertTrue(evidence.payloadJson.contains("\"lateness_micros\":20000"))
        assertFalse(evidence.payloadJson.contains("NaN"))
        assertFalse(evidence.payloadJson.contains("Infinity"))
        assertEquals(sha256(evidence.payloadJson), evidence.payloadSha256)

        val repeated = DeterministicScenarioTraceEvidenceCodec.encode(
            snapshot,
            "a".repeat(40),
            "b".repeat(64),
            1_775_000_000_000L
        )
        assertEquals(evidence.payloadJson, repeated?.payloadJson)
        assertEquals(evidence.payloadSha256, repeated?.payloadSha256)
    }

    @Test
    fun `incomplete altered overflowed or unbound traces fail closed`() {
        val valid = replayableSnapshot()
        val incomplete = valid.copy(events = valid.events.dropLast(1))
        val alteredSchedule = valid.copy(
            events = valid.events.toMutableList().also { events ->
                events[0] = events[0].copy(scheduledAtSeconds = 3.17f)
            }
        )
        val alteredAction = valid.copy(
            events = valid.events.toMutableList().also { events ->
                events[0] = events[0].copy(action = DebugScenarioAction.TAP_JUMP)
            }
        )
        val overflowed = valid.copy(overflowed = true)

        for (snapshot in listOf(incomplete, alteredSchedule, alteredAction, overflowed)) {
            assertNull(
                DeterministicScenarioTraceEvidenceCodec.encode(
                    snapshot,
                    "a".repeat(40),
                    "b".repeat(64),
                    1L
                )
            )
        }
        assertNull(
            DeterministicScenarioTraceEvidenceCodec.encode(
                valid,
                "not-a-commit",
                "b".repeat(64),
                1L
            )
        )
        assertNull(
            DeterministicScenarioTraceEvidenceCodec.encode(
                valid,
                "a".repeat(40),
                "not-an-artifact",
                1L
            )
        )
        assertNull(
            DeterministicScenarioTraceEvidenceCodec.encode(
                valid,
                "a".repeat(40),
                "b".repeat(64),
                -1L
            )
        )
    }

    private fun replayableSnapshot(): DeterministicScenarioTraceSnapshot {
        val scenario = EncounterScenario.CACTUS_READ
        val steps = DebugScenarioScript.stepsFor(scenario)
        return DeterministicScenarioTraceSnapshot(
            scenario = scenario,
            events = steps.mapIndexed { index, step ->
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

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
