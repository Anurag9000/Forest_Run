package com.yourname.forest_run.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.sin

/**
 * Main-menu / Garden loading screen — Phase 22.
 *
 * Two phases:
 *   IDLE  — The character sits under a Weeping Willow. Ambient loop plays.
 *            A soft "Run!" prompt pulses at the bottom.
 *   TAP1  — Player character stands up (2 s animation drawn as a simple
 *            squash/stretch transition on the idle rect).
 *   TAP2  — Run starts (GameView transitions to PLAYING).
 *
 * Currently drawn with primitive shapes (no sprites) so the game is fully
 * playable while background artwork (Phase 24) is being added.
 *
 * The main forest colour palette used here matches the Spring Orchard biome.
 */
class MainMenuScreen(
    private val context: Context,
    private val screenW: Int,
    private val screenH: Int
) {
    enum class Phase { IDLE, STANDING_UP, READY }

    var phase: Phase = Phase.IDLE
        private set

    /** Returns true the first frame the player should begin running. */
    var shouldStartRun: Boolean = false
        private set

    /** Called when the user taps the Garden button in IDLE phase. */
    var onGardenTap: (() -> Unit)? = null

    // Timers
    private var standTimer = 0f
    private val STAND_DURATION = 1.8f

    // Pulse / ambient
    private var elapsedT = 0f

    // ── Font ─────────────────────────────────────────────────────────────
    private val pixelFont: Typeface = runCatching {
        Typeface.createFromAsset(context.assets, "fonts/PressStart2P-Regular.ttf")
    }.getOrDefault(Typeface.MONOSPACE)

    // ── Paints ────────────────────────────────────────────────────────────

    // Sky gradient
    private val skyPaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, screenH * 0.75f,
            intArrayOf(Color.rgb(120, 200, 255), Color.rgb(200, 235, 180)),
            null, Shader.TileMode.CLAMP
        )
    }

    // Ground
    private val groundPaint = Paint().apply { color = Color.rgb(90, 170, 80) }

    // Willow trunk
    private val trunkPaint  = Paint().apply { color = Color.rgb(100, 70, 40) }

    // Willow foliage
    private val foliagePaint = Paint().apply { color = Color.argb(200, 60, 140, 50) }

    // Character body rect
    private val charPaint = Paint().apply { color = Color.rgb(230, 180, 130) }
    private val charRect  = RectF()

    // Prompt text
    private val promptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.WHITE
        textSize  = 22f
        typeface  = pixelFont
        textAlign = Paint.Align.CENTER
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color    = Color.rgb(255, 240, 100)
        textSize = 34f
        typeface = pixelFont
        textAlign = Paint.Align.CENTER
    }

    // ── API ───────────────────────────────────────────────────────────────

    /** Called on each tap from GameView.onTouchListener. */
    fun onTap(tapX: Float = 0f, tapY: Float = 0f) {
        // Garden button: bottom-left strip in IDLE phase
        if (phase == Phase.IDLE && tapX < screenW * 0.35f && tapY > screenH * 0.85f) {
            onGardenTap?.invoke()
            return
        }
        when (phase) {
            Phase.IDLE         -> { phase = Phase.STANDING_UP; standTimer = 0f }
            Phase.STANDING_UP  -> { /* wait for animation */ }
            Phase.READY        -> { shouldStartRun = true }
        }
    }

    fun update(deltaTime: Float) {
        elapsedT += deltaTime

        if (phase == Phase.STANDING_UP) {
            standTimer += deltaTime
            if (standTimer >= STAND_DURATION) {
                phase = Phase.READY
            }
        }

        // Reset trigger after one frame
        if (shouldStartRun) shouldStartRun = false
    }

    fun draw(canvas: Canvas) {
        val cw = screenW.toFloat()
        val ch = screenH.toFloat()
        val groundY = ch * 0.78f

        // Sky
        canvas.drawRect(0f, 0f, cw, ch, skyPaint)
        // Ground
        canvas.drawRect(0f, groundY, cw, ch, groundPaint)

        // Weeping Willow — left-centre of screen
        drawWillow(canvas, cw * 0.35f, groundY)

        // Character
        drawCharacter(canvas, cw * 0.62f, groundY)

        // Title
        canvas.drawText("FOREST RUN", cw / 2f, ch * 0.10f + titlePaint.textSize, titlePaint)

        // Prompt
        val promptAlpha = when (phase) {
            Phase.IDLE         -> (0.6f + 0.4f * sin(elapsedT * 2.5f)) * 255
            Phase.STANDING_UP  -> 200f
            Phase.READY        -> (0.5f + 0.5f * sin(elapsedT * 4f)) * 255
        }
        promptPaint.alpha = promptAlpha.toInt().coerceIn(0, 255)
        val promptText = when (phase) {
            Phase.IDLE        -> "tap to stand"
            Phase.STANDING_UP -> "..."
            Phase.READY       -> "tap to run!"
        }
        canvas.drawText(promptText, cw / 2f, ch * 0.92f, promptPaint)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun drawWillow(canvas: Canvas, cx: Float, groundY: Float) {
        val trunkH = 160f
        val trunkW  = 28f
        canvas.drawRect(cx - trunkW / 2f, groundY - trunkH, cx + trunkW / 2f, groundY, trunkPaint)
        // Canopy (ellipse)
        val sway = sin(elapsedT * 0.7f) * 18f
        canvas.drawOval(
            cx - 120f + sway, groundY - trunkH - 100f,
            cx + 120f + sway, groundY - trunkH + 80f,
            foliagePaint
        )
        // Trailing strands
        val strandPaint = Paint().apply { color = Color.argb(160, 50, 120, 40); strokeWidth = 3f; style = Paint.Style.STROKE }
        for (i in -3..3) {
            val sx = cx + i * 32f + sway * 0.5f
            canvas.drawLine(sx, groundY - trunkH + 20f, sx + sway * 0.3f, groundY, strandPaint)
        }
    }

    private fun drawCharacter(canvas: Canvas, x: Float, groundY: Float) {
        val t     = (standTimer / STAND_DURATION).coerceIn(0f, 1f)
        val baseH = 92f
        val baseW = 44f

        val scaleY = when (phase) {
            Phase.IDLE         -> 0.65f   // sitting
            Phase.STANDING_UP  -> 0.65f + t * 0.35f  // lerp to standing
            Phase.READY        -> 1.00f + 0.05f * sin(elapsedT * 4f)  // gentle bounce
        }
        val scaleX = when (phase) {
            Phase.STANDING_UP -> 1f + 0.15f * sin(t * Math.PI.toFloat()) // squash mid-rise
            else -> 1f
        }

        val h = baseH * scaleY
        val w = baseW * scaleX
        charRect.set(x - w / 2f, groundY - h, x + w / 2f, groundY)
        canvas.drawRoundRect(charRect, 12f, 12f, charPaint)

        // Simple dot eyes
        val eyePaint = Paint().apply { color = Color.rgb(60, 30, 10) }
        canvas.drawCircle(charRect.centerX() - 8f, charRect.top + h * 0.28f, 4f, eyePaint)
        canvas.drawCircle(charRect.centerX() + 8f, charRect.top + h * 0.28f, 4f, eyePaint)
    }
}
