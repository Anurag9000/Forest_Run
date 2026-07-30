package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FramePerformanceResetTest {
    @Test
    fun `reset removes warmup samples while retaining monitor configuration`() {
        val monitor = FramePerformanceMonitor(windowSize = 4, frameBudgetNs = 10L)
        monitor.record(updateNs = 2L, renderNs = 3L, processingNs = 12L)
        monitor.record(updateNs = 1L, renderNs = 1L, processingNs = 4L)
        assertEquals(2L, monitor.snapshot().totalFrames)

        monitor.reset()
        val cleared = monitor.snapshot()

        assertEquals(0L, cleared.totalFrames)
        assertEquals(0, cleared.sampledFrames)
        assertEquals(0L, cleared.slowFrames)
        assertEquals(0L, cleared.maximumProcessingNs)
        assertEquals(10L, cleared.frameBudgetNs)

        monitor.record(updateNs = 1L, renderNs = 2L, processingNs = 5L)
        val measured = monitor.snapshot()
        assertEquals(1L, measured.totalFrames)
        assertEquals(1, measured.sampledFrames)
        assertEquals(5L, measured.p95ProcessingNs)
        assertTrue(measured.slowFrameRatio == 0.0)
    }
}
