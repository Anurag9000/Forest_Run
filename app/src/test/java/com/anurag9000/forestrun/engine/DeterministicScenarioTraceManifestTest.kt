package com.anurag9000.forestrun.engine

import java.nio.file.Files
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeterministicScenarioTraceManifestTest {

    @Test
    fun `manifest is complete deterministic and independent of capture collection order`() {
        val evidence = evidenceSet()
        val canonical = requireNotNull(DeterministicScenarioTraceManifestCodec.build(evidence))

        repeat(32) { seed ->
            val shuffled = requireNotNull(
                DeterministicScenarioTraceManifestCodec.build(evidence.shuffled(Random(seed)))
            )
            assertEquals(canonical.payloadJson, shuffled.payloadJson)
            assertEquals(canonical.payloadSha256, shuffled.payloadSha256)
            assertEquals(
                DeterministicScenarioTraceManifestCodec.authoredScenarios(),
                shuffled.entries.map { it.scenario }
            )
        }

        val decoded = requireNotNull(
            DeterministicScenarioTraceManifestCodec.decodeCanonical(canonical.payloadJson)
        )
        assertEquals(canonical, decoded)
        assertTrue(DeterministicScenarioTraceManifestCodec.isCanonical(decoded))
    }

    @Test
    fun `manifest rejects missing duplicate mixed identity and noncanonical child evidence`() {
        val evidence = evidenceSet()
        assertTrue(evidence.size > 1)

        assertNull(DeterministicScenarioTraceManifestCodec.build(evidence.dropLast(1)))
        assertNull(
            DeterministicScenarioTraceManifestCodec.build(
                evidence.dropLast(1) + evidence.first()
            )
        )

        val otherCommit = evidenceSet(candidateCommitSha = "c".repeat(40))
        assertNull(
            DeterministicScenarioTraceManifestCodec.build(
                evidence.dropLast(1) + otherCommit.last()
            )
        )
        val otherArtifact = evidenceSet(artifactSha256 = "d".repeat(64))
        assertNull(
            DeterministicScenarioTraceManifestCodec.build(
                evidence.dropLast(1) + otherArtifact.last()
            )
        )

        val corrupted = evidence.last().copy(eventCount = evidence.last().eventCount + 1)
        assertFalse(DeterministicScenarioTraceEvidenceCodec.isCanonical(corrupted))
        assertNull(
            DeterministicScenarioTraceManifestCodec.build(
                evidence.dropLast(1) + corrupted
            )
        )
    }

    @Test
    fun `manifest canonical decoder rejects rehashed semantic and structural tampering`() {
        val manifest = requireNotNull(
            DeterministicScenarioTraceManifestCodec.build(evidenceSet())
        )
        val firstEntry = manifest.entries.first()
        val tamperedPayloads = listOf(
            manifest.payloadJson.replaceFirst("\"schema_version\":1", "\"schema_version\":2"),
            manifest.payloadJson.replaceFirst(
                "\"entry_count\":${manifest.entries.size}",
                "\"entry_count\":${manifest.entries.size + 1}"
            ),
            manifest.payloadJson.replaceFirst(
                firstEntry.payloadSha256,
                "e".repeat(64)
            ),
            manifest.payloadJson.replaceFirst(
                firstEntry.fileName,
                "scenario-trace-renamed.json"
            )
        )

        tamperedPayloads.forEach { payload ->
            assertFalse(payload == manifest.payloadJson)
            assertNull(DeterministicScenarioTraceManifestCodec.decodeCanonical(payload))
        }
    }

    @Test
    fun `complete persisted directory verifies as one candidate bound evidence set`() {
        withPersistedManifest { directory, manifest ->
            val verified = requireNotNull(
                DeterministicScenarioTraceManifestVerifier.verify(
                    directory = directory,
                    expectedCandidateCommitSha = manifest.candidateCommitSha.uppercase(),
                    expectedArtifactSha256 = " ${manifest.artifactSha256.uppercase()} ",
                    expectedManifestPayloadSha256 = manifest.payloadSha256
                )
            )

            assertEquals(manifest.candidateCommitSha, verified.candidateCommitSha)
            assertEquals(manifest.artifactSha256, verified.artifactSha256)
            assertEquals(manifest.payloadSha256, verified.manifestPayloadSha256)
            assertEquals(manifest.entries.map { it.scenario }, verified.verifiedScenarios)
        }
    }

    @Test
    fun `directory verification fails closed for missing corrupt renamed and extra trace files`() {
        withPersistedManifest { directory, manifest ->
            val target = directory.resolve(manifest.entries.first().fileName)
            val original = target.readBytes()

            assertTrue(target.delete())
            assertNull(verify(directory, manifest))
            target.writeBytes(original)

            target.appendText("\n")
            assertNull(verify(directory, manifest))
            target.writeBytes(original)

            val renamed = directory.resolve("scenario-trace-unexpected.json")
            assertTrue(target.renameTo(renamed))
            assertNull(verify(directory, manifest))
            assertTrue(renamed.renameTo(target))

            val extra = directory.resolve("scenario-trace-stale.json")
            extra.writeText("{}")
            assertNull(verify(directory, manifest))
            assertTrue(extra.delete())

            assertTrue(verify(directory, manifest) != null)
        }
    }

    @Test
    fun `directory verification binds candidate artifact and manifest digest`() {
        withPersistedManifest { directory, manifest ->
            assertNull(
                DeterministicScenarioTraceManifestVerifier.verify(
                    directory,
                    "c".repeat(40),
                    manifest.artifactSha256,
                    manifest.payloadSha256
                )
            )
            assertNull(
                DeterministicScenarioTraceManifestVerifier.verify(
                    directory,
                    manifest.candidateCommitSha,
                    "d".repeat(64),
                    manifest.payloadSha256
                )
            )
            assertNull(
                DeterministicScenarioTraceManifestVerifier.verify(
                    directory,
                    manifest.candidateCommitSha,
                    manifest.artifactSha256,
                    "e".repeat(64)
                )
            )
        }
    }

    @Test
    fun `rejected manifest replacement preserves the last complete manifest atomically`() {
        withPersistedManifest { directory, manifest ->
            val file = directory.resolve(DeterministicScenarioTraceManifestStore.FILE_NAME)
            val before = file.readBytes()
            val invalid = manifest.copy(entries = manifest.entries.dropLast(1))

            assertFalse(DeterministicScenarioTraceManifestCodec.isCanonical(invalid))
            assertNull(DeterministicScenarioTraceManifestStore.write(directory, invalid))
            assertEquals(before.toList(), file.readBytes().toList())
            assertFalse(directory.resolve(file.name + ".bak").exists())
        }
    }

    private fun verify(
        directory: java.io.File,
        manifest: DeterministicScenarioTraceManifest
    ): VerifiedDeterministicScenarioTraceManifest? =
        DeterministicScenarioTraceManifestVerifier.verify(
            directory = directory,
            expectedCandidateCommitSha = manifest.candidateCommitSha,
            expectedArtifactSha256 = manifest.artifactSha256,
            expectedManifestPayloadSha256 = manifest.payloadSha256
        )

    private fun withPersistedManifest(
        block: (java.io.File, DeterministicScenarioTraceManifest) -> Unit
    ) {
        val directory = Files.createTempDirectory("forest-run-trace-manifest").toFile()
        try {
            val evidence = evidenceSet()
            evidence.forEach { item ->
                requireNotNull(DeterministicScenarioTraceEvidenceStore.write(directory, item))
            }
            val manifest = requireNotNull(DeterministicScenarioTraceManifestCodec.build(evidence))
            requireNotNull(DeterministicScenarioTraceManifestStore.write(directory, manifest))
            block(directory, manifest)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun evidenceSet(
        candidateCommitSha: String = "a".repeat(40),
        artifactSha256: String = "b".repeat(64)
    ): List<DeterministicScenarioTraceEvidence> =
        DeterministicScenarioTraceManifestCodec.authoredScenarios().map { scenario ->
            val events = DebugScenarioScript.stepsFor(scenario).mapIndexed { index, step ->
                DeterministicScenarioTraceEvent(
                    scenario = scenario,
                    sequence = index,
                    scheduledAtSeconds = step.atSeconds,
                    dispatchedAtSeconds = step.atSeconds + 0.01f,
                    action = step.action
                )
            }
            requireNotNull(
                DeterministicScenarioTraceEvidenceCodec.encode(
                    snapshot = DeterministicScenarioTraceSnapshot(
                        scenario = scenario,
                        events = events,
                        overflowed = false
                    ),
                    candidateCommitSha = candidateCommitSha,
                    artifactSha256 = artifactSha256,
                    capturedAtUtcMs = 1_000L + scenario.ordinal
                )
            )
        }
}
