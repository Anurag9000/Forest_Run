#!/usr/bin/env python3
"""Make the production GameThread shutdown boundary directly JVM-testable."""

from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def write(path: str, content: str) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def main() -> None:
    write(
        "app/src/main/java/com/anurag9000/forestrun/engine/GameThread.kt",
        '''package com.anurag9000.forestrun.engine

import android.graphics.Canvas
import android.view.SurfaceHolder
import java.util.concurrent.TimeUnit

/** Dedicated 60 Hz background thread for update and Canvas rendering. */
class GameThread private constructor(
    private val updateFrame: (Float) -> Unit,
    private val renderFrame: () -> Unit,
    private val targetFrameTimeNs: Long
) : Thread("GameThread") {
    constructor(surfaceHolder: SurfaceHolder, gameView: GameView) : this(
        updateFrame = { deltaTime -> gameView.update(deltaTime) },
        renderFrame = { renderSurfaceFrame(surfaceHolder, gameView) },
        targetFrameTimeNs = DEFAULT_TARGET_FRAME_TIME_NS
    )

    /** Test seam that still executes the production timing, sleep, and stop loop. */
    internal constructor(
        updateFrame: (Float) -> Unit,
        renderFrame: () -> Unit = {},
        targetFrameTimeNs: Long = DEFAULT_TARGET_FRAME_TIME_NS
    ) : this(
        updateFrame = updateFrame,
        renderFrame = renderFrame,
        targetFrameTimeNs = targetFrameTimeNs.coerceAtLeast(0L)
    )

    @Volatile
    private var running: Boolean = false

    var isRunning: Boolean
        get() = running
        set(value) {
            running = value
            if (!value) interrupt()
        }

    fun requestStop() {
        isRunning = false
    }

    /**
     * Requests cancellation and waits no longer than [timeoutMs]. If the caller
     * is interrupted while joining, shutdown continues and the interrupt flag is
     * restored before returning.
     */
    internal fun requestStopAndAwait(timeoutMs: Long = DEFAULT_STOP_TIMEOUT_MS): Boolean {
        requestStop()
        if (!isAlive) return true
        if (Thread.currentThread() === this) return false

        val timeoutNs = TimeUnit.MILLISECONDS.toNanos(timeoutMs.coerceAtLeast(0L))
        val startedAtNs = System.nanoTime()
        var callerWasInterrupted = false

        while (isAlive) {
            val elapsedNs = System.nanoTime() - startedAtNs
            val remainingNs = timeoutNs - elapsedNs
            if (remainingNs <= 0L) break

            val waitMs = (remainingNs / 1_000_000L).coerceIn(1L, MAX_JOIN_SLICE_MS)
            try {
                join(waitMs)
            } catch (_: InterruptedException) {
                callerWasInterrupted = true
                requestStop()
            }
        }

        if (callerWasInterrupted) Thread.currentThread().interrupt()
        return !isAlive
    }

    override fun run() {
        var lastTimeNs = System.nanoTime()
        try {
            while (running) {
                val nowNs = System.nanoTime()
                val deltaTime = ((nowNs - lastTimeNs) / 1_000_000_000.0).toFloat()
                    .coerceIn(0f, MAX_DELTA_SECONDS)
                lastTimeNs = nowNs

                updateFrame(deltaTime)
                if (!running) break

                renderFrame()
                if (!running) break

                val elapsedNs = System.nanoTime() - nowNs
                val sleepNs = targetFrameTimeNs - elapsedNs
                if (sleepNs > 0L) {
                    try {
                        sleep(
                            sleepNs / 1_000_000L,
                            (sleepNs % 1_000_000L).toInt()
                        )
                    } catch (_: InterruptedException) {
                        // requestStop interrupts a long frame sleep immediately.
                        return
                    }
                }
            }
        } finally {
            running = false
        }
    }

    companion object {
        private const val DEFAULT_TARGET_FRAME_TIME_NS = 1_000_000_000L / 60L
        private const val DEFAULT_STOP_TIMEOUT_MS = 1_000L
        private const val MAX_JOIN_SLICE_MS = 250L
        private const val MAX_DELTA_SECONDS = 0.05f

        private fun renderSurfaceFrame(surfaceHolder: SurfaceHolder, gameView: GameView) {
            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas()
                if (canvas != null) {
                    synchronized(surfaceHolder) {
                        gameView.draw(canvas)
                    }
                }
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) {
                        // The Surface may disappear while the frame is held.
                    }
                }
            }
        }
    }
}
'''
    )

    game_view = Path("app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt")
    replace_once(
        game_view,
        '''    private fun stopThread() {
        val thread = gameThread
        thread.requestStop()

        val deadlineNs = System.nanoTime() + 1_000_000_000L
        var callerWasInterrupted = false
        while (thread.isAlive) {
            val remainingNs = deadlineNs - System.nanoTime()
            if (remainingNs <= 0L) break

            val waitMs = (remainingNs / 1_000_000L).coerceIn(1L, 250L)
            try {
                thread.join(waitMs)
            } catch (_: InterruptedException) {
                callerWasInterrupted = true
                thread.requestStop()
            }
        }

        if (thread.isAlive) {
            Log.w(TAG, "GameThread did not terminate within the 1 second shutdown bound")
        }
        if (callerWasInterrupted) {
            Thread.currentThread().interrupt()
        }
    }
''',
        '''    private fun stopThread() {
        if (!gameThread.requestStopAndAwait()) {
            Log.w(TAG, "GameThread did not terminate within the 1 second shutdown bound")
        }
    }
''',
        "delegate GameView shutdown to tested GameThread boundary",
    )

    write(
        "app/src/test/java/com/anurag9000/forestrun/engine/GameThreadShutdownTest.kt",
        '''package com.anurag9000.forestrun.engine

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
'''
    )

    release = Path("docs/RELEASE.md")
    replace_once(
        release,
        '''- [x] Future-schema preference and ghost preservation with compatibility-namespace round trips
''',
        '''- [x] Future-schema preference and ghost preservation with compatibility-namespace round trips
- [x] Actual `GameThread` sleep interruption, caller-interrupt restoration, bounded uncooperative-update timeout, and stale-render suppression
''',
        "document shutdown regression coverage",
    )
    replace_once(
        release,
        '''- [ ] Execute `connectedDebugAndroidTest` on an emulator and physical device
- [ ] Add a deterministic interruption test around the real `GameThread`/`GameView` shutdown boundary if feasible without instrumentation
- [ ] Add signed-release installation and launch smoke tests
''',
        '''- [ ] Execute `connectedDebugAndroidTest` on an emulator and physical device
- [ ] Add signed-release installation and launch smoke tests
''',
        "remove completed shutdown test blocker",
    )


if __name__ == "__main__":
    main()
