package com.anurag9000.forestrun.engine

/** Snapshot of asynchronous best-run ghost persistence activity. */
data class GhostIoTelemetrySnapshot(
    val writesStarted: Long,
    val writesCompleted: Long,
    val writesFailed: Long,
    val latestFrameCount: Int,
    val maximumFrameCount: Int,
    val latestWriteDurationNs: Long,
    val maximumWriteDurationNs: Long
) {
    companion object {
        val EMPTY = GhostIoTelemetrySnapshot(
            writesStarted = 0L,
            writesCompleted = 0L,
            writesFailed = 0L,
            latestFrameCount = 0,
            maximumFrameCount = 0,
            latestWriteDurationNs = 0L,
            maximumWriteDurationNs = 0L
        )
    }
}

/**
 * Primitive, allocation-free publication of ghost I/O timings.
 *
 * Saves are serialized by GhostPersistenceManager, so one worker publishes
 * completion while the game thread may publish a new start. Values are
 * best-effort profiling evidence and are never consumed by gameplay logic.
 */
object GhostIoTelemetry {
    @Volatile private var writesStarted = 0L
    @Volatile private var writesCompleted = 0L
    @Volatile private var writesFailed = 0L
    @Volatile private var latestFrameCount = 0
    @Volatile private var maximumFrameCount = 0
    @Volatile private var latestWriteDurationNs = 0L
    @Volatile private var maximumWriteDurationNs = 0L

    @Synchronized
    fun recordWriteStarted(frameCount: Int) {
        writesStarted = saturatingIncrement(writesStarted)
        latestFrameCount = frameCount.coerceAtLeast(0)
        if (latestFrameCount > maximumFrameCount) maximumFrameCount = latestFrameCount
    }

    @Synchronized
    fun recordWriteCompleted(durationNs: Long, succeeded: Boolean) {
        writesCompleted = saturatingIncrement(writesCompleted)
        if (!succeeded) writesFailed = saturatingIncrement(writesFailed)
        latestWriteDurationNs = durationNs.coerceAtLeast(0L)
        if (latestWriteDurationNs > maximumWriteDurationNs) {
            maximumWriteDurationNs = latestWriteDurationNs
        }
    }

    @Synchronized
    fun reset() {
        writesStarted = 0L
        writesCompleted = 0L
        writesFailed = 0L
        latestFrameCount = 0
        maximumFrameCount = 0
        latestWriteDurationNs = 0L
        maximumWriteDurationNs = 0L
    }

    fun snapshot(): GhostIoTelemetrySnapshot = GhostIoTelemetrySnapshot(
        writesStarted = writesStarted,
        writesCompleted = writesCompleted,
        writesFailed = writesFailed,
        latestFrameCount = latestFrameCount,
        maximumFrameCount = maximumFrameCount,
        latestWriteDurationNs = latestWriteDurationNs,
        maximumWriteDurationNs = maximumWriteDurationNs
    )

    private fun saturatingIncrement(value: Long): Long =
        if (value == Long.MAX_VALUE) Long.MAX_VALUE else value + 1L
}
