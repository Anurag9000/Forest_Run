package com.anurag9000.forestrun.engine

/** Metadata accompanying a physical-device frame telemetry capture. */
data class FramePerformanceReport(
    val scenario: String,
    val durationMs: Long,
    val manufacturer: String,
    val model: String,
    val apiLevel: Int,
    val refreshRateHz: Float,
    val snapshot: FramePerformanceSnapshot,
    val workload: RuntimeWorkloadSnapshot = RuntimeWorkloadSnapshot.EMPTY,
    val ghostIo: GhostIoTelemetrySnapshot = GhostIoTelemetrySnapshot.EMPTY
) {
    init {
        require(scenario.isNotBlank()) { "scenario must not be blank" }
        require(durationMs >= 0L) { "durationMs must be non-negative" }
        require(apiLevel >= 0) { "apiLevel must be non-negative" }
        require(refreshRateHz.isFinite() && refreshRateHz >= 0f) {
            "refreshRateHz must be finite and non-negative"
        }
    }

    /** Dependency-free deterministic JSON for adb/CI artifact collection. */
    fun toJson(): String = buildString(1_024) {
        append("{\n")
        appendJsonString("scenario", scenario, trailingComma = true)
        append("  \"durationMs\": ").append(durationMs).append(",\n")
        appendJsonString("manufacturer", manufacturer, trailingComma = true)
        appendJsonString("model", model, trailingComma = true)
        append("  \"apiLevel\": ").append(apiLevel).append(",\n")
        append("  \"refreshRateHz\": ").append(refreshRateHz).append(",\n")
        append("  \"sampledFrames\": ").append(snapshot.sampledFrames).append(",\n")
        append("  \"totalFrames\": ").append(snapshot.totalFrames).append(",\n")
        append("  \"slowFrames\": ").append(snapshot.slowFrames).append(",\n")
        append("  \"slowFrameRatio\": ").append(snapshot.slowFrameRatio).append(",\n")
        append("  \"frameBudgetNs\": ").append(snapshot.frameBudgetNs).append(",\n")
        append("  \"meanUpdateNs\": ").append(snapshot.meanUpdateNs).append(",\n")
        append("  \"meanRenderNs\": ").append(snapshot.meanRenderNs).append(",\n")
        append("  \"meanProcessingNs\": ").append(snapshot.meanProcessingNs).append(",\n")
        append("  \"p50ProcessingNs\": ").append(snapshot.p50ProcessingNs).append(",\n")
        append("  \"p95ProcessingNs\": ").append(snapshot.p95ProcessingNs).append(",\n")
        append("  \"p99ProcessingNs\": ").append(snapshot.p99ProcessingNs).append(",\n")
        append("  \"maximumProcessingNs\": ").append(snapshot.maximumProcessingNs).append(",\n")
        append("  \"usedHeapBytes\": ").append(snapshot.usedHeapBytes).append(",\n")
        append("  \"maxHeapBytes\": ").append(snapshot.maxHeapBytes).append(",\n")
        append("  \"currentEntities\": ").append(workload.currentEntities).append(",\n")
        append("  \"peakEntities\": ").append(workload.peakEntities).append(",\n")
        append("  \"currentSeedOrbs\": ").append(workload.currentSeedOrbs).append(",\n")
        append("  \"peakSeedOrbs\": ").append(workload.peakSeedOrbs).append(",\n")
        append("  \"currentParticles\": ").append(workload.currentParticles).append(",\n")
        append("  \"peakParticles\": ").append(workload.peakParticles).append(",\n")
        append("  \"currentDialogueBubbles\": ").append(workload.currentDialogueBubbles).append(",\n")
        append("  \"peakDialogueBubbles\": ").append(workload.peakDialogueBubbles).append(",\n")
        append("  \"currentFlavorTexts\": ").append(workload.currentFlavorTexts).append(",\n")
        append("  \"peakFlavorTexts\": ").append(workload.peakFlavorTexts).append(",\n")
        append("  \"ghostWritesStarted\": ").append(ghostIo.writesStarted).append(",\n")
        append("  \"ghostWritesCompleted\": ").append(ghostIo.writesCompleted).append(",\n")
        append("  \"ghostWritesFailed\": ").append(ghostIo.writesFailed).append(",\n")
        append("  \"latestGhostFrameCount\": ").append(ghostIo.latestFrameCount).append(",\n")
        append("  \"maximumGhostFrameCount\": ").append(ghostIo.maximumFrameCount).append(",\n")
        append("  \"latestGhostWriteDurationNs\": ").append(ghostIo.latestWriteDurationNs).append(",\n")
        append("  \"maximumGhostWriteDurationNs\": ").append(ghostIo.maximumWriteDurationNs).append("\n")
        append("}\n")
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
