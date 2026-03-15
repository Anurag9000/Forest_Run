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
 * Full-screen post-run rest overlay.
 *
 * Displayed during [RunState.GAME_OVER].
 * Tap-anywhere input is handled in GameView's onTouchListener;
 * this class only handles drawing.
 *
 * Layout (top → bottom, centred):
 *  1. Semi-transparent dark scrim.
 *  2. Pixel-art bordered panel (rounded rect).
 *  3. "REST" header.
 *  4. Reflective quote / killer memory.
 *  5. Score and distance.
 *  6. Seeds carried into the garden.
 *  7. Mercy hearts earned this run.
 *  8. "tap anywhere to return to garden" — subtle pulsing prompt.
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
        color = Color.argb(232, 16, 18, 26)
        style = Paint.Style.FILL
    }
    private val panelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 120, 210, 150)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface  = pixelFont
        textSize  = 46f
        textAlign = Paint.Align.CENTER
        color     = Color.rgb(220, 255, 210)
    }
    private val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = pixelFont
        textSize = 18f
        textAlign = Paint.Align.CENTER
        color = Color.argb(220, 220, 240, 220)
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
    private val seedsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = pixelFont
        textSize = 20f
        textAlign = Paint.Align.CENTER
        color = Color.rgb(160, 255, 120)
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
        mercyHearts:   Int,
        mercyMisses:   Int,
        kindnessChain: Int,
        cleanPasses:   Int,
        sparedCount:   Int,
        hitsTaken:     Int,
        seedsCollected: Int,
        restQuote: String
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
        canvas.drawText("REST", cx, ty, titlePaint)
        ty += 48f

        drawWrappedCenteredText(canvas, restQuote, cx, ty, panelW * 0.82f, quotePaint)
        ty += 64f

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

        if (seedsCollected > 0) {
            canvas.drawText("+$seedsCollected seeds carried home", cx, ty, seedsPaint)
            ty += 38f
        }

        // 7. Mercy hearts row
        if (mercyHearts > 0) {
            val hearts = "♥".repeat(mercyHearts.coerceAtMost(10))
            canvas.drawText(hearts, cx, ty, heartPaint)
            ty += 40f
        }

        val summaryBits = buildList {
            if (mercyMisses > 0) add("$mercyMisses close calls")
            if (cleanPasses > 0) add("$cleanPasses clean")
            if (sparedCount > 0) add("$sparedCount spared")
            if (hitsTaken > 0) add("$hitsTaken hit")
        }
        if (summaryBits.isNotEmpty()) {
            canvas.drawText(summaryBits.joinToString("  •  "), cx, ty, scoreLabelPaint)
            ty += 34f
        }
        if (kindnessChain > 0) {
            canvas.drawText("best kindness chain $kindnessChain", cx, ty, distancePaint)
            ty += 34f
        }

        // 8. Tap prompt — pulsing alpha
        val promptAlpha = ((sin(pulseTimer * 2.5f) * 0.4f + 0.6f) * 200).toInt().coerceIn(0, 255)
        promptPaint.alpha = promptAlpha
        canvas.drawText("tap anywhere to return to garden", cx, ty + 20f, promptPaint)
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private fun formatNumber(n: Int): String {
        if (n < 1000) return n.toString()
        val thousands = n / 1000
        val remainder = n % 1000
        return "${thousands},${remainder.toString().padStart(3, '0')}"
    }

    private fun drawWrappedCenteredText(
        canvas: Canvas,
        text: String,
        centerX: Float,
        baselineY: Float,
        maxWidth: Float,
        paint: Paint
    ) {
        val words = text.split(" ")
        if (words.isEmpty()) return

        val lines = mutableListOf<String>()
        val builder = StringBuilder()
        for (word in words) {
            val candidate = if (builder.isEmpty()) word else "${builder} $word"
            if (paint.measureText(candidate) <= maxWidth) {
                builder.clear()
                builder.append(candidate)
            } else {
                lines += builder.toString()
                builder.clear()
                builder.append(word)
            }
        }
        if (builder.isNotEmpty()) lines += builder.toString()

        var y = baselineY
        for (line in lines.take(2)) {
            canvas.drawText(line, centerX, y, paint)
            y += paint.textSize + 8f
        }
    }
}
