package com.anurag9000.forestrun.engine

import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `concurrent snapshots preserve publication invariants`() {
        GhostIoTelemetry.reset()
        val failure = AtomicReference<Throwable?>(null)
        val iterations = 10_000

        val writer = Thread {
            repeat(iterations) { index ->
                GhostIoTelemetry.recordWriteStarted(frameCount = index)
                GhostIoTelemetry.recordWriteCompleted(
                    durationNs = index.toLong(),
                    succeeded = index % 5 != 0
                )
            }
        }
        val reader = Thread {
            repeat(iterations) {
                if (failure.get() != null) return@Thread
                val snapshot = GhostIoTelemetry.snapshot()
                runCatching {
                    assertTrue(snapshot.writesCompleted <= snapshot.writesStarted)
                    assertTrue(snapshot.writesFailed <= snapshot.writesCompleted)
                    assertTrue(snapshot.latestFrameCount <= snapshot.maximumFrameCount)
                    assertTrue(snapshot.latestWriteDurationNs <= snapshot.maximumWriteDurationNs)
                }.onFailure { error ->
                    failure.compareAndSet(null, error)
                }
            }
        }

        writer.start()
        reader.start()
        writer.join()
        reader.join()

        assertNull(failure.get())
        val snapshot = GhostIoTelemetry.snapshot()
        assertEquals(iterations.toLong(), snapshot.writesStarted)
        assertEquals(iterations.toLong(), snapshot.writesCompleted)
        assertEquals((iterations / 5).toLong(), snapshot.writesFailed)
    }

    @Test
    fun `reset isolates profiling sessions`() {
        GhostIoTelemetry.recordWriteStarted(frameCount = 12)
        GhostIoTelemetry.recordWriteCompleted(durationNs = 5L, succeeded = false)

        GhostIoTelemetry.reset()

        assertEquals(GhostIoTelemetrySnapshot.EMPTY, GhostIoTelemetry.snapshot())
    }
}
