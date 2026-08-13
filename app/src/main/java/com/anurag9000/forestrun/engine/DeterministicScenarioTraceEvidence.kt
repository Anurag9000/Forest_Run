package com.anurag9000.forestrun.engine

import java.security.MessageDigest
import kotlin.math.roundToLong

internal data class DeterministicScenarioTraceEvidence(
    val candidateCommitSha: String,
    val artifactSha256: String,
    val capturedAtUtcMs: Long,
    val scenario: EncounterScenario,
    val scenarioDefinitionSha256: String,
    val traceContractSha256: String,
    val eventCount: Int,
    val payloadJson: String,
    val payloadSha256: String
)

/** Stable, bounded, privacy-preserving encoding for deterministic input traces. */
internal object DeterministicScenarioTraceEvidenceCodec {
    private const val SCHEMA_VERSION = 2
    internal const val MAX_PAYLOAD_BYTES = 256 * 1024
    private const val MICROS_PER_SECOND = 1_000_000.0
    private val commitPattern = Regex("^[0-9a-f]{40}$")
    private val sha256Pattern = Regex("^[0-9a-f]{64}$")
    private val eventPattern = Regex(
        """\{"sequence":(\d+),"scheduled_at_micros":(\d+),"dispatched_at_micros":(\d+),"lateness_micros":(\d+),"action":"([A-Z_]+)"\}"""
    )
    private val payloadPattern = Regex(
        """\{"schema_version":(\d+),"candidate_commit_sha":"([0-9a-f]{40})","artifact_sha256":"([0-9a-f]{64})","captured_at_utc_ms":(\d+),"scenario":"([A-Z0-9_]+)","scenario_definition_sha256":"([0-9a-f]{64})","trace_contract_sha256":"([0-9a-f]{64})","event_count":(\d+),"events":\[(.*)]\}"""
    )

    fun encode(
        snapshot: DeterministicScenarioTraceSnapshot,
        candidateCommitSha: String,
        artifactSha256: String,
        capturedAtUtcMs: Long
    ): DeterministicScenarioTraceEvidence? {
        val commit = candidateCommitSha.trim().lowercase()
        val artifact = artifactSha256.trim().lowercase()
        if (!DeterministicScenarioReplayContract.matches(snapshot) ||
            !commitPattern.matches(commit) ||
            !sha256Pattern.matches(artifact) ||
            capturedAtUtcMs < 0L
        ) {
            return null
        }
        val scenario = snapshot.scenario ?: return null
        val scenarioDefinitionSha = EncounterScenarioFingerprint.sha256(scenario)
        val traceContractSha = EncounterScenarioFingerprint.traceContractSha256(scenario)
        val prefix = metadataPrefix(
            candidateCommitSha = commit,
            artifactSha256 = artifact,
            capturedAtUtcMs = capturedAtUtcMs,
            scenario = scenario,
            scenarioDefinitionSha256 = scenarioDefinitionSha,
            traceContractSha256 = traceContractSha,
            eventCount = snapshot.events.size
        )

        val payload = buildString(prefix.length + snapshot.events.size * 128 + 2) {
            append(prefix)
            snapshot.events.forEachIndexed { index, event ->
                if (index > 0) append(',')
                val scheduledMicros = secondsToMicros(event.scheduledAtSeconds)
                val dispatchedMicros = secondsToMicros(event.dispatchedAtSeconds)
                if (scheduledMicros == null || dispatchedMicros == null || dispatchedMicros < scheduledMicros) {
                    return null
                }
                append('{')
                append("\"sequence\":").append(event.sequence)
                append(",\"scheduled_at_micros\":").append(scheduledMicros)
                append(",\"dispatched_at_micros\":").append(dispatchedMicros)
                append(",\"lateness_micros\":").append(dispatchedMicros - scheduledMicros)
                append(",\"action\":\"").append(event.action.name).append('"')
                append('}')
            }
            append("]}")
        }
        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        if (payloadBytes.isEmpty() || payloadBytes.size > MAX_PAYLOAD_BYTES) return null

        return DeterministicScenarioTraceEvidence(
            candidateCommitSha = commit,
            artifactSha256 = artifact,
            capturedAtUtcMs = capturedAtUtcMs,
            scenario = scenario,
            scenarioDefinitionSha256 = scenarioDefinitionSha,
            traceContractSha256 = traceContractSha,
            eventCount = snapshot.events.size,
            payloadJson = payload,
            payloadSha256 = sha256(payloadBytes)
        )
    }

    /**
     * Parses a persisted payload without trusting an in-memory evidence envelope.
     * Only the exact current canonical schema is admitted; isCanonical then
     * rechecks authored scenario fingerprints, event order, schedule and action.
     */
    fun decodeCanonical(payloadJson: String): DeterministicScenarioTraceEvidence? {
        val payloadBytes = payloadJson.toByteArray(Charsets.UTF_8)
        if (payloadBytes.isEmpty() || payloadBytes.size > MAX_PAYLOAD_BYTES) return null

        val match = payloadPattern.matchEntire(payloadJson) ?: return null
        val groups = match.groupValues
        if (groups[1].toIntOrNull() != SCHEMA_VERSION) return null
        val capturedAtUtcMs = groups[4].toLongOrNull() ?: return null
        val scenario = EncounterScenario.entries.firstOrNull { it.name == groups[5] } ?: return null
        val eventCount = groups[8].toIntOrNull() ?: return null

        val evidence = DeterministicScenarioTraceEvidence(
            candidateCommitSha = groups[2],
            artifactSha256 = groups[3],
            capturedAtUtcMs = capturedAtUtcMs,
            scenario = scenario,
            scenarioDefinitionSha256 = groups[6],
            traceContractSha256 = groups[7],
            eventCount = eventCount,
            payloadJson = payloadJson,
            payloadSha256 = sha256(payloadBytes)
        )
        return evidence.takeIf(::isCanonical)
    }

    /**
     * Revalidates an evidence object at the persistence boundary.
     *
     * This rejects objects whose envelope metadata no longer describes their
     * payload, as well as payloads whose event sequence, authored schedule,
     * action, or lateness arithmetic has been modified and re-hashed.
     */
    fun isCanonical(evidence: DeterministicScenarioTraceEvidence): Boolean {
        if (!commitPattern.matches(evidence.candidateCommitSha) ||
            !sha256Pattern.matches(evidence.artifactSha256) ||
            !sha256Pattern.matches(evidence.scenarioDefinitionSha256) ||
            !sha256Pattern.matches(evidence.traceContractSha256) ||
            !sha256Pattern.matches(evidence.payloadSha256) ||
            evidence.capturedAtUtcMs < 0L ||
            evidence.eventCount < 0
        ) {
            return false
        }

        val expectedDefinitionSha = EncounterScenarioFingerprint.sha256(evidence.scenario)
        val expectedTraceContractSha = EncounterScenarioFingerprint.traceContractSha256(evidence.scenario)
        if (evidence.scenarioDefinitionSha256 != expectedDefinitionSha ||
            evidence.traceContractSha256 != expectedTraceContractSha
        ) {
            return false
        }

        val steps = DebugScenarioScript.stepsFor(evidence.scenario)
        if (steps.isEmpty() || evidence.eventCount != steps.size) return false

        val payloadBytes = evidence.payloadJson.toByteArray(Charsets.UTF_8)
        if (payloadBytes.isEmpty() ||
            payloadBytes.size > MAX_PAYLOAD_BYTES ||
            sha256(payloadBytes) != evidence.payloadSha256
        ) {
            return false
        }

        val prefix = metadataPrefix(
            candidateCommitSha = evidence.candidateCommitSha,
            artifactSha256 = evidence.artifactSha256,
            capturedAtUtcMs = evidence.capturedAtUtcMs,
            scenario = evidence.scenario,
            scenarioDefinitionSha256 = evidence.scenarioDefinitionSha256,
            traceContractSha256 = evidence.traceContractSha256,
            eventCount = evidence.eventCount
        )
        if (!evidence.payloadJson.startsWith(prefix) || !evidence.payloadJson.endsWith("]}")) {
            return false
        }

        val eventText = evidence.payloadJson.substring(prefix.length, evidence.payloadJson.length - 2)
        val matches = eventPattern.findAll(eventText).toList()
        if (matches.size != evidence.eventCount ||
            matches.joinToString(separator = ",") { it.value } != eventText
        ) {
            return false
        }

        return matches.indices.all { index ->
            val groups = matches[index].groupValues
            val sequence = groups[1].toIntOrNull() ?: return@all false
            val scheduledMicros = groups[2].toLongOrNull() ?: return@all false
            val dispatchedMicros = groups[3].toLongOrNull() ?: return@all false
            val latenessMicros = groups[4].toLongOrNull() ?: return@all false
            val expectedScheduledMicros = secondsToMicros(steps[index].atSeconds) ?: return@all false

            sequence == index &&
                scheduledMicros == expectedScheduledMicros &&
                dispatchedMicros >= scheduledMicros &&
                latenessMicros == dispatchedMicros - scheduledMicros &&
                groups[5] == steps[index].action.name
        }
    }

    private fun metadataPrefix(
        candidateCommitSha: String,
        artifactSha256: String,
        capturedAtUtcMs: Long,
        scenario: EncounterScenario,
        scenarioDefinitionSha256: String,
        traceContractSha256: String,
        eventCount: Int
    ): String = buildString(384) {
        append('{')
        append("\"schema_version\":").append(SCHEMA_VERSION)
        append(",\"candidate_commit_sha\":\"").append(candidateCommitSha).append('"')
        append(",\"artifact_sha256\":\"").append(artifactSha256).append('"')
        append(",\"captured_at_utc_ms\":").append(capturedAtUtcMs)
        append(",\"scenario\":\"").append(scenario.name).append('"')
        append(",\"scenario_definition_sha256\":\"")
            .append(scenarioDefinitionSha256)
            .append('"')
        append(",\"trace_contract_sha256\":\"")
            .append(traceContractSha256)
            .append('"')
        append(",\"event_count\":").append(eventCount)
        append(",\"events\":[")
    }

    private fun secondsToMicros(seconds: Float): Long? {
        if (!seconds.isFinite() || seconds < 0f) return null
        val micros = seconds.toDouble() * MICROS_PER_SECOND
        if (!micros.isFinite() || micros > Long.MAX_VALUE.toDouble()) return null
        return micros.roundToLong()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
