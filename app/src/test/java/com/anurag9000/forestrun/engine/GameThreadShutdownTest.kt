package com.anurag9000.forestrun.engine

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameThreadShutdownTest {
    @After
    fun clearCallerInterrupt() {
        Thread.interrupted()
    }

    @Test
    fun `request stop interrupts a long frame sleep and terminates promptly`() {
        val firstUpdate = CountDownLatch(1)
        val thread = GameThread(
            updateFrame = { firstUpdate.countDown() },
            targetFrameTimeNs = TimeUnit.SECONDS.toNanos(5)
        )
        thread.isRunning = true
        thread.start()
        assertTrue(firstUpdate.await(1, TimeUnit.SECONDS))

        val startedAt = System.nanoTime()
        assertTrue(thread.requestStopAndAwait(timeoutMs = 500L))
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        assertFalse(thread.isAlive)
        assertFalse(thread.isRunning)
        assertTrue("shutdown took ${elapsedMs}ms", elapsedMs < 1_000L)
    }

    @Test
    fun `caller interruption is restored after the thread terminates`() {
        val firstUpdate = CountDownLatch(1)
        val thread = GameThread(
            updateFrame = { firstUpdate.countDown() },
            targetFrameTimeNs = TimeUnit.SECONDS.toNanos(5)
        )
        thread.isRunning = true
        thread.start()
        assertTrue(firstUpdate.await(1, TimeUnit.SECONDS))

        Thread.currentThread().interrupt()
        assertTrue(thread.requestStopAndAwait(timeoutMs = 500L))
        assertTrue(Thread.currentThread().isInterrupted)
        assertFalse(thread.isAlive)
    }

    @Test
    fun `uncooperative update cannot make shutdown wait past its bound`() {
        val updateEntered = CountDownLatch(1)
        val releaseUpdate = AtomicBoolean(false)
        val renders = AtomicInteger(0)
        val thread = GameThread(
            updateFrame = {
                updateEntered.countDown()
                while (!releaseUpdate.get()) Thread.yield()
            },
            renderFrame = { renders.incrementAndGet() },
            targetFrameTimeNs = TimeUnit.SECONDS.toNanos(5)
        )
        thread.isRunning = true
        thread.start()
        assertTrue(updateEntered.await(1, TimeUnit.SECONDS))

        val startedAt = System.nanoTime()
        assertFalse(thread.requestStopAndAwait(timeoutMs = 40L))
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        assertTrue(thread.isAlive)
        assertTrue("bounded wait took ${elapsedMs}ms", elapsedMs < 500L)

        releaseUpdate.set(true)
        assertTrue(thread.requestStopAndAwait(timeoutMs = 1_000L))
        assertFalse(thread.isAlive)
        assertTrue("a stale frame rendered after stop", renders.get() == 0)
    }

    @Test
    fun `stop before start is already terminated and remains safe`() {
        val thread = GameThread(updateFrame = {})
        assertTrue(thread.requestStopAndAwait(timeoutMs = 0L))
        assertFalse(thread.isAlive)
        assertFalse(thread.isRunning)
    }
}
