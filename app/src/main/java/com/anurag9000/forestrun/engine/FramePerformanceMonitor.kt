package com.anurag9000.forestrun.engine

import kotlin.math.ceil
import kotlin.math.min

/**
 * Allocation-free per-frame timing recorder for the render thread.
 *
 * [record] only writes primitive values into fixed-size ring buffers. Expensive
 * copying, sorting, percentile calculation, and heap sampling happen solely in
 * [snapshot], which is intended for debug overlays, instrumentation, and manual
 * profiling checkpoints rather than the frame hot path.
 */
class FramePerformanceMonitor(
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
    val frameBudgetNs: Long = DEFAULT_FRAME_BUDGET_NS,
    private val runtime: Runtime = Runtime.getRuntime()
) {
    init {
        require(windowSize > 0) { "windowSize must be positive" }
        require(frameBudgetNs > 0L) { "frameBudgetNs must be positive" }
    }

    private val updateSamplesNs = LongArray(windowSize)
    private val renderSamplesNs = LongArray(windowSize)
    private val processingSamplesNs = LongArray(windowSize)

    /** Published only after the corresponding ring-buffer entry is complete. */
    @Volatile
    private var publishedFrameCount = 0L

    private var slowFrameCount = 0L
    private var maximumProcessingNs = 0L

    /**
     * Record one completed frame without allocating. Invalid negative timings
     * are rejected instead of corrupting the profiling window.
     */
    fun record(updateNs: Long, renderNs: Long, processingNs: Long) {
        if (updateNs < 0L || renderNs < 0L || processingNs < 0L) return

        val sequence = publishedFrameCount
        val index = (sequence % windowSize).toInt()
        updateSamplesNs[index] = updateNs
        renderSamplesNs[index] = renderNs
        processingSamplesNs[index] = processingNs

        if (processingNs > frameBudgetNs) slowFrameCount++
        if (processingNs > maximumProcessingNs) maximumProcessingNs = processingNs

        // Volatile publication makes the completed primitive writes visible to
        // a snapshot reader without locking every frame.
        publishedFrameCount = sequence + 1L
    }

    /**
     * Clear one profiling session without replacing this monitor object.
     *
     * The producer GameThread retains the monitor reference passed at
     * construction, so instrumentation cannot swap in a new monitor after the
     * Activity starts. Callers must stop that producer before invoking reset.
     */
    fun reset() {
        publishedFrameCount = 0L
        updateSamplesNs.fill(0L)
        renderSamplesNs.fill(0L)
        processingSamplesNs.fill(0L)
        slowFrameCount = 0L
        maximumProcessingNs = 0L
    }

    /**
     * Capture a coherent best-effort view of the latest timing window.
     *
     * If the render thread advances while the arrays are copied, the copy is
     * retried. The final fallback remains safe and may only omit the newest
     * frame; it can never expose partially initialized object state because the
     * hot path stores primitives only.
     */
    fun snapshot(): FramePerformanceSnapshot {
        var attempt = 0
        while (true) {
            val endSequence = publishedFrameCount
            val sampleCount = min(endSequence, windowSize.toLong()).toInt()
            val startSequence = endSequence - sampleCount
            val updateCopy = LongArray(sampleCount)
            val renderCopy = LongArray(sampleCount)
            val processingCopy = LongArray(sampleCount)

            for (offset in 0 until sampleCount) {
                val sequence = startSequence + offset
                val index = (sequence % windowSize).toInt()
                updateCopy[offset] = updateSamplesNs[index]
                renderCopy[offset] = renderSamplesNs[index]
                processingCopy[offset] = processingSamplesNs[index]
            }

            val confirmedEndSequence = publishedFrameCount
            if (confirmedEndSequence == endSequence || attempt >= MAX_SNAPSHOT_RETRIES) {
                return buildSnapshot(
                    totalFrames = endSequence,
                    updateSamples = updateCopy,
                    renderSamples = renderCopy,
                    processingSamples = processingCopy
                )
            }
            attempt++
        }
    }

    private fun buildSnapshot(
        totalFrames: Long,
        updateSamples: LongArray,
        renderSamples: LongArray,
        processingSamples: LongArray
    ): FramePerformanceSnapshot {
        val sortedProcessing = processingSamples.copyOf().apply { sort() }
        val usedHeapBytes = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        return FramePerformanceSnapshot(
            sampledFrames = processingSamples.size,
            totalFrames = totalFrames,
            slowFrames = slowFrameCount,
            frameBudgetNs = frameBudgetNs,
            meanUpdateNs = updateSamples.meanAsLong(),
            meanRenderNs = renderSamples.meanAsLong(),
            meanProcessingNs = processingSamples.meanAsLong(),
            p50ProcessingNs = sortedProcessing.percentile(0.50),
            p95ProcessingNs = sortedProcessing.percentile(0.95),
            p99ProcessingNs = sortedProcessing.percentile(0.99),
            maximumProcessingNs = maximumProcessingNs,
            usedHeapBytes = usedHeapBytes,
            maxHeapBytes = runtime.maxMemory().coerceAtLeast(0L)
        )
    }

    private fun LongArray.meanAsLong(): Long {
        if (isEmpty()) return 0L
        var sum = 0L
        for (value in this) sum = saturatingAdd(sum, value)
        return sum / size
    }

    private fun LongArray.percentile(fraction: Double): Long {
        if (isEmpty()) return 0L
        val rank = ceil(fraction.coerceIn(0.0, 1.0) * size).toInt().coerceAtLeast(1)
        return this[(rank - 1).coerceIn(indices)]
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

    companion object {
        const val DEFAULT_WINDOW_SIZE = 600
        const val DEFAULT_FRAME_BUDGET_NS = 1_000_000_000L / 60L
        private const val MAX_SNAPSHOT_RETRIES = 2
    }
}

/** Snapshot produced outside the frame hot path. */
data class FramePerformanceSnapshot(
    val sampledFrames: Int,
    val totalFrames: Long,
    val slowFrames: Long,
    val frameBudgetNs: Long,
    val meanUpdateNs: Long,
    val meanRenderNs: Long,
    val meanProcessingNs: Long,
    val p50ProcessingNs: Long,
    val p95ProcessingNs: Long,
    val p99ProcessingNs: Long,
    val maximumProcessingNs: Long,
    val usedHeapBytes: Long,
    val maxHeapBytes: Long
) {
    val slowFrameRatio: Double
        get() = if (totalFrames == 0L) 0.0 else slowFrames.toDouble() / totalFrames.toDouble()
}
