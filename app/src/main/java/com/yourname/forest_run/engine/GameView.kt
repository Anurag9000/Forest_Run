package com.yourname.forest_run.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

private const val TAG = "ForestRun"

/**
 * The top-level game view.
 *
 * Current phases implemented:
 *  - Phase 0: SurfaceView + GameThread scaffold
 *  - Phase 1: 60 FPS loop with nanosecond deltaTime
 *  - Phase 2: [InputHandler] wired + on-screen debug panel
 *
 * Upcoming phases will progressively fill [update] and [draw] with real systems.
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    // -----------------------------------------------------------------------
    // Engine
    // -----------------------------------------------------------------------
    private var gameThread: GameThread = GameThread(holder, this)

    // -----------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------
    val inputHandler = InputHandler()

    // -----------------------------------------------------------------------
    // FPS tracking
    // -----------------------------------------------------------------------
    private var fpsFrameCount = 0
    private var fpsElapsed    = 0f
    private var currentFps    = 0

    // -----------------------------------------------------------------------
    // Debug: last few input events (ring buffer of 5)
    // -----------------------------------------------------------------------
    private val inputLog = ArrayDeque<String>(6)

    // -----------------------------------------------------------------------
    // Paint objects – created ONCE, never inside draw()
    // -----------------------------------------------------------------------
    private val bgPaint = Paint().apply {
        color = Color.BLACK; style = Paint.Style.FILL
    }

    private val fpsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 30f; typeface = Typeface.MONOSPACE
    }

    private val fpsLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 40, 220, 100); textSize = 30f; typeface = Typeface.MONOSPACE
    }

    // Debug panel background
    private val debugBgPaint = Paint().apply {
        color = Color.argb(160, 0, 0, 0); style = Paint.Style.FILL
    }

    // Debug panel text
    private val debugTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 26f; typeface = Typeface.MONOSPACE
    }

    // Jump indicator (yellow box)
    private val jumpBoxPaint = Paint().apply {
        color = Color.argb(200, 255, 220, 0); style = Paint.Style.FILL
    }

    // Duck indicator (cyan box)
    private val duckBoxPaint = Paint().apply {
        color = Color.argb(200, 0, 220, 255); style = Paint.Style.FILL
    }

    // Neutral indicator box
    private val neutralBoxPaint = Paint().apply {
        color = Color.argb(120, 80, 80, 80); style = Paint.Style.FILL
    }

    private val indicatorRect = RectF()

    // -----------------------------------------------------------------------
    // Screen dimensions
    // -----------------------------------------------------------------------
    var screenWidth:  Int = 0
        private set
    var screenHeight: Int = 0
        private set

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------
    init {
        holder.addCallback(this)
        wireInputCallbacks()
        setOnTouchListener(inputHandler)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenWidth  = width
        screenHeight = height
        gameThread.isRunning = true
        if (gameThread.state == Thread.State.NEW) gameThread.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenWidth  = width
        screenHeight = height
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopThread()
    }

    fun pause() { stopThread() }

    fun resume() {
        // Re-create thread (Java threads can't be restarted after stop)
        gameThread = GameThread(holder, this)
        // Thread starts when surfaceCreated fires again (or immediately if surface exists)
        if (holder.surface.isValid) {
            gameThread.isRunning = true
            gameThread.start()
        }
    }

    private fun stopThread() {
        gameThread.isRunning = false
        var retry = true
        while (retry) {
            try { gameThread.join(); retry = false }
            catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        }
    }

    // -----------------------------------------------------------------------
    // Input callback wiring
    // -----------------------------------------------------------------------

    private fun wireInputCallbacks() {
        inputHandler.onJumpPressed = {
            Log.d(TAG, "INPUT → JUMP PRESSED")
            addInputLog("▲ Jump pressed")
        }

        inputHandler.onJumpHeld = { holdSec ->
            // Fires every frame while held – only log at 0.1s intervals to avoid spam
            if ((holdSec * 10).toInt() != ((holdSec - 0.016f) * 10).toInt()) {
                Log.d(TAG, "INPUT → JUMP HELD ${String.format("%.2f", holdSec)}s")
            }
        }

        inputHandler.onJumpReleased = { holdSec ->
            val type = if (holdSec < 0.12f) "TAP" else "HOLD(${String.format("%.2f", holdSec)}s)"
            Log.d(TAG, "INPUT → JUMP RELEASED [$type]")
            addInputLog("▲ Jump $type")
        }

        inputHandler.onDuckPressed = {
            Log.d(TAG, "INPUT → DUCK")
            addInputLog("▼ Duck")
        }

        inputHandler.onDuckReleased = {
            Log.d(TAG, "INPUT → DUCK END")
            addInputLog("▼ Duck end")
        }
    }

    private fun addInputLog(msg: String) {
        if (inputLog.size >= 5) inputLog.removeFirst()
        inputLog.addLast(msg)
    }

    // -----------------------------------------------------------------------
    // Game loop – called by GameThread
    // -----------------------------------------------------------------------

    fun update(deltaTime: Float) {
        // FPS bookkeeping
        fpsFrameCount++
        fpsElapsed += deltaTime
        if (fpsElapsed >= 1f) {
            currentFps    = fpsFrameCount
            fpsFrameCount = 0
            fpsElapsed   -= 1f
        }

        // Tick the input handler so hold duration accumulates
        inputHandler.tick(deltaTime)

        // Phase 3+ will call subsystems here:
        // gameStateManager.update(deltaTime)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        // ── Background ──────────────────────────────────────────────
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // ── FPS counter (top-left) ────────────────────────────────
        drawFps(canvas)

        // ── Input debug panel (bottom-left) ──────────────────────
        drawInputDebugPanel(canvas)

        // ── Input state indicator (bottom-right) ─────────────────
        drawInputStateIndicator(canvas)

        // Phase 3+ draw calls go here ─────────────────────────────
        // parallaxBackground.draw(canvas)
        // player.draw(canvas)
        // entityManager.draw(canvas)
        // hud.draw(canvas)
        // flavorTextManager.draw(canvas)
    }

    // -----------------------------------------------------------------------
    // Private draw helpers
    // -----------------------------------------------------------------------

    private fun drawFps(canvas: Canvas) {
        val label = "FPS: "
        val x = 24f; val y = 52f
        canvas.drawText(label, x, y, fpsLabelPaint)
        canvas.drawText(currentFps.toString(), x + fpsLabelPaint.measureText(label), y, fpsPaint)
    }

    /**
     * Draws the last 5 input events as scrolling log in bottom-left.
     * Shows gesture string and hold duration live.
     */
    private fun drawInputDebugPanel(canvas: Canvas) {
        val panelW = 420f
        val lineH  = 34f
        val lines  = inputLog.size + 2  // log lines + header + live status
        val panelH = lines * lineH + 16f

        val left   = 16f
        val bottom = height.toFloat() - 16f
        val top    = bottom - panelH

        // Panel background
        canvas.drawRoundRect(left, top, left + panelW, bottom, 8f, 8f, debugBgPaint)

        var ty = top + lineH
        debugTextPaint.color = Color.argb(200, 100, 255, 150)
        canvas.drawText("── INPUT DEBUG ──", left + 12f, ty, debugTextPaint)
        debugTextPaint.color = Color.WHITE

        ty += lineH
        // Live status line
        val liveStatus = when {
            inputHandler.isDucking      -> "▼ DUCKING"
            inputHandler.isChargingJump -> "▲ CHARGING ${String.format("%.2f", inputHandler.holdDuration)}s"
            else                        -> "· idle"
        }
        debugTextPaint.color = if (inputHandler.isDucking) Color.CYAN
                               else if (inputHandler.isChargingJump) Color.YELLOW
                               else Color.GRAY
        canvas.drawText(liveStatus, left + 12f, ty, debugTextPaint)
        debugTextPaint.color = Color.WHITE
        ty += 4f

        for (entry in inputLog) {
            ty += lineH
            canvas.drawText(entry, left + 12f, ty, debugTextPaint)
        }
    }

    /**
     * Draws a coloured square in bottom-right showing current input state at a glance.
     * YELLOW = jumping, CYAN = ducking, GREY = idle.
     */
    private fun drawInputStateIndicator(canvas: Canvas) {
        val size  = 80f
        val right  = width.toFloat()  - 24f
        val bottom = height.toFloat() - 24f
        indicatorRect.set(right - size, bottom - size, right, bottom)

        val paint = when {
            inputHandler.isChargingJump -> jumpBoxPaint
            inputHandler.isDucking      -> duckBoxPaint
            else                        -> neutralBoxPaint
        }
        canvas.drawRoundRect(indicatorRect, 12f, 12f, paint)

        val label = when {
            inputHandler.isChargingJump -> "▲"
            inputHandler.isDucking      -> "▼"
            else                        -> "·"
        }
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 40f; typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(label, indicatorRect.centerX(), indicatorRect.centerY() + 14f, lp)
    }
}
