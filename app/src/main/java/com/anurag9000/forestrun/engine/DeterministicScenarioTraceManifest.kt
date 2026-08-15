package com.anurag9000.forestrun.engine

import android.util.AtomicFile
import java.io.BufferedOutputStream
import java.io.File
import java.security.MessageDigest

internal data class DeterministicScenarioTraceManifestEntry(
    val scenario: EncounterScenario,
    val fileName: String,
    val payloadSha256: String,
    val scenarioDefinitionSha256: String,
    val traceContractSha256: String,
    val eventCount: Int,
    val capturedAtUtcMs: Long
)

internal data class DeterministicScenarioTraceManifest(
    val candidateCommitSha: String,
    val artifactSha256: String,
    val entries: List<DeterministicScenarioTraceManifestEntry>,
    val payloadJson: String,
    val payloadSha256: String
)

/**
 * Candidate-level binding for the complete authored deterministic trace set.
 *
 * A manifest can only be built when every currently authored scenario is
 * represented exactly once by canonical evidence for the same commit/artifact.
 * Canonical enum ordering makes the payload stable regardless of capture order.
 */
internal object DeterministicScenarioTraceManifestCodec {
    private const val SCHEMA_VERSION = 1
    internal const val MAX_PAYLOAD_BYTES = 128 * 1024
    private val commitPattern = Regex("^[0-9a-f]{40}$")
    private val sha256Pattern = Regex("^[0-9a-f]{64}$")
    private val filePattern = Regex("^scenario-trace-[a-z0-9_]+\\.json$")
    private val payloadPattern = Regex(
        """\{"schema_version":(\d+),"candidate_commit_sha":"([0-9a-f]{40})","artifact_sha256":"([0-9a-f]{64})","entry_count":(\d+),"entries":\[(.*)]\}"""
    )
    private val entryPattern = Regex(
        """\{"scenario":"([A-Z0-9_]+)","file_name":"(scenario-trace-[a-z0-9_]+\.json)","payload_sha256":"([0-9a-f]{64})","scenario_definition_sha256":"([0-9a-f]{64})","trace_contract_sha256":"([0-9a-f]{64})","event_count":(\d+),"captured_at_utc_ms":(\d+)\}"""
    )

    fun build(evidence: Collection<DeterministicScenarioTraceEvidence>): DeterministicScenarioTraceManifest? {
        if (evidence.isEmpty() || evidence.any { !DeterministicScenarioTraceEvidenceCodec.isCanonical(it) }) {
            return null
        }

        val first = evidence.first()
        if (evidence.any {
                it.candidateCommitSha != first.candidateCommitSha ||
                    it.artifactSha256 != first.artifactSha256
            }
        ) {
            return null
        }

        val expectedScenarios = authoredScenarios()
        if (evidence.size != expectedScenarios.size ||
            evidence.map { it.scenario }.toSet() != expectedScenarios.toSet()
        ) {
            return null
        }

        val byScenario = evidence.groupBy { it.scenario }
        if (byScenario.values.any { it.size != 1 }) return null
        val entries = expectedScenarios.map { scenario ->
            val item = byScenario.getValue(scenario).single()
            DeterministicScenarioTraceManifestEntry(
                scenario = scenario,
                fileName = DeterministicScenarioTraceEvidenceStore.fileNameFor(item),
                payloadSha256 = item.payloadSha256,
                scenarioDefinitionSha256 = item.scenarioDefinitionSha256,
                traceContractSha256 = item.traceContractSha256,
                eventCount = item.eventCount,
                capturedAtUtcMs = item.capturedAtUtcMs
            )
        }
        return encode(first.candidateCommitSha, first.artifactSha256, entries)
    }

    fun decodeCanonical(payloadJson: String): DeterministicScenarioTraceManifest? {
        val payloadBytes = payloadJson.toByteArray(Charsets.UTF_8)
        if (payloadBytes.isEmpty() || payloadBytes.size > MAX_PAYLOAD_BYTES) return null
        val match = payloadPattern.matchEntire(payloadJson) ?: return null
        val groups = match.groupValues
        if (groups[1].toIntOrNull() != SCHEMA_VERSION) return null
        val candidateCommitSha = groups[2]
        val artifactSha256 = groups[3]
        val entryCount = groups[4].toIntOrNull() ?: return null
        val entryText = groups[5]
        val matches = entryPattern.findAll(entryText).toList()
        if (matches.size != entryCount ||
            matches.joinToString(separator = ",") { it.value } != entryText
        ) {
            return null
        }

        val entries = matches.map { entryMatch ->
            val values = entryMatch.groupValues
            val scenario = EncounterScenario.entries.firstOrNull { it.name == values[1] }
                ?: return null
            DeterministicScenarioTraceManifestEntry(
                scenario = scenario,
                fileName = values[2],
                payloadSha256 = values[3],
                scenarioDefinitionSha256 = values[4],
                traceContractSha256 = values[5],
                eventCount = values[6].toIntOrNull() ?: return null,
                capturedAtUtcMs = values[7].toLongOrNull() ?: return null
            )
        }
        val manifest = DeterministicScenarioTraceManifest(
            candidateCommitSha = candidateCommitSha,
            artifactSha256 = artifactSha256,
            entries = entries,
            payloadJson = payloadJson,
            payloadSha256 = sha256(payloadBytes)
        )
        return manifest.takeIf(::isCanonical)
    }

    fun isCanonical(manifest: DeterministicScenarioTraceManifest): Boolean {
        if (!commitPattern.matches(manifest.candidateCommitSha) ||
            !sha256Pattern.matches(manifest.artifactSha256) ||
            !sha256Pattern.matches(manifest.payloadSha256)
        ) {
            return false
        }

        val expectedScenarios = authoredScenarios()
        if (manifest.entries.size != expectedScenarios.size ||
            manifest.entries.map { it.scenario } != expectedScenarios
        ) {
            return false
        }
        if (manifest.entries.map { it.scenario }.distinct().size != manifest.entries.size ||
            manifest.entries.map { it.fileName }.distinct().size != manifest.entries.size
        ) {
            return false
        }

        manifest.entries.forEach { entry ->
            if (!filePattern.matches(entry.fileName) ||
                !sha256Pattern.matches(entry.payloadSha256) ||
                !sha256Pattern.matches(entry.scenarioDefinitionSha256) ||
                !sha256Pattern.matches(entry.traceContractSha256) ||
                entry.eventCount <= 0 ||
                entry.capturedAtUtcMs < 0L ||
                entry.fileName != "scenario-trace-${entry.scenario.name.lowercase()}.json" ||
                entry.scenarioDefinitionSha256 != EncounterScenarioFingerprint.sha256(entry.scenario) ||
                entry.traceContractSha256 != EncounterScenarioFingerprint.traceContractSha256(entry.scenario) ||
                entry.eventCount != DebugScenarioScript.stepsFor(entry.scenario).size
            ) {
                return false
            }
        }

        val expectedPayload = canonicalPayload(
            candidateCommitSha = manifest.candidateCommitSha,
            artifactSha256 = manifest.artifactSha256,
            entries = manifest.entries
        )
        val payloadBytes = manifest.payloadJson.toByteArray(Charsets.UTF_8)
        return payloadBytes.isNotEmpty() &&
            payloadBytes.size <= MAX_PAYLOAD_BYTES &&
            manifest.payloadJson == expectedPayload &&
            manifest.payloadSha256 == sha256(payloadBytes)
    }

    internal fun authoredScenarios(): List<EncounterScenario> =
        EncounterScenario.entries.filter { DebugScenarioScript.stepsFor(it).isNotEmpty() }

    private fun encode(
        candidateCommitSha: String,
        artifactSha256: String,
        entries: List<DeterministicScenarioTraceManifestEntry>
    ): DeterministicScenarioTraceManifest? {
        if (!commitPattern.matches(candidateCommitSha) || !sha256Pattern.matches(artifactSha256)) {
            return null
        }
        val payloadJson = canonicalPayload(candidateCommitSha, artifactSha256, entries)
        val payloadBytes = payloadJson.toByteArray(Charsets.UTF_8)
        if (payloadBytes.isEmpty() || payloadBytes.size > MAX_PAYLOAD_BYTES) return null
        val manifest = DeterministicScenarioTraceManifest(
            candidateCommitSha = candidateCommitSha,
            artifactSha256 = artifactSha256,
            entries = entries,
            payloadJson = payloadJson,
            payloadSha256 = sha256(payloadBytes)
        )
        return manifest.takeIf(::isCanonical)
    }

    private fun canonicalPayload(
        candidateCommitSha: String,
        artifactSha256: String,
        entries: List<DeterministicScenarioTraceManifestEntry>
    ): String = buildString(512 + entries.size * 384) {
        append('{')
        append("\"schema_version\":").append(SCHEMA_VERSION)
        append(",\"candidate_commit_sha\":\"").append(candidateCommitSha).append('"')
        append(",\"artifact_sha256\":\"").append(artifactSha256).append('"')
        append(",\"entry_count\":").append(entries.size)
        append(",\"entries\":[")
        entries.forEachIndexed { index, entry ->
            if (index > 0) append(',')
            append('{')
            append("\"scenario\":\"").append(entry.scenario.name).append('"')
            append(",\"file_name\":\"").append(entry.fileName).append('"')
            append(",\"payload_sha256\":\"").append(entry.payloadSha256).append('"')
            append(",\"scenario_definition_sha256\":\"")
                .append(entry.scenarioDefinitionSha256).append('"')
            append(",\"trace_contract_sha256\":\"")
                .append(entry.traceContractSha256).append('"')
            append(",\"event_count\":").append(entry.eventCount)
            append(",\"captured_at_utc_ms\":").append(entry.capturedAtUtcMs)
            append('}')
        }
        append("]}")
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

/** Atomic persistence for a validated candidate-level trace manifest. */
internal object DeterministicScenarioTraceManifestStore {
    const val FILE_NAME = "scenario-trace-manifest.json"

    fun write(directory: File, manifest: DeterministicScenarioTraceManifest): File? {
        if (!DeterministicScenarioTraceManifestCodec.isCanonical(manifest)) return null
        if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory) return null

        val destination = File(directory, FILE_NAME)
        val atomicFile = AtomicFile(destination)
        val activeStream = try {
            atomicFile.startWrite()
        } catch (_: Exception) {
            return null
        }
        return try {
            val output = BufferedOutputStream(activeStream)
            output.write(manifest.payloadJson.toByteArray(Charsets.UTF_8))
            output.flush()
            atomicFile.finishWrite(activeStream)
            destination
        } catch (_: Exception) {
            runCatching { atomicFile.failWrite(activeStream) }
            null
        }
    }
}
