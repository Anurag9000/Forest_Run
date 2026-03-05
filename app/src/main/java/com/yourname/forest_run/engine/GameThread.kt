package com.yourname.forest_run.engine

import android.graphics.Canvas
import android.view.SurfaceHolder

/**
 * Dedicated background thread that drives the game loop.
 *
 * The loop:
 *  1. Calculates deltaTime (seconds) since last frame.
 *  2. Calls [GameView.update] with deltaTime.
 *  3. Locks the canvas, calls [GameView.draw], unlocks/posts.
 *  4. Sleeps for the remainder of the 16.67ms budget (targeting 60 FPS).
 *
 * Because we use deltaTime for all movement calculations the game
 * automatically adapts to 90 Hz or 120 Hz displays without running faster.
 */
class GameThread(
    private val surfaceHolder: SurfaceHolder,
    private val gameView: GameView
) : Thread("GameThread") {

    @Volatile var isRunning: Boolean = false

    // Target 60 FPS – each frame should take at most 16 666 microseconds
    private val targetFrameTimeNs: Long = 1_000_000_000L / 60L

    override fun run() {
        var lastTimeNs = System.nanoTime()

        while (isRunning) {
            val nowNs = System.nanoTime()
            // deltaTime in seconds, capped at 0.05 s (= 20 FPS minimum)
            // to prevent physics explosions if the app is paused/resumed.
            val deltaTime = ((nowNs - lastTimeNs) / 1_000_000_000.0).toFloat()
                .coerceIn(0f, 0.05f)
            lastTimeNs = nowNs

            // ----- Update game logic -----
            gameView.update(deltaTime)

            // ----- Draw -----
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
                    } catch (e: Exception) {
                        // Surface was destroyed while we held the canvas – ignore.
                    }
                }
            }

            // ----- Sleep for remaining budget -----
            val elapsedNs = System.nanoTime() - nowNs
            val sleepNs = targetFrameTimeNs - elapsedNs
            if (sleepNs > 0) {
                try {
                    sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt())
                } catch (e: InterruptedException) {
                    currentThread().interrupt()
                }
            }
        }
    }
}
