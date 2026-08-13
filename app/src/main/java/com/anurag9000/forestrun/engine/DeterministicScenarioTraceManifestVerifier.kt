package com.anurag9000.forestrun.engine

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

internal data class VerifiedDeterministicScenarioTraceManifest(
    val candidateCommitSha: String,
    val artifactSha256: String,
    val manifestPayloadSha256: String,
    val verifiedScenarios: List<EncounterScenario>
)

/**
 * Verifies a complete candidate trace evidence directory from persisted bytes.
 *
 * The manifest and every child trace are independently decoded and bound to the
 * expected candidate/artifact. Extra trace-shaped files fail closed so stale or
 * substituted scenario evidence cannot silently coexist with a valid manifest.
 */
internal object DeterministicScenarioTraceManifestVerifier {
    private val commitPattern = Regex("^[0-9a-f]{40}$")
    private val sha256Pattern = Regex("^[0-9a-f]{64}$")

    fun verify(
        directory: File,
        expectedCandidateCommitSha: String,
        expectedArtifactSha256: String,
        expectedManifestPayloadSha256: String? = null
    ): VerifiedDeterministicScenarioTraceManifest? {
        val expectedCommit = expectedCandidateCommitSha.trim().lowercase()
        val expectedArtifact = expectedArtifactSha256.trim().lowercase()
        val expectedManifest = expectedManifestPayloadSha256?.trim()?.lowercase()
        if (!directory.isDirectory ||
            !commitPattern.matches(expectedCommit) ||
            !sha256Pattern.matches(expectedArtifact) ||
            (expectedManifest != null && !sha256Pattern.matches(expectedManifest))
        ) {
            return null
        }

        val manifestFile = directory.resolve(DeterministicScenarioTraceManifestStore.FILE_NAME)
        val manifestPayload = readBounded(
            manifestFile,
            DeterministicScenarioTraceManifestCodec.MAX_PAYLOAD_BYTES
        ) ?: return null
        val manifestJson = manifestPayload.toString(Charsets.UTF_8)
        if (!manifestJson.toByteArray(Charsets.UTF_8).contentEquals(manifestPayload)) return null

        val manifest = DeterministicScenarioTraceManifestCodec.decodeCanonical(manifestJson)
            ?: return null
        if (manifest.candidateCommitSha != expectedCommit ||
            manifest.artifactSha256 != expectedArtifact ||
            (expectedManifest != null && manifest.payloadSha256 != expectedManifest)
        ) {
            return null
        }

        val expectedTraceNames = manifest.entries.map { it.fileName }.toSet()
        val actualTraceNames = directory.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile }
            .map { it.name }
            .filter { name ->
                name != DeterministicScenarioTraceManifestStore.FILE_NAME &&
                    name.startsWith("scenario-trace-") &&
                    name.endsWith(".json")
            }
            .toSet()
        if (actualTraceNames != expectedTraceNames) return null

        val verifiedScenarios = ArrayList<EncounterScenario>(manifest.entries.size)
        manifest.entries.forEach { entry ->
            val verified = DeterministicScenarioTraceEvidenceVerifier.verify(
                file = directory.resolve(entry.fileName),
                expectedScenario = entry.scenario,
                expectedCandidateCommitSha = expectedCommit,
                expectedArtifactSha256 = expectedArtifact,
                expectedPayloadSha256 = entry.payloadSha256
            ) ?: return null
            if (verified.capturedAtUtcMs != entry.capturedAtUtcMs ||
                verified.eventCount != entry.eventCount
            ) {
                return null
            }
            verifiedScenarios += verified.scenario
        }

        return VerifiedDeterministicScenarioTraceManifest(
            candidateCommitSha = manifest.candidateCommitSha,
            artifactSha256 = manifest.artifactSha256,
            manifestPayloadSha256 = manifest.payloadSha256,
            verifiedScenarios = verifiedScenarios
        )
    }

    private fun readBounded(file: File, maxBytes: Int): ByteArray? {
        if (!file.isFile || file.length() > maxBytes) return null
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
                    if (total > maxBytes) return null
                    output.write(buffer, 0, read)
                }
                output.toByteArray().takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            null
        }
    }
}
