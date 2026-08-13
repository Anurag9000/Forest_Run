package com.anurag9000.forestrun.engine

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

internal data class VerifiedDeterministicScenarioTraceEvidence(
    val scenario: EncounterScenario,
    val candidateCommitSha: String,
    val artifactSha256: String,
    val capturedAtUtcMs: Long,
    val eventCount: Int,
    val payloadSha256: String
)

/**
 * Independent read/verify boundary for persisted deterministic trace evidence.
 *
 * The verifier never trusts the in-memory object that produced the file. It
 * performs a bounded read, rejects non-canonical UTF-8/JSON, revalidates the
 * authored scenario contract through the codec, and binds the result to the
 * candidate commit and artifact expected by release tooling.
 */
internal object DeterministicScenarioTraceEvidenceVerifier {
    private val commitPattern = Regex("^[0-9a-f]{40}$")
    private val sha256Pattern = Regex("^[0-9a-f]{64}$")

    fun verify(
        file: File,
        expectedScenario: EncounterScenario,
        expectedCandidateCommitSha: String,
        expectedArtifactSha256: String,
        expectedPayloadSha256: String? = null
    ): VerifiedDeterministicScenarioTraceEvidence? {
        val expectedCommit = expectedCandidateCommitSha.trim().lowercase()
        val expectedArtifact = expectedArtifactSha256.trim().lowercase()
        val expectedPayload = expectedPayloadSha256?.trim()?.lowercase()
        if (!commitPattern.matches(expectedCommit) ||
            !sha256Pattern.matches(expectedArtifact) ||
            (expectedPayload != null && !sha256Pattern.matches(expectedPayload))
        ) {
            return null
        }

        val payloadBytes = readBounded(file) ?: return null
        val payloadJson = payloadBytes.toString(Charsets.UTF_8)
        if (!payloadJson.toByteArray(Charsets.UTF_8).contentEquals(payloadBytes)) {
            return null
        }

        val evidence = DeterministicScenarioTraceEvidenceCodec.decodeCanonical(payloadJson)
            ?: return null
        if (evidence.scenario != expectedScenario ||
            evidence.candidateCommitSha != expectedCommit ||
            evidence.artifactSha256 != expectedArtifact ||
            (expectedPayload != null && evidence.payloadSha256 != expectedPayload) ||
            file.name != DeterministicScenarioTraceEvidenceStore.fileNameFor(evidence)
        ) {
            return null
        }

        return VerifiedDeterministicScenarioTraceEvidence(
            scenario = evidence.scenario,
            candidateCommitSha = evidence.candidateCommitSha,
            artifactSha256 = evidence.artifactSha256,
            capturedAtUtcMs = evidence.capturedAtUtcMs,
            eventCount = evidence.eventCount,
            payloadSha256 = evidence.payloadSha256
        )
    }

    private fun readBounded(file: File): ByteArray? {
        if (!file.isFile || file.length() > DeterministicScenarioTraceEvidenceCodec.MAX_PAYLOAD_BYTES) {
            return null
        }

        return try {
            FileInputStream(file).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    total += read
                    if (total > DeterministicScenarioTraceEvidenceCodec.MAX_PAYLOAD_BYTES) {
                        return null
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray().takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            null
        }
    }
}
