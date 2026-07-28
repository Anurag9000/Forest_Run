package com.anurag9000.forestrun.engine

import android.graphics.Canvas
import android.view.SurfaceHolder
import java.util.concurrent.TimeUnit

/** Dedicated 60 Hz background thread for update and Canvas rendering. */
class GameThread internal constructor(
    private val updateFrame: (Float) -> Unit,
    private val renderFrame: () -> Unit = {},
    targetFrameTimeNs: Long = DEFAULT_TARGET_FRAME_TIME_NS
) : Thread("GameThread") {
    private val targetFrameTimeNs = targetFrameTimeNs.coerceAtLeast(0L)

    constructor(surfaceHolder: SurfaceHolder, gameView: GameView) : this(
        updateFrame = { deltaTime -> gameView.update(deltaTime) },
        renderFrame = { renderSurfaceFrame(surfaceHolder, gameView) },
        targetFrameTimeNs = DEFAULT_TARGET_FRAME_TIME_NS
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
