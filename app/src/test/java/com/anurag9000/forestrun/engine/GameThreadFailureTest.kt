package com.anurag9000.forestrun.engine

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GameThreadFailureTest {
    @Test
    fun `update exception is captured reported once and stops before render`() {
        val expected = IllegalStateException("update failed")
        val reported = AtomicReference<GameThread.FrameFailure>()
        val renderCalls = AtomicInteger(0)
        val thread = GameThread(
            updateFrame = { throw expected },
            renderFrame = { renderCalls.incrementAndGet() },
            targetFrameTimeNs = 0L,
            failureHandler = { reported.set(it) }
        )

        thread.isRunning = true
        thread.start()
        thread.join(TimeUnit.SECONDS.toMillis(1))

        assertFalse(thread.isAlive)
        assertFalse(thread.isRunning)
        assertEquals(0, renderCalls.get())
        val failure = requireNotNull(thread.lastFailure)
        assertEquals(GameThread.FrameStage.UPDATE, failure.stage)
        assertSame(expected, failure.cause)
        assertSame(failure, reported.get())
    }

    @Test
    fun `render exception is captured after the completed update`() {
        val expected = IllegalArgumentException("render failed")
        val reported = AtomicReference<GameThread.FrameFailure>()
        val updateCalls = AtomicInteger(0)
        val thread = GameThread(
            updateFrame = { updateCalls.incrementAndGet() },
            renderFrame = { throw expected },
            targetFrameTimeNs = 0L,
            failureHandler = { reported.set(it) }
        )

        thread.isRunning = true
        thread.start()
        thread.join(TimeUnit.SECONDS.toMillis(1))

        assertFalse(thread.isAlive)
        assertFalse(thread.isRunning)
        assertEquals(1, updateCalls.get())
        val failure = requireNotNull(thread.lastFailure)
        assertEquals(GameThread.FrameStage.RENDER, failure.stage)
        assertSame(expected, failure.cause)
        assertSame(failure, reported.get())
    }

    @Test
    fun `failure handler exception is suppressed without escaping the thread`() {
        val frameFailure = IllegalStateException("frame failed")
        val handlerFailure = IllegalArgumentException("handler failed")
        val thread = GameThread(
            updateFrame = { throw frameFailure },
            targetFrameTimeNs = 0L,
            failureHandler = { throw handlerFailure }
        )

        thread.isRunning = true
        thread.start()
        thread.join(TimeUnit.SECONDS.toMillis(1))

        assertFalse(thread.isAlive)
        assertFalse(thread.isRunning)
        assertEquals(1, frameFailure.suppressed.size)
        assertSame(handlerFailure, frameFailure.suppressed.single())
        assertTrue(thread.lastFailure?.cause === frameFailure)
    }
}
