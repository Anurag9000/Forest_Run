package com.anurag9000.forestrun.engine

/** Metadata plus in-process input/render latency statistics from one hardware run. */
data class InputLatencyReport(
    val scenario: String,
    val durationMs: Long,
    val manufacturer: String,
    val model: String,
    val apiLevel: Int,
    val refreshRateHz: Float,
    val injectedActions: Int,
    val snapshot: InputLatencySnapshot
) {
    init {
        require(scenario.isNotBlank()) { "scenario must not be blank" }
        require(durationMs >= 0L) { "durationMs must be non-negative" }
        require(manufacturer.isNotBlank()) { "manufacturer must not be blank" }
        require(model.isNotBlank()) { "model must not be blank" }
        require(apiLevel >= 0) { "apiLevel must be non-negative" }
        require(refreshRateHz.isFinite() && refreshRateHz > 0f) {
            "refreshRateHz must be finite and positive"
        }
        require(injectedActions >= 0) { "injectedActions must be non-negative" }
        require(snapshot.sampledActions in 0..injectedActions) {
            "sampledActions must not exceed injectedActions"
        }
        require(snapshot.droppedActions >= 0L) { "droppedActions must be non-negative" }
        requireOrdered(snapshot.p50TouchToDecisionNs, snapshot.p95TouchToDecisionNs, snapshot.p99TouchToDecisionNs, "touchToDecision")
        requireOrdered(snapshot.p50DecisionToResponseNs, snapshot.p95DecisionToResponseNs, snapshot.p99DecisionToResponseNs, "decisionToResponse")
        requireOrdered(snapshot.p50ResponseToRenderNs, snapshot.p95ResponseToRenderNs, snapshot.p99ResponseToRenderNs, "responseToRender")
        requireOrdered(snapshot.p50TouchToRenderNs, snapshot.p95TouchToRenderNs, snapshot.p99TouchToRenderNs, "touchToRender")
    }

    fun toJson(): String = buildString(1_024) {
        append("{\n")
        append("  \"schemaVersion\": 1,\n")
        appendJsonString("measurementKind", "app_touch_to_posted_frame", trailingComma = true)
        appendJsonString("scenario", scenario, trailingComma = true)
        append("  \"durationMs\": ").append(durationMs).append(",\n")
        appendJsonString("manufacturer", manufacturer, trailingComma = true)
        appendJsonString("model", model, trailingComma = true)
        append("  \"apiLevel\": ").append(apiLevel).append(",\n")
        append("  \"refreshRateHz\": ").append(refreshRateHz).append(",\n")
        append("  \"injectedActions\": ").append(injectedActions).append(",\n")
        append("  \"sampledActions\": ").append(snapshot.sampledActions).append(",\n")
        append("  \"droppedActions\": ").append(snapshot.droppedActions).append(",\n")
        append("  \"p50TouchToDecisionNs\": ").append(snapshot.p50TouchToDecisionNs).append(",\n")
        append("  \"p95TouchToDecisionNs\": ").append(snapshot.p95TouchToDecisionNs).append(",\n")
        append("  \"p99TouchToDecisionNs\": ").append(snapshot.p99TouchToDecisionNs).append(",\n")
        append("  \"p50DecisionToResponseNs\": ").append(snapshot.p50DecisionToResponseNs).append(",\n")
        append("  \"p95DecisionToResponseNs\": ").append(snapshot.p95DecisionToResponseNs).append(",\n")
        append("  \"p99DecisionToResponseNs\": ").append(snapshot.p99DecisionToResponseNs).append(",\n")
        append("  \"p50ResponseToRenderNs\": ").append(snapshot.p50ResponseToRenderNs).append(",\n")
        append("  \"p95ResponseToRenderNs\": ").append(snapshot.p95ResponseToRenderNs).append(",\n")
        append("  \"p99ResponseToRenderNs\": ").append(snapshot.p99ResponseToRenderNs).append(",\n")
        append("  \"p50TouchToRenderNs\": ").append(snapshot.p50TouchToRenderNs).append(",\n")
        append("  \"p95TouchToRenderNs\": ").append(snapshot.p95TouchToRenderNs).append(",\n")
        append("  \"p99TouchToRenderNs\": ").append(snapshot.p99TouchToRenderNs).append("\n")
        append("}\n")
    }

    private fun requireOrdered(p50: Long, p95: Long, p99: Long, label: String) {
        require(p50 >= 0L) { "$label p50 must be non-negative" }
        require(p95 >= p50) { "$label p95 must not be below p50" }
        require(p99 >= p95) { "$label p99 must not be below p95" }
    }

    private fun StringBuilder.appendJsonString(
        key: String,
        value: String,
        trailingComma: Boolean
    ) {
        append("  ")
        appendQuoted(key)
        append(": ")
        appendQuoted(value)
        if (trailingComma) append(',')
        append('\n')
    }

    private fun StringBuilder.appendQuoted(value: String) {
        append('"')
        for (character in value) {
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u").append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }
}
