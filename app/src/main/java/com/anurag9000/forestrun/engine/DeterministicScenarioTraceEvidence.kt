package com.anurag9000.forestrun.engine

import java.security.MessageDigest

internal data class DeterministicScenarioTraceEvidence(
    val candidateCommitSha: String,
    val artifactSha256: String,
    val capturedAtUtcMs: Long,
    val scenario: EncounterScenario,
    val eventCount: Int,
    val payloadJson: String,
    val payloadSha256: String
)

/** Stable, bounded, privacy-preserving encoding for deterministic input traces. */
internal object DeterministicScenarioTraceEvidenceCodec {
    private const val SCHEMA_VERSION = 1
    private const val MAX_PAYLOAD_BYTES = 256 * 1024
    private val commitPattern = Regex("^[0-9a-f]{40}$")
    private val sha256Pattern = Regex("^[0-9a-f]{64}$")

    fun encode(
        snapshot: DeterministicScenarioTraceSnapshot,
        candidateCommitSha: String,
        artifactSha256: String,
        capturedAtUtcMs: Long
    ): DeterministicScenarioTraceEvidence? {
        val commit = candidateCommitSha.trim().lowercase()
        val artifact = artifactSha256.trim().lowercase()
        if (!snapshot.isReplayable ||
            !commitPattern.matches(commit) ||
            !sha256Pattern.matches(artifact) ||
            capturedAtUtcMs < 0L
        ) {
            return null
        }
        val scenario = snapshot.scenario ?: return null

        val payload = buildString(256 + snapshot.events.size * 128) {
            append('{')
            append("\"schema_version\":").append(SCHEMA_VERSION)
            append(",\"candidate_commit_sha\":\"").append(commit).append('"')
            append(",\"artifact_sha256\":\"").append(artifact).append('"')
            append(",\"captured_at_utc_ms\":").append(capturedAtUtcMs)
            append(",\"scenario\":\"").append(scenario.name).append('"')
            append(",\"event_count\":").append(snapshot.events.size)
            append(",\"events\":[")
            snapshot.events.forEachIndexed { index, event ->
                if (index > 0) append(',')
                append('{')
                append("\"sequence\":").append(event.sequence)
                append(",\"scheduled_at_seconds\":").append(event.scheduledAtSeconds)
                append(",\"dispatched_at_seconds\":").append(event.dispatchedAtSeconds)
                append(",\"lateness_seconds\":").append(event.latenessSeconds)
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
            eventCount = snapshot.events.size,
            payloadJson = payload,
            payloadSha256 = sha256(payloadBytes)
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
