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

    /** Even when idle, odd while the single render-thread producer is mutating. */
    @Volatile
    private var publicationSequence = 0L

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
        if (publishedFrameCount == Long.MAX_VALUE) return

        publishMutation {
            val sequence = publishedFrameCount
            val index = (sequence % windowSize).toInt()
            updateSamplesNs[index] = updateNs
            renderSamplesNs[index] = renderNs
            processingSamplesNs[index] = processingNs

            if (processingNs > frameBudgetNs) slowFrameCount++
            if (processingNs > maximumProcessingNs) maximumProcessingNs = processingNs

            publishedFrameCount = sequence + 1L
        }
    }

    /**
     * Clear one profiling session without replacing this monitor object.
     *
     * The producer GameThread retains the monitor reference passed at
     * construction, so instrumentation cannot swap in a new monitor after the
     * Activity starts. Callers must stop that producer before invoking reset.
     */
    fun reset() {
        publishMutation {
            publishedFrameCount = 0L
            updateSamplesNs.fill(0L)
            renderSamplesNs.fill(0L)
            processingSamplesNs.fill(0L)
            slowFrameCount = 0L
            maximumProcessingNs = 0L
        }
    }

    /**
     * Capture a coherent best-effort view of the latest timing window.
     *
     * A primitive sequence lock covers the ring entries and their cumulative
     * slow-frame/maximum counters as one publication. If the render thread
     * advances during a copy, the copy is retried. The bounded fallback may mix
     * adjacent frames but normalizes every public invariant before returning.
     */
    fun snapshot(): FramePerformanceSnapshot {
        var attempt = 0
        while (true) {
            val before = publicationSequence
            if (before and 1L != 0L) {
                Thread.yield()
                continue
            }

            val endSequence = publishedFrameCount
            val capturedSlowFrames = slowFrameCount
            val capturedMaximumProcessingNs = maximumProcessingNs
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

            val after = publicationSequence
            val stable = before == after && after and 1L == 0L
            if (stable || attempt >= MAX_SNAPSHOT_RETRIES) {
                return buildSnapshot(
                    totalFrames = endSequence,
                    slowFrames = capturedSlowFrames,
                    maximumProcessingNs = capturedMaximumProcessingNs,
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
        slowFrames: Long,
        maximumProcessingNs: Long,
        updateSamples: LongArray,
        renderSamples: LongArray,
        processingSamples: LongArray
    ): FramePerformanceSnapshot {
        val sortedProcessing = processingSamples.copyOf().apply { sort() }
        val observedMaximum = sortedProcessing.lastOrNull() ?: 0L
        val usedHeapBytes = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        val maxHeapBytes = runtime.maxMemory().coerceAtLeast(usedHeapBytes)
        return FramePerformanceSnapshot(
            sampledFrames = processingSamples.size,
            totalFrames = totalFrames.coerceAtLeast(processingSamples.size.toLong()),
            slowFrames = slowFrames.coerceIn(0L, totalFrames.coerceAtLeast(0L)),
            frameBudgetNs = frameBudgetNs,
            meanUpdateNs = updateSamples.meanAsLong(),
            meanRenderNs = renderSamples.meanAsLong(),
            meanProcessingNs = processingSamples.meanAsLong(),
            p50ProcessingNs = sortedProcessing.percentile(0.50),
            p95ProcessingNs = sortedProcessing.percentile(0.95),
            p99ProcessingNs = sortedProcessing.percentile(0.99),
            maximumProcessingNs = maxOf(maximumProcessingNs.coerceAtLeast(0L), observedMaximum),
            usedHeapBytes = usedHeapBytes,
            maxHeapBytes = maxHeapBytes
        )
    }

    private inline fun publishMutation(update: () -> Unit) {
        val start = publicationSequence
        publicationSequence = start + 1L
        try {
            update()
        } finally {
            publicationSequence = start + 2L
        }
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
        private const val MAX_SNAPSHOT_RETRIES = 4
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
