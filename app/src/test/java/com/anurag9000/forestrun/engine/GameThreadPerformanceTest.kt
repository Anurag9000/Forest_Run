package com.anurag9000.forestrun.engine

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
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
}
