package com.yourname.forest_run.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.yourname.forest_run.engine.AssetPaths
import kotlin.math.sin

/**
 * Full-screen Game Over overlay.
 *
 * Displayed during [RunState.GAME_OVER].
 * Tap-anywhere input is handled in GameView's onTouchListener;
 * this class only handles drawing.
 *
 * Layout (top → bottom, centred):
 *  1. Semi-transparent dark scrim.
 *  2. Pixel-art bordered panel (rounded rect).
 *  3. "GAME OVER" header in red-gradient PressStart2P.
 *  4. Score (large).
 *  5. Distance label.
 *  6. Best (distance) if this run set a new high score — animated "NEW!" badge.
 *  7. Mercy hearts earned this run (small heart icons in a row).
 *  8. "tap anywhere to run again" — subtle pulsing prompt.
 */
class GameOverScreen(
    context: Context,
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    // ── Fonts ─────────────────────────────────────────────────────────────
    private val pixelFont: Typeface = runCatching {
        Typeface.createFromAsset(context.assets, AssetPaths.PIXEL_FONT)
    }.getOrDefault(Typeface.MONOSPACE)

    // ── Paints ────────────────────────────────────────────────────────────
    private val scrimPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
    }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 20, 10, 30)
        style = Paint.Style.FILL
    }
    private val panelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 180, 80, 220)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface  = pixelFont
        textSize  = 52f
        textAlign = Paint.Align.CENTER
        color     = Color.rgb(255, 80, 80)
    }
    private val scoreLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface  = pixelFont
        textSize  = 22f
        textAlign = Paint.Align.CENTER
        color     = Color.argb(200, 180, 180, 180)
    }
    private val scoreValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface  = pixelFont
        textSize  = 44f
        textAlign = Paint.Align.CENTER
        color     = Color.WHITE
    }
    private val distancePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface  = pixelFont
        textSize  = 24f
        textAlign = Paint.Align.CENTER
        color     = Color.rgb(140, 220, 140)
    }
    private val newBestPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface  = pixelFont
        textSize  = 20f
        textAlign = Paint.Align.CENTER
        color     = Color.rgb(255, 220, 60)
    }
    private val heartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize  = 28f
        textAlign = Paint.Align.CENTER
        color     = Color.rgb(255, 100, 100)
    }
    private val promptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface  = pixelFont
        textSize  = 18f
        textAlign = Paint.Align.CENTER
        color     = Color.argb(200, 200, 180, 255)
    }

    // ── Pulse timer ───────────────────────────────────────────────────────
    private var pulseTimer = 0f

    // ── Panel rect (computed once) ────────────────────────────────────────
    private val panelW     = screenWidth  * 0.72f
    private val panelH     = screenHeight * 0.70f
    private val panelLeft  = (screenWidth  - panelW) / 2f
    private val panelTop   = (screenHeight - panelH) / 2f
    private val panelRect  = RectF(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH)
    private val cx         = screenWidth / 2f

    // ── Update ────────────────────────────────────────────────────────────

    fun update(deltaTime: Float) {
        pulseTimer += deltaTime
    }

    // ── Draw ──────────────────────────────────────────────────────────────

    fun draw(
        canvas:        Canvas,
        score:         Int,
        distanceM:     Float,
        isNewHighScore: Boolean,
        highScore:     Int,
        mercyHearts:   Int
    ) {
        val w = screenWidth.toFloat()
        val h = screenHeight.toFloat()

        // 1. Scrim
        canvas.drawRect(0f, 0f, w, h, scrimPaint)

        // 2. Panel
        canvas.drawRoundRect(panelRect, 24f, 24f, panelPaint)
        canvas.drawRoundRect(panelRect, 24f, 24f, panelBorderPaint)

        var ty = panelTop + 70f

        // 3. Title
        canvas.drawText("GAME OVER", cx, ty, titlePaint)
        ty += 65f

        // 4. Score label + value
        canvas.drawText("SCORE", cx, ty, scoreLabelPaint)
        ty += 36f
        canvas.drawText(formatNumber(score), cx, ty, scoreValuePaint)
        ty += 55f

        // 5. Distance
        canvas.drawText("${distanceM.toInt()} m", cx, ty, distancePaint)
        ty += 40f

        // 6. New best badge (if applicable)
        if (isNewHighScore) {
            val pulse = sin(pulseTimer * 4f) * 0.2f + 0.8f
            newBestPaint.alpha = (pulse * 255f).toInt()
            canvas.drawText("★ NEW BEST! ${formatNumber(highScore)} ★", cx, ty, newBestPaint)
            ty += 38f
        }

        // 7. Mercy hearts row
        if (mercyHearts > 0) {
            val hearts = "♥".repeat(mercyHearts.coerceAtMost(10))
            canvas.drawText(hearts, cx, ty, heartPaint)
            ty += 40f
        }

        // 8. Tap prompt — pulsing alpha
        val promptAlpha = ((sin(pulseTimer * 2.5f) * 0.4f + 0.6f) * 200).toInt().coerceIn(0, 255)
        promptPaint.alpha = promptAlpha
        canvas.drawText("tap anywhere to run again", cx, ty + 20f, promptPaint)
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private fun formatNumber(n: Int): String {
        if (n < 1000) return n.toString()
        val thousands = n / 1000
        val remainder = n % 1000
        return "${thousands},${remainder.toString().padStart(3, '0')}"
    }
}
