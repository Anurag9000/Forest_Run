package com.yourname.forest_run.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.yourname.forest_run.engine.GameConstants
import com.yourname.forest_run.engine.GameStateManager

/**
 * Heads-Up Display — always drawn last so it's always on top of everything.
 *
 * Layout:
 *  ┌─────────────────────────────────────────────────────────────┐
 *  │  [🌱 × 7]   ║  BLOOM METER  ║              1,842 m  [NEW!] │
 *  │                                                             │
 *  └─────────────────────────────────────────────────────────────┘
 *  Left:   seed counter
 *  Centre: bloom meter (10 segments vertical bar, left side)
 *  Right:  score + distance (top-right)
 *          high score ghost line (below current if ahead)
 *
 * Font: PressStart2P (pixel font). Graceful fallback to Typeface.MONOSPACE
 * if the .ttf hasn't been placed in assets/fonts/ yet.
 */
class HUD(context: Context, private val screenWidth: Int, private val screenHeight: Int) {

    // -----------------------------------------------------------------------
    // Typeface – try to load pixel font, fall back silently
    // -----------------------------------------------------------------------
    private val pixelFont: Typeface = runCatching {
        Typeface.createFromAsset(context.assets, "fonts/PressStart2P-Regular.ttf")
    }.getOrDefault(Typeface.MONOSPACE)

    // -----------------------------------------------------------------------
    // Layout constants
    // -----------------------------------------------------------------------
    private val PAD          = 24f     // edge padding
    private val HUD_H        = 72f     // HUD strip height

    // Score text size
    private val SCORE_TEXT_SIZE  = 28f
    private val LABEL_TEXT_SIZE  = 20f
    private val SMALL_TEXT_SIZE  = 18f

    // Bloom meter geometry
    private val METER_LEFT   = screenWidth * 0.38f
    private val METER_RIGHT  = screenWidth * 0.62f
    private val METER_TOP    = 10f
    private val METER_BOTTOM = HUD_H - 10f
    private val SEG_GAP      = 4f
    private val SEGMENT_COUNT = GameConstants.BLOOM_SEED_COUNT

    // -----------------------------------------------------------------------
    // Reusable geometry objects (never allocate inside draw())
    // -----------------------------------------------------------------------
    private val hudBgRect    = RectF(0f, 0f, screenWidth.toFloat(), HUD_H)
    private val segRect      = RectF()
    private val scoreRect    = RectF()

    // -----------------------------------------------------------------------
    // Paints (created once)
    // -----------------------------------------------------------------------
    private val hudBgPaint = Paint().apply {
        color = Color.argb(200, 10, 15, 10)
        style = Paint.Style.FILL
    }

    private val hudBorderPaint = Paint().apply {
        color = Color.argb(120, 80, 200, 80)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    // Score & distance
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = SCORE_TEXT_SIZE
        typeface = pixelFont
        textAlign = Paint.Align.RIGHT
    }

    private val newHighPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 220, 50)
        textSize = LABEL_TEXT_SIZE
        typeface = pixelFont
        textAlign = Paint.Align.RIGHT
    }

    private val ghostScorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 160, 200, 255)
        textSize = SMALL_TEXT_SIZE
        typeface = pixelFont
        textAlign = Paint.Align.RIGHT
    }

    // Seed counter
    private val seedLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(140, 255, 120)
        textSize = LABEL_TEXT_SIZE
        typeface = pixelFont
        textAlign = Paint.Align.LEFT
    }

    private val seedCountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = SCORE_TEXT_SIZE
        typeface = pixelFont
        textAlign = Paint.Align.LEFT
    }

    // Bloom meter segments — filled
    private val segFilledPaint = Paint().apply {
        style = Paint.Style.FILL
        // Gradient set per-draw below
    }

    // Bloom meter segments — empty
    private val segEmptyPaint = Paint().apply {
        color = Color.argb(80, 60, 80, 60)
        style = Paint.Style.FILL
    }

    private val segBorderPaint = Paint().apply {
        color = Color.argb(140, 120, 200, 120)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    // Bloom meter label
    private val meterLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 160, 255, 160)
        textSize = SMALL_TEXT_SIZE - 2f
        typeface = pixelFont
        textAlign = Paint.Align.CENTER
    }

    // Bloom ACTIVE overlay
    private val bloomActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(200, 100, 255)
        textSize = LABEL_TEXT_SIZE
        typeface = pixelFont
        textAlign = Paint.Align.CENTER
    }

    // Bloom meter pulse (filled when active)
    private var bloomPulse = 0f

    // Distance label
    private val distLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 200, 200, 200)
        textSize = SMALL_TEXT_SIZE
        typeface = pixelFont
        textAlign = Paint.Align.RIGHT
    }

    // -----------------------------------------------------------------------
    // Update (called every frame)
    // -----------------------------------------------------------------------
    fun update(deltaTime: Float, state: GameStateManager) {
        // Bloom pulse animation: oscillates 0.0..1.0
        if (state.isBloomActive) {
            bloomPulse = (bloomPulse + deltaTime * 4f) % (Math.PI.toFloat() * 2f)
        }
    }

    // -----------------------------------------------------------------------
    // Draw
    // -----------------------------------------------------------------------
    fun draw(canvas: Canvas, state: GameStateManager) {
        // HUD background strip
        canvas.drawRect(hudBgRect, hudBgPaint)
        canvas.drawRect(hudBgRect, hudBorderPaint)

        drawSeedCounter(canvas, state)
        drawBloomMeter(canvas, state)
        drawScoreArea(canvas, state)
    }

    // -----------------------------------------------------------------------
    // Private draw sections
    // -----------------------------------------------------------------------

    private fun drawSeedCounter(canvas: Canvas, state: GameStateManager) {
        val x = PAD
        // Seed icon (pixel-art green circle) drawn as a small filled oval
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(120, 230, 80); style = Paint.Style.FILL
        }
        val cy = HUD_H / 2f
        canvas.drawOval(x, cy - 12f, x + 22f, cy + 12f, iconPaint)
        // Leaf vein line
        val veinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(60, 160, 40); style = Paint.Style.STROKE; strokeWidth = 2f
        }
        canvas.drawLine(x + 11f, cy - 10f, x + 11f, cy + 10f, veinPaint)

        // "× N" label
        val countText = "×${state.seedsThisRun}"
        canvas.drawText(countText, x + 32f, cy + seedCountPaint.textSize / 3f, seedCountPaint)

        // "SEEDS" subtitle
        canvas.drawText("seeds", x + 32f, cy + seedCountPaint.textSize / 3f + 20f, seedLabelPaint)
    }

    private fun drawBloomMeter(canvas: Canvas, state: GameStateManager) {
        val totalW    = METER_RIGHT - METER_LEFT
        val segW      = (totalW - SEG_GAP * (SEGMENT_COUNT - 1)) / SEGMENT_COUNT
        val segH      = METER_BOTTOM - METER_TOP
        val cornerR   = 4f

        for (i in 0 until SEGMENT_COUNT) {
            val left  = METER_LEFT + i * (segW + SEG_GAP)
            val right = left + segW
            segRect.set(left, METER_TOP, right, METER_BOTTOM)

            val filled = when {
                state.isBloomActive -> true    // all segments lit during bloom
                i < state.bloomMeter -> true
                else -> false
            }

            if (filled) {
                // Gradient: yellow-green at bottom → bright cyan-white at top
                val pulse = if (state.isBloomActive)
                    (0.5f + 0.5f * Math.sin(bloomPulse.toDouble()).toFloat())
                else 1f

                val alpha = (180 + (75 * pulse).toInt()).coerceIn(0, 255)

                segFilledPaint.shader = LinearGradient(
                    left, METER_BOTTOM,
                    left, METER_TOP,
                    intArrayOf(
                        Color.argb(alpha, 80,  220, 80),
                        Color.argb(alpha, 180, 255, 120)
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(segRect, cornerR, cornerR, segFilledPaint)
            } else {
                canvas.drawRoundRect(segRect, cornerR, cornerR, segEmptyPaint)
            }
            canvas.drawRoundRect(segRect, cornerR, cornerR, segBorderPaint)
        }

        // Label below meter
        val labelText = if (state.isBloomActive) "✦ BLOOM ✦" else "bloom"
        val labelPaint = if (state.isBloomActive) bloomActivePaint else meterLabelPaint
        val centerX = (METER_LEFT + METER_RIGHT) / 2f
        canvas.drawText(labelText, centerX, METER_BOTTOM + 16f, labelPaint)
    }

    private fun drawScoreArea(canvas: Canvas, state: GameStateManager) {
        val rightX  = screenWidth.toFloat() - PAD
        val topLine = HUD_H * 0.42f
        val botLine = HUD_H * 0.78f

        // Distance
        val distText = formatDistance(state.distanceMetres)
        canvas.drawText(distText, rightX, topLine, distLabelPaint)

        // Score
        val scoreText = formatScore(state.score)
        canvas.drawText(scoreText, rightX, botLine, scorePaint)

        // "NEW!" badge if beating high score
        if (state.isNewHighScore && state.score > 0) {
            canvas.drawText("✦NEW", rightX - scorePaint.measureText(scoreText) - 12f, botLine, newHighPaint)
        }

        // Ghost high score line (shown when NOT yet a new high score)
        if (!state.isNewHighScore && state.highScore > 0) {
            val ghostText = "best ${formatScore(state.highScore)}"
            canvas.drawText(ghostText, rightX, botLine + SMALL_TEXT_SIZE + 6f, ghostScorePaint)
        }
    }

    // -----------------------------------------------------------------------
    // Format helpers
    // -----------------------------------------------------------------------

    private fun formatDistance(metres: Float): String {
        return when {
            metres < 1_000f -> "${metres.toInt()} m"
            else            -> String.format("%.1f km", metres / 1_000f)
        }
    }

    private fun formatScore(score: Int): String {
        return when {
            score < 1_000   -> score.toString()
            score < 10_000  -> String.format("%,d", score)
            else            -> String.format("%,d", score)
        }
    }
}
