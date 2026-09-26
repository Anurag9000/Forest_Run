package com.anurag9000.forestrun.engine

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameThreadPerformanceTest {
    @Test
    fun `completed frame records update render and total processing time`() {
        val rendered = CountDownLatch(1)
        val monitor = FramePerformanceMonitor(windowSize = 8, frameBudgetNs = Long.MAX_VALUE)
        lateinit var thread: GameThread
        thread = GameThread(
            updateFrame = {},
            renderFrame = {
                rendered.countDown()
                thread.requestStop()
            },
            targetFrameTimeNs = 0L,
            performanceMonitor = monitor
        )

        thread.isRunning = true
        thread.start()
        assertTrue(rendered.await(1, TimeUnit.SECONDS))
        assertTrue(thread.requestStopAndAwait(timeoutMs = 1_000L))

        val snapshot = monitor.snapshot()
        assertEquals(1L, snapshot.totalFrames)
        assertEquals(1, snapshot.sampledFrames)
        assertTrue(snapshot.meanUpdateNs >= 0L)
        assertTrue(snapshot.meanRenderNs >= 0L)
        assertTrue(snapshot.meanProcessingNs >= snapshot.meanUpdateNs)
        assertTrue(snapshot.meanProcessingNs >= snapshot.meanRenderNs)
    }

    @Test
    fun `frame stopped during update is recorded without rendering`() {
        val updated = CountDownLatch(1)
        val monitor = FramePerformanceMonitor(windowSize = 8, frameBudgetNs = Long.MAX_VALUE)
        lateinit var thread: GameThread
        thread = GameThread(
            updateFrame = {
                updated.countDown()
                thread.requestStop()
            },
            renderFrame = { error("render must not run after update requests stop") },
            targetFrameTimeNs = 0L,
            performanceMonitor = monitor
        )

        thread.isRunning = true
        thread.start()
        assertTrue(updated.await(1, TimeUnit.SECONDS))
        assertTrue(thread.requestStopAndAwait(timeoutMs = 1_000L))

        val snapshot = monitor.snapshot()
        assertEquals(1L, snapshot.totalFrames)
        assertEquals(0L, snapshot.meanRenderNs)
        assertTrue(snapshot.meanProcessingNs >= snapshot.meanUpdateNs)
    }

    @Test
    fun `non surface render callback cannot complete pending input latency`() {
        InputLatencyTelemetryRegistry.reset()
        try {
            val touchedAtNs = System.nanoTime().coerceAtLeast(1L)
            InputLatencyTelemetryRegistry.recordTouchReceived(touchedAtNs)
            InputLatencyTelemetryRegistry.recordGestureDecision(
                InputGestureKind.JUMP, touchedAtNs + 1L
            )
            InputLatencyTelemetryRegistry.recordGameplayResponse(touchedAtNs + 2L)

            val rendered = CountDownLatch(1)
            lateinit var thread: GameThread
            thread = GameThread(
                updateFrame = {},
                renderFrame = {
                    rendered.countDown()
                    thread.requestStop()
                },
                targetFrameTimeNs = 0L
            )
            thread.isRunning = true
            thread.start()
            assertTrue(rendered.await(1, TimeUnit.SECONDS))
            assertTrue(thread.requestStopAndAwait(timeoutMs = 1_000L))
            assertNull(thread.lastFailure)

            // No Canvas was ever posted by this synthetic callback.
            assertEquals(0, InputLatencyTelemetryRegistry.snapshot().sampledActions)
            // Pending input must remain eligible for a genuine post signal.
            InputLatencyTelemetryRegistry.recordFrameRendered(touchedAtNs + 3L)
            assertEquals(1, InputLatencyTelemetryRegistry.snapshot().sampledActions)
        } finally {
            InputLatencyTelemetryRegistry.reset()
        }
    }

}
