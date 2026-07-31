package com.anurag9000.forestrun.engine

import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FramePerformanceMonitorTest {
    @Test
    fun `empty snapshot reports zero timings`() {
        val snapshot = FramePerformanceMonitor(windowSize = 4, frameBudgetNs = 16L).snapshot()

        assertEquals(0, snapshot.sampledFrames)
        assertEquals(0L, snapshot.totalFrames)
        assertEquals(0L, snapshot.slowFrames)
        assertEquals(0L, snapshot.meanProcessingNs)
        assertEquals(0L, snapshot.p99ProcessingNs)
        assertEquals(0.0, snapshot.slowFrameRatio, 0.0)
        assertTrue(snapshot.usedHeapBytes >= 0L)
        assertTrue(snapshot.maxHeapBytes >= snapshot.usedHeapBytes)
    }

    @Test
    fun `snapshot reports means percentiles maximum and slow frames`() {
        val monitor = FramePerformanceMonitor(windowSize = 8, frameBudgetNs = 16L)
        monitor.record(updateNs = 4L, renderNs = 4L, processingNs = 8L)
        monitor.record(updateNs = 5L, renderNs = 5L, processingNs = 10L)
        monitor.record(updateNs = 8L, renderNs = 10L, processingNs = 18L)
        monitor.record(updateNs = 10L, renderNs = 20L, processingNs = 30L)

        val snapshot = monitor.snapshot()

        assertEquals(4, snapshot.sampledFrames)
        assertEquals(4L, snapshot.totalFrames)
        assertEquals(2L, snapshot.slowFrames)
        assertEquals(6L, snapshot.meanUpdateNs)
        assertEquals(9L, snapshot.meanRenderNs)
        assertEquals(16L, snapshot.meanProcessingNs)
        assertEquals(10L, snapshot.p50ProcessingNs)
        assertEquals(30L, snapshot.p95ProcessingNs)
        assertEquals(30L, snapshot.p99ProcessingNs)
        assertEquals(30L, snapshot.maximumProcessingNs)
        assertEquals(0.5, snapshot.slowFrameRatio, 0.0)
    }

    @Test
    fun `concurrent snapshots satisfy the report evidence contract`() {
        val monitor = FramePerformanceMonitor(windowSize = 64, frameBudgetNs = 16L)
        val failure = AtomicReference<Throwable?>(null)
        val iterations = 8_000

        val writer = Thread {
            repeat(iterations) { index ->
                val processing = (index % 40 + 1).toLong()
                monitor.record(
                    updateNs = processing / 3L,
                    renderNs = processing / 2L,
                    processingNs = processing
                )
            }
        }
        val reader = Thread {
            repeat(iterations / 4) {
                if (failure.get() == null) {
                    runCatching {
                        val snapshot = monitor.snapshot()
                        assertTrue(snapshot.sampledFrames.toLong() <= snapshot.totalFrames)
                        assertTrue(snapshot.slowFrames in 0L..snapshot.totalFrames)
                        assertTrue(snapshot.p50ProcessingNs <= snapshot.p95ProcessingNs)
                        assertTrue(snapshot.p95ProcessingNs <= snapshot.p99ProcessingNs)
                        assertTrue(snapshot.p99ProcessingNs <= snapshot.maximumProcessingNs)
                        assertTrue(snapshot.meanProcessingNs <= snapshot.maximumProcessingNs)
                        assertTrue(snapshot.usedHeapBytes <= snapshot.maxHeapBytes)
                        FramePerformanceReport(
                            scenario = "concurrent",
                            durationMs = 1L,
                            manufacturer = "test",
                            model = "test",
                            apiLevel = 35,
                            refreshRateHz = 60f,
                            snapshot = snapshot
                        )
                    }.onFailure { error ->
                        failure.compareAndSet(null, error)
                    }
                }
            }
        }

        writer.start()
        reader.start()
        writer.join()
        reader.join()

        assertNull(failure.get())
        assertEquals(iterations.toLong(), monitor.snapshot().totalFrames)
    }

    @Test
    fun `ring window retains only the newest samples while totals remain cumulative`() {
        val monitor = FramePerformanceMonitor(windowSize = 3, frameBudgetNs = 100L)
        monitor.record(1L, 1L, 10L)
        monitor.record(1L, 1L, 20L)
        monitor.record(1L, 1L, 30L)
        monitor.record(1L, 1L, 40L)
        monitor.record(1L, 1L, 50L)

        val snapshot = monitor.snapshot()

        assertEquals(3, snapshot.sampledFrames)
        assertEquals(5L, snapshot.totalFrames)
        assertEquals(40L, snapshot.meanProcessingNs)
        assertEquals(40L, snapshot.p50ProcessingNs)
        assertEquals(50L, snapshot.p95ProcessingNs)
        assertEquals(50L, snapshot.maximumProcessingNs)
    }

    @Test
    fun `negative timing input is rejected atomically`() {
        val monitor = FramePerformanceMonitor(windowSize = 3, frameBudgetNs = 16L)
        monitor.record(updateNs = -1L, renderNs = 2L, processingNs = 3L)
        monitor.record(updateNs = 1L, renderNs = -2L, processingNs = 3L)
        monitor.record(updateNs = 1L, renderNs = 2L, processingNs = -3L)

        val snapshot = monitor.snapshot()

        assertEquals(0L, snapshot.totalFrames)
        assertEquals(0, snapshot.sampledFrames)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero-sized timing window is rejected`() {
        FramePerformanceMonitor(windowSize = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-positive frame budget is rejected`() {
        FramePerformanceMonitor(frameBudgetNs = 0L)
    }
}
