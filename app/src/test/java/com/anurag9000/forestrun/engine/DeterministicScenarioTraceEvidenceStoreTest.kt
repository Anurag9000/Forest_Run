package com.anurag9000.forestrun.engine

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeterministicScenarioTraceEvidenceStoreTest {

    @Test
    fun `valid evidence is written with a deterministic scenario filename`() {
        val directory = Files.createTempDirectory("forest-run-trace").toFile()
        try {
            val evidence = requireNotNull(evidence(capturedAtUtcMs = 10L))

            val written = DeterministicScenarioTraceEvidenceStore.write(directory, evidence)

            assertEquals("scenario-trace-cactus_read.json", written?.name)
            assertEquals(evidence.payloadJson, written?.readText(Charsets.UTF_8))
            assertFalse(directory.resolve("scenario-trace-cactus_read.json.bak").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `same scenario atomically replaces the previous complete payload`() {
        val directory = Files.createTempDirectory("forest-run-trace").toFile()
        try {
            val first = requireNotNull(evidence(capturedAtUtcMs = 10L))
            val second = requireNotNull(evidence(capturedAtUtcMs = 20L))
            assertTrue(DeterministicScenarioTraceEvidenceStore.write(directory, first) != null)

            val written = DeterministicScenarioTraceEvidenceStore.write(directory, second)

            assertEquals(second.payloadJson, written?.readText(Charsets.UTF_8))
            assertFalse(written?.readText(Charsets.UTF_8)?.contains("\"captured_at_utc_ms\":10") == true)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `tampered digest fails closed without creating evidence`() {
        val directory = Files.createTempDirectory("forest-run-trace").toFile()
        try {
            val valid = requireNotNull(evidence(capturedAtUtcMs = 10L))
            val tampered = valid.copy(payloadSha256 = "0".repeat(64))

            assertNull(DeterministicScenarioTraceEvidenceStore.write(directory, tampered))
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `regular file cannot be used as evidence directory`() {
        val parent = Files.createTempDirectory("forest-run-trace").toFile()
        try {
            val notDirectory = parent.resolve("not-a-directory").apply { writeText("x") }
            assertNull(
                DeterministicScenarioTraceEvidenceStore.write(
                    notDirectory,
                    requireNotNull(evidence(capturedAtUtcMs = 10L))
                )
            )
            assertEquals("x", notDirectory.readText())
        } finally {
            parent.deleteRecursively()
        }
    }

    private fun evidence(capturedAtUtcMs: Long): DeterministicScenarioTraceEvidence? {
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
        return DeterministicScenarioTraceEvidenceCodec.encode(
            snapshot = DeterministicScenarioTraceSnapshot(
                scenario = scenario,
                events = events,
                overflowed = false
            ),
            candidateCommitSha = "a".repeat(40),
            artifactSha256 = "b".repeat(64),
            capturedAtUtcMs = capturedAtUtcMs
        )
    }
}
