package com.anurag9000.forestrun.engine

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicScenarioTraceEvidenceVerifierTest {

    @Test
    fun `every authored scenario survives canonical encode persist decode verify round trip`() {
        EncounterScenario.entries.forEach { scenario ->
            val steps = DebugScenarioScript.stepsFor(scenario)
            if (steps.isEmpty()) return@forEach

            withEvidenceFile(scenario) { file, evidence ->
                val verified = requireNotNull(
                    DeterministicScenarioTraceEvidenceVerifier.verify(
                        file = file,
                        expectedScenario = scenario,
                        expectedCandidateCommitSha = evidence.candidateCommitSha.uppercase(),
                        expectedArtifactSha256 = "  ${evidence.artifactSha256.uppercase()}  ",
                        expectedPayloadSha256 = evidence.payloadSha256
                    )
                )

                assertEquals(scenario, verified.scenario)
                assertEquals(evidence.candidateCommitSha, verified.candidateCommitSha)
                assertEquals(evidence.artifactSha256, verified.artifactSha256)
                assertEquals(evidence.capturedAtUtcMs, verified.capturedAtUtcMs)
                assertEquals(evidence.eventCount, verified.eventCount)
                assertEquals(evidence.payloadSha256, verified.payloadSha256)
            }
        }
    }

    @Test
    fun `truncated wrong schema and authored event tampering are rejected`() {
        withEvidenceFile(EncounterScenario.CACTUS_READ) { file, evidence ->
            val mutations = listOf(
                evidence.payloadJson.dropLast(1),
                evidence.payloadJson.replaceFirst("\"schema_version\":2", "\"schema_version\":3"),
                evidence.payloadJson.replaceFirst("\"sequence\":0", "\"sequence\":9"),
                evidence.payloadJson.replaceFirst(
                    Regex("\\\"lateness_micros\\\":\\d+"),
                    "\"lateness_micros\":999999"
                )
            )

            mutations.forEach { payload ->
                file.writeText(payload, Charsets.UTF_8)
                assertNull(verify(file, evidence))
            }
        }
    }

    @Test
    fun `candidate artifact scenario digest and filename mismatches are rejected`() {
        withEvidenceFile(EncounterScenario.CACTUS_READ) { file, evidence ->
            assertNull(
                DeterministicScenarioTraceEvidenceVerifier.verify(
                    file,
                    evidence.scenario,
                    "c".repeat(40),
                    evidence.artifactSha256,
                    evidence.payloadSha256
                )
            )
            assertNull(
                DeterministicScenarioTraceEvidenceVerifier.verify(
                    file,
                    evidence.scenario,
                    evidence.candidateCommitSha,
                    "d".repeat(64),
                    evidence.payloadSha256
                )
            )
            assertNull(
                DeterministicScenarioTraceEvidenceVerifier.verify(
                    file,
                    EncounterScenario.EAGLE_MARK,
                    evidence.candidateCommitSha,
                    evidence.artifactSha256,
                    evidence.payloadSha256
                )
            )
            assertNull(
                DeterministicScenarioTraceEvidenceVerifier.verify(
                    file,
                    evidence.scenario,
                    evidence.candidateCommitSha,
                    evidence.artifactSha256,
                    "e".repeat(64)
                )
            )

            val renamed = file.parentFile.resolve("renamed-trace.json")
            assertTrue(file.renameTo(renamed))
            assertNull(
                DeterministicScenarioTraceEvidenceVerifier.verify(
                    renamed,
                    evidence.scenario,
                    evidence.candidateCommitSha,
                    evidence.artifactSha256,
                    evidence.payloadSha256
                )
            )
        }
    }

    @Test
    fun `invalid expected identities fail closed instead of being normalized into guesses`() {
        withEvidenceFile(EncounterScenario.CACTUS_READ) { file, evidence ->
            val invalidCommits = listOf("", "a".repeat(39), "g".repeat(40))
            invalidCommits.forEach { candidate ->
                assertNull(
                    DeterministicScenarioTraceEvidenceVerifier.verify(
                        file,
                        evidence.scenario,
                        candidate,
                        evidence.artifactSha256
                    )
                )
            }

            val invalidHashes = listOf("", "b".repeat(63), "z".repeat(64))
            invalidHashes.forEach { artifact ->
                assertNull(
                    DeterministicScenarioTraceEvidenceVerifier.verify(
                        file,
                        evidence.scenario,
                        evidence.candidateCommitSha,
                        artifact
                    )
                )
            }
        }
    }

    @Test
    fun `oversized malformed utf8 missing and directory inputs are rejected`() {
        val directory = Files.createTempDirectory("forest-run-trace-verifier-malformed").toFile()
        try {
            val oversized = directory.resolve("scenario-trace-cactus_read.json")
            oversized.writeBytes(
                ByteArray(DeterministicScenarioTraceEvidenceCodec.MAX_PAYLOAD_BYTES + 1) { 'x'.code.toByte() }
            )
            assertNull(
                DeterministicScenarioTraceEvidenceVerifier.verify(
                    oversized,
                    EncounterScenario.CACTUS_READ,
                    "a".repeat(40),
                    "b".repeat(64)
                )
            )

            val malformedUtf8 = directory.resolve("scenario-trace-eagle_mark.json")
            malformedUtf8.writeBytes(byteArrayOf(0xC3.toByte(), 0x28))
            assertNull(
                DeterministicScenarioTraceEvidenceVerifier.verify(
                    malformedUtf8,
                    EncounterScenario.EAGLE_MARK,
                    "a".repeat(40),
                    "b".repeat(64)
                )
            )

            assertNull(
                DeterministicScenarioTraceEvidenceVerifier.verify(
                    directory.resolve("missing.json"),
                    EncounterScenario.CACTUS_READ,
                    "a".repeat(40),
                    "b".repeat(64)
                )
            )
            assertNull(
                DeterministicScenarioTraceEvidenceVerifier.verify(
                    directory,
                    EncounterScenario.CACTUS_READ,
                    "a".repeat(40),
                    "b".repeat(64)
                )
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun verify(
        file: java.io.File,
        evidence: DeterministicScenarioTraceEvidence
    ): VerifiedDeterministicScenarioTraceEvidence? =
        DeterministicScenarioTraceEvidenceVerifier.verify(
            file = file,
            expectedScenario = evidence.scenario,
            expectedCandidateCommitSha = evidence.candidateCommitSha,
            expectedArtifactSha256 = evidence.artifactSha256,
            expectedPayloadSha256 = evidence.payloadSha256
        )

    private fun withEvidenceFile(
        scenario: EncounterScenario,
        block: (java.io.File, DeterministicScenarioTraceEvidence) -> Unit
    ) {
        val directory = Files.createTempDirectory("forest-run-trace-verifier").toFile()
        try {
            val evidence = evidence(scenario)
            val file = directory.resolve(DeterministicScenarioTraceEvidenceStore.fileNameFor(evidence))
            file.writeText(evidence.payloadJson, Charsets.UTF_8)
            block(file, evidence)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun evidence(scenario: EncounterScenario): DeterministicScenarioTraceEvidence {
        val events = DebugScenarioScript.stepsFor(scenario).mapIndexed { index, step ->
            DeterministicScenarioTraceEvent(
                scenario = scenario,
                sequence = index,
                scheduledAtSeconds = step.atSeconds,
                dispatchedAtSeconds = step.atSeconds + 0.015f,
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
                capturedAtUtcMs = 123_456L
            )
        )
    }
}
