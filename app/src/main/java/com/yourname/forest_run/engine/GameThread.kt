package com.yourname.forest_run.engine

import android.graphics.Canvas
import android.view.SurfaceHolder

/** Dedicated 60 Hz background thread for update and Canvas rendering. */
class GameThread(
    private val surfaceHolder: SurfaceHolder,
    private val gameView: GameView
) : Thread("GameThread") {
    @Volatile
    private var running: Boolean = false

    var isRunning: Boolean
        get() = running
        set(value) {
            running = value
            if (!value) interrupt()
        }

    private val targetFrameTimeNs: Long = 1_000_000_000L / 60L

    fun requestStop() {
        isRunning = false
    }

    override fun run() {
        var lastTimeNs = System.nanoTime()
        try {
            while (running) {
                val nowNs = System.nanoTime()
                val deltaTime = ((nowNs - lastTimeNs) / 1_000_000_000.0).toFloat()
                    .coerceIn(0f, 0.05f)
                lastTimeNs = nowNs

                gameView.update(deltaTime)

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

                val elapsedNs = System.nanoTime() - nowNs
                val sleepNs = targetFrameTimeNs - elapsedNs
                if (sleepNs > 0) {
                    try {
                        sleep(
                            sleepNs / 1_000_000L,
                            (sleepNs % 1_000_000L).toInt()
                        )
                    } catch (_: InterruptedException) {
                        // Stopping the thread interrupts sleep so shutdown does
                        // not wait for the remainder of a frame. Any other
                        // interruption is also treated as cancellation.
                        return
                    }
                }
            }
        } finally {
            running = false
        }
    }
}
