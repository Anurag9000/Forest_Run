package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class GhostIoTelemetryTest {
    @Test
    fun `ghost write telemetry tracks counts frames failures and latency`() {
        GhostIoTelemetry.reset()

        GhostIoTelemetry.recordWriteStarted(frameCount = 120)
        GhostIoTelemetry.recordWriteCompleted(durationNs = 4_000L, succeeded = true)
        GhostIoTelemetry.recordWriteStarted(frameCount = 360)
        GhostIoTelemetry.recordWriteCompleted(durationNs = 9_000L, succeeded = false)

        val snapshot = GhostIoTelemetry.snapshot()
        assertEquals(2L, snapshot.writesStarted)
        assertEquals(2L, snapshot.writesCompleted)
        assertEquals(1L, snapshot.writesFailed)
        assertEquals(360, snapshot.latestFrameCount)
        assertEquals(360, snapshot.maximumFrameCount)
        assertEquals(9_000L, snapshot.latestWriteDurationNs)
        assertEquals(9_000L, snapshot.maximumWriteDurationNs)
    }

    @Test
    fun `reset isolates profiling sessions`() {
        GhostIoTelemetry.recordWriteStarted(frameCount = 12)
        GhostIoTelemetry.recordWriteCompleted(durationNs = 5L, succeeded = false)

        GhostIoTelemetry.reset()

        assertEquals(GhostIoTelemetrySnapshot.EMPTY, GhostIoTelemetry.snapshot())
    }
}
