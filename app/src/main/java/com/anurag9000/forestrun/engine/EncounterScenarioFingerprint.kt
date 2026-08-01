package com.anurag9000.forestrun.engine

import java.security.MessageDigest
import kotlin.math.roundToLong

/** Stable SHA-256 identities for authored deterministic encounter definitions. */
internal object EncounterScenarioFingerprint {
    private const val FORMAT_VERSION = 1
    private const val MICROS_PER_SECOND = 1_000_000.0
    private const val MICRO_PIXELS_PER_PIXEL = 1_000_000.0

    fun sha256(scenario: EncounterScenario): String =
        sha256(canonicalBytes(scenario))

    fun catalogueSha256(): String {
        val canonical = buildString {
            append("forest-run-encounter-catalogue-v").append(FORMAT_VERSION).append('\n')
            EncounterScenario.entries.forEach { scenario ->
                appendLengthPrefixed(scenario.name)
                appendLengthPrefixed(sha256(scenario))
            }
        }
        return sha256(canonical.toByteArray(Charsets.UTF_8))
    }

    fun canonicalBytes(scenario: EncounterScenario): ByteArray {
        val canonical = buildString(512 + scenario.steps.size * 96) {
            append("forest-run-encounter-scenario-v").append(FORMAT_VERSION).append('\n')
            appendLengthPrefixed(scenario.name)
            appendLengthPrefixed(scenario.title)
            appendLengthPrefixed(scenario.summary)
            append(if (scenario.startsWithBloom) '1' else '0').append('\n')
            append(scenario.steps.size).append('\n')
            scenario.steps.forEachIndexed { index, step ->
                append(index).append('\n')
                append(secondsToMicros(step.atSeconds)).append('\n')
                appendLengthPrefixed(step.type.name)
                append(pixelsToMicroPixels(step.xOffset)).append('\n')
                appendLengthPrefixed(step.variant.name)
            }
        }
        return canonical.toByteArray(Charsets.UTF_8)
    }

    private fun StringBuilder.appendLengthPrefixed(value: String) {
        val byteLength = value.toByteArray(Charsets.UTF_8).size
        append(byteLength).append(':').append(value).append('\n')
    }

    private fun secondsToMicros(value: Float): Long {
        require(value.isFinite() && value >= 0f) {
            "Scenario time must be finite and non-negative."
        }
        return (value.toDouble() * MICROS_PER_SECOND).roundToLong()
    }

    private fun pixelsToMicroPixels(value: Float): Long {
        require(value.isFinite()) { "Scenario offset must be finite." }
        return (value.toDouble() * MICRO_PIXELS_PER_PIXEL).roundToLong()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
