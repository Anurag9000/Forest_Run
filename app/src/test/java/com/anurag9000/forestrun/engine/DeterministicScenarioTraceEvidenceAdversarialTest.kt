package com.anurag9000.forestrun.engine

import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeterministicScenarioTraceEvidenceAdversarialTest {

    @Test
    fun `canonical codec output passes persistence self validation`() {
        val valid = evidence()

        assertTrue(DeterministicScenarioTraceEvidenceCodec.isCanonical(valid))
    }

    @Test
    fun `envelope metadata cannot be changed independently of the signed payload`() {
        val directory = Files.createTempDirectory("forest-run-trace-adversarial").toFile()
        try {
            val valid = evidence()
            val mutations = listOf(
                valid.copy(candidateCommitSha = "c".repeat(40)),
                valid.copy(artifactSha256 = "d".repeat(64)),
                valid.copy(capturedAtUtcMs = valid.capturedAtUtcMs + 1L),
                valid.copy(scenario = EncounterScenario.EAGLE_MARK),
                valid.copy(scenarioDefinitionSha256 = "0".repeat(64)),
                valid.copy(traceContractSha256 = "1".repeat(64)),
                valid.copy(eventCount = valid.eventCount + 1)
            )

            mutations.forEach { tampered ->
                assertFalse(tampered.toString(), DeterministicScenarioTraceEvidenceCodec.isCanonical(tampered))
                assertNull(DeterministicScenarioTraceEvidenceStore.write(directory, tampered))
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `rehashed authored action schedule and lateness tampering is rejected`() {
        val valid = evidence()
        val tamperedPayloads = listOf(
            valid.payloadJson.replaceFirst(
                Regex("\\\"action\\\":\\\"[A-Z_]+\\\""),
                "\"action\":\"TAP_JUMP\""
            ),
            valid.payloadJson.replaceFirst(
                Regex("\\\"scheduled_at_micros\\\":\\d+"),
                "\"scheduled_at_micros\":1"
            ),
            valid.payloadJson.replaceFirst(
                Regex("\\\"lateness_micros\\\":\\d+"),
                "\"lateness_micros\":999999"
            )
        )

        tamperedPayloads.forEach { payload ->
            assertFalse(payload == valid.payloadJson)
            val tampered = valid.copy(
                payloadJson = payload,
                payloadSha256 = sha256(payload.toByteArray(Charsets.UTF_8))
            )
            assertFalse(DeterministicScenarioTraceEvidenceCodec.isCanonical(tampered))
        }
    }

    @Test
    fun `failed adversarial replacement preserves the last complete evidence file`() {
        val directory = Files.createTempDirectory("forest-run-trace-adversarial").toFile()
        try {
            val valid = evidence()
            val written = requireNotNull(DeterministicScenarioTraceEvidenceStore.write(directory, valid))
            val originalBytes = written.readBytes()
            val payload = valid.payloadJson.replaceFirst(
                Regex("\\\"dispatched_at_micros\\\":\\d+"),
                "\"dispatched_at_micros\":0"
            )
            val tampered = valid.copy(
                payloadJson = payload,
                payloadSha256 = sha256(payload.toByteArray(Charsets.UTF_8))
            )

            assertNull(DeterministicScenarioTraceEvidenceStore.write(directory, tampered))
            assertEquals(originalBytes.toList(), written.readBytes().toList())
            assertFalse(directory.resolve(written.name + ".bak").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun evidence(): DeterministicScenarioTraceEvidence {
        val scenario = EncounterScenario.CACTUS_READ
        val events = DebugScenarioScript.stepsFor(scenario).mapIndexed { index, step ->
            DeterministicScenarioTraceEvent(
                scenario = scenario,
                sequence = index,
                scheduledAtSeconds = step.atSeconds,
                dispatchedAtSeconds = step.atSeconds + 0.02f,
                action = step.action
            )
        }
        return requireNotNull(
            DeterministicScenarioTraceEvidenceCodec.encode(
                snapshot = DeterministicScenarioTraceSnapshot(
                    scenario = scenario,
                    events = events,
                    overflowed = false
                ),
                candidateCommitSha = "a".repeat(40),
                artifactSha256 = "b".repeat(64),
                capturedAtUtcMs = 10L
            )
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
