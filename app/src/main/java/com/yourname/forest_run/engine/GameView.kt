package com.yourname.forest_run.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * The top-level game view.
 *
 * Phase 0 deliverable:
 *  - SurfaceView with a dedicated [GameThread]
 *  - Black background
 *  - FPS counter drawn in the top-left corner so we can verify 60 FPS
 *  - [update] and [draw] stubs ready for Phases 1+
 *
 * Phases will progressively fill [update] and [draw] with real systems.
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    // -----------------------------------------------------------------------
    // Engine
    // -----------------------------------------------------------------------
    private val gameThread: GameThread = GameThread(holder, this)

    // -----------------------------------------------------------------------
    // FPS Tracking (Phase 0 – can be disabled later when publishing)
    // -----------------------------------------------------------------------
    private var fpsFrameCount: Int = 0
    private var fpsElapsed: Float = 0f
    private var currentFps: Int = 0

    // -----------------------------------------------------------------------
    // Paint objects – created ONCE, never inside draw()
    // -----------------------------------------------------------------------
    private val bgPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val fpsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        typeface = Typeface.MONOSPACE
        style = Paint.Style.FILL
    }

    private val fpsLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 0, 220, 100)   // green-ish indicator
        textSize = 28f
        typeface = Typeface.MONOSPACE
        style = Paint.Style.FILL
    }

    // -----------------------------------------------------------------------
    // Screen dimensions – set once surface is created
    // -----------------------------------------------------------------------
    var screenWidth: Int = 0
        private set
    var screenHeight: Int = 0
        private set

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------
    init {
        holder.addCallback(this)
        // Force the surface to be on top and opaque (no transparency needed)
        setZOrderOnTop(false)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenWidth = width
        screenHeight = height
        gameThread.isRunning = true
        if (gameThread.state == Thread.State.NEW) {
            gameThread.start()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Ask the thread to stop and wait for it to finish
        gameThread.isRunning = false
        var retry = true
        while (retry) {
            try {
                gameThread.join()
                retry = false
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    /** Called by [MainActivity.onPause] – stops the game loop. */
    fun pause() {
        gameThread.isRunning = false
        try {
            gameThread.join()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** Called by [MainActivity.onResume] – restarts the game loop thread. */
    fun resume() {
        // A Thread cannot be restarted once stopped – we do nothing here for now.
        // In Phase 1 we will replace the thread so it can be restarted properly.
    }

    // -----------------------------------------------------------------------
    // Game loop – called by GameThread
    // -----------------------------------------------------------------------

    /**
     * Update all game systems.
     * @param deltaTime Seconds elapsed since the previous frame (capped at 0.05 s).
     */
    fun update(deltaTime: Float) {
        // FPS counter bookkeeping
        fpsFrameCount++
        fpsElapsed += deltaTime
        if (fpsElapsed >= 1f) {
            currentFps = fpsFrameCount
            fpsFrameCount = 0
            fpsElapsed -= 1f
        }

        // Phases 1+ will call subsystem updates here:
        // gameStateManager.update(deltaTime)
    }

    /**
     * Draw everything onto [canvas].
     * Draw order matters – later calls paint on top of earlier ones.
     */
    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        // ── Background ──
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // ── Phase 0: FPS counter ──
        drawFpsCounter(canvas)

        // Phases 1+ will draw subsystems here:
        // parallaxBackground.draw(canvas)
        // entityManager.draw(canvas)
        // player.draw(canvas)
        // hud.draw(canvas)
    }

    // -----------------------------------------------------------------------
    // Private draw helpers
    // -----------------------------------------------------------------------

    private fun drawFpsCounter(canvas: Canvas) {
        val label = "FPS: "
        val value = currentFps.toString()
        val x = 24f
        val y = 52f

        canvas.drawText(label, x, y, fpsLabelPaint)
        val labelWidth = fpsLabelPaint.measureText(label)
        canvas.drawText(value, x + labelWidth, y, fpsPaint)
    }
}
