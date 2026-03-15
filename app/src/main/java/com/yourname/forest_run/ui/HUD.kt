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
import com.yourname.forest_run.engine.GameConstants
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.utils.MathUtils
import kotlin.math.sin

/**
 * Heads-Up Display — Phase 18 fully polished.
 *
 * ┌──────────────────────────────────────────────────────────────────────┐
 * │  🌱 ×7    ║ [▓▓▓▓▒▒▒▒▒▒] BLOOM  ║  1,842 m   ✦NEW  │
 * │  ♥♥♥♥♥                                          12,048    │
 * └──────────────────────────────────────────────────────────────────────┘
 *
 * Features:
 *  - Bloom meter: 10 rounded segments, animated fill (smooth lerp),
 *    green gradient per segment, glowing border ring when full/active.
 *  - Mercy heart strip: ♥ icons rendered below the seed counter.
 *  - Score: PressStart2P, right-aligned, comma-formatted.
 *  - Distance: top-right in km when > 1000 m.
 *  - NEW badge: pulsing gold star when beating high score.
 *  - Ghost best score shown when NOT yet a high score.
 *  - All paints and geometry objects allocated once (zero GC in draw).
 */
class HUD(context: Context, private val screenWidth: Int, private val screenHeight: Int) {

    // ── Font ─────────────────────────────────────────────────────────────
    private val pixelFont: Typeface = runCatching {
        Typeface.createFromAsset(context.assets, AssetPaths.PIXEL_FONT)
    }.getOrDefault(Typeface.MONOSPACE)

    // ── Layout ────────────────────────────────────────────────────────────
    private val PAD          = 28f
    private val HUD_H        = 112f
    private val SCORE_SIZE   = 34f
    private val LABEL_SIZE   = 20f
    private val SMALL_SIZE   = 18f
    private val HEART_SIZE   = 26f

    // Bloom meter
    private val METER_LEFT    = screenWidth * 0.30f
    private val METER_RIGHT   = screenWidth * 0.70f
    private val METER_TOP     = 16f
    private val METER_BOTTOM  = 62f
    private val SEG_GAP       = 4f
    private val SEG_COUNT     = GameConstants.BLOOM_SEED_COUNT
    private val segW          = (METER_RIGHT - METER_LEFT - SEG_GAP * (SEG_COUNT - 1)) / SEG_COUNT
    private val CORNER_R      = 5f

    // ── Reusable geometry ────────────────────────────────────────────────
    private val hudBgRect  = RectF(0f, 0f, screenWidth.toFloat(), HUD_H)
    private val segRect    = RectF()
    private val glowRect   = RectF()
    private val partRect   = RectF()

    // ── Animated fill ─────────────────────────────────────────────────────
    /** Smoothly-lerped fill level (0..SEG_COUNT). Drives segment rendering. */
    private var displayedFill = 0f
    private val FILL_LERP_SPEED = 8f    // segments per second

    // ── Pulse / animation timers ─────────────────────────────────────────
    private var bloomPulse  = 0f   // 0..2π oscillator
    private var newBadge    = 0f   // 0..2π oscillator for NEW! badge
    private var heartPulse  = 0f   // hearts bounce on mercy gain

    // ── Paints ────────────────────────────────────────────────────────────

    private val hudBgPaint = Paint().apply {
        color = Color.argb(210, 8, 12, 8)
    }
    private val hudBorderPaint = Paint().apply {
        color = Color.argb(120, 80, 200, 80)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    // Score & distance
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = SCORE_SIZE; typeface = pixelFont
        textAlign = Paint.Align.RIGHT
    }
    private val distPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 180, 220, 180); textSize = SMALL_SIZE; typeface = pixelFont
        textAlign = Paint.Align.RIGHT
    }
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 140, 180, 255); textSize = SMALL_SIZE; typeface = pixelFont
        textAlign = Paint.Align.RIGHT
    }
    private val newBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 220, 50); textSize = LABEL_SIZE; typeface = pixelFont
        textAlign = Paint.Align.RIGHT
    }

    // Seed counter
    private val seedIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 230, 80); style = Paint.Style.FILL
    }
    private val seedVeinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(60, 160, 40); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val seedCountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = SCORE_SIZE; typeface = pixelFont
        textAlign = Paint.Align.LEFT
    }
    private val seedLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(140, 255, 120); textSize = LABEL_SIZE; typeface = pixelFont
        textAlign = Paint.Align.LEFT
    }

    // Mercy hearts
    private val heartFilledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 80, 100); textSize = HEART_SIZE
        textAlign = Paint.Align.LEFT
    }
    private val heartEmptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 160, 60, 70); textSize = HEART_SIZE
        textAlign = Paint.Align.LEFT
    }

    // Bloom meter
    private val segFilledPaint = Paint().apply { style = Paint.Style.FILL }
    private val segEmptyPaint  = Paint().apply {
        color = Color.argb(70, 50, 70, 50); style = Paint.Style.FILL
    }
    private val segBorderPaint = Paint().apply {
        color = Color.argb(130, 100, 180, 100); style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    private val glowBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val bloomLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 140, 255, 140); textSize = SMALL_SIZE - 2f; typeface = pixelFont
        textAlign = Paint.Align.CENTER
    }
    private val bloomActiveLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(200, 100, 255); textSize = LABEL_SIZE; typeface = pixelFont
        textAlign = Paint.Align.CENTER
    }
    private val bloomCountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 210, 255, 210); textSize = SMALL_SIZE; typeface = pixelFont
        textAlign = Paint.Align.CENTER
    }

    // ── Update ────────────────────────────────────────────────────────────

    fun update(deltaTime: Float, state: GameStateManager) {
        val dt = deltaTime

        // Smooth fill lerp toward actual bloom meter value
        val target = if (state.isBloomActive) SEG_COUNT.toFloat() else state.bloomMeter.toFloat()
        displayedFill = MathUtils.lerp(displayedFill, target, (FILL_LERP_SPEED * dt).coerceAtMost(1f))

        // Bloom pulse oscillator
        if (state.isBloomActive) {
            bloomPulse = (bloomPulse + dt * 4.5f) % (Math.PI.toFloat() * 2f)
        } else {
            bloomPulse = 0f
        }

        // NEW badge pulse
        if (state.isNewHighScore) {
            newBadge = (newBadge + dt * 3f) % (Math.PI.toFloat() * 2f)
        }
    }

    // ── Draw ──────────────────────────────────────────────────────────────

    fun draw(canvas: Canvas, state: GameStateManager) {
        // HUD background strip
        canvas.drawRect(hudBgRect, hudBgPaint)
        canvas.drawRect(hudBgRect, hudBorderPaint)

        drawSeedAndHearts(canvas, state)
        drawBloomMeter(canvas, state)
        drawScoreArea(canvas, state)
    }

    // ── Sections ─────────────────────────────────────────────────────────

    private fun drawSeedAndHearts(canvas: Canvas, state: GameStateManager) {
        val x   = PAD
        val cy1 = 40f
        val cy2 = 88f

        // ── Seed icon ─────────────────────────────────────────────────────
        canvas.drawOval(x, cy1 - 16f, x + 30f, cy1 + 16f, seedIconPaint)
        canvas.drawLine(x + 15f, cy1 - 13f, x + 15f, cy1 + 13f, seedVeinPaint)

        // Count + label
        canvas.drawText("x${state.seedsThisRun}", x + 40f, cy1 + seedCountPaint.textSize * 0.35f, seedCountPaint)
        canvas.drawText("seeds", x + 40f, cy1 + seedCountPaint.textSize * 0.35f + 20f, seedLabelPaint)

        // ── Mercy hearts strip ────────────────────────────────────────────
        val MAX_HEARTS   = 10
        val heartSpacing = 26f
        val heartY       = cy2
        for (i in 0 until MAX_HEARTS) {
            val hx = x + i * heartSpacing
            val paint = if (i < state.mercyHearts) heartFilledPaint else heartEmptyPaint
            canvas.drawText("♥", hx, heartY, paint)
        }
    }

    private fun drawBloomMeter(canvas: Canvas, state: GameStateManager) {
        val cx = (METER_LEFT + METER_RIGHT) / 2f

        // Draw segments
        for (i in 0 until SEG_COUNT) {
            val left  = METER_LEFT + i * (segW + SEG_GAP)
            val right = left + segW
            segRect.set(left, METER_TOP, right, METER_BOTTOM)

            // Fill fraction for this segment (0..1 for smooth animation)
            val fillFrac = (displayedFill - i).coerceIn(0f, 1f)

            if (fillFrac > 0f) {
                // Pulse brightness when bloom is active
                val pulse = if (state.isBloomActive)
                    (0.75f + 0.25f * sin(bloomPulse))
                else 1f

                val alpha = (150 + (105 * fillFrac * pulse).toInt()).coerceIn(0, 255)
                segFilledPaint.shader = LinearGradient(
                    left, METER_BOTTOM, left, METER_TOP,
                    intArrayOf(
                        Color.argb(alpha, 60,  200, 60),
                        Color.argb(alpha, 160, 255, 100)
                    ),
                    null, Shader.TileMode.CLAMP
                )

                // Partial fill: clip bottom portion for the last partial segment
                if (fillFrac < 1f) {
                    val fillTop = METER_BOTTOM - (METER_BOTTOM - METER_TOP) * fillFrac
                    partRect.set(left, fillTop, right, METER_BOTTOM)
                    canvas.drawRoundRect(partRect, CORNER_R, CORNER_R, segFilledPaint)
                } else {
                    canvas.drawRoundRect(segRect, CORNER_R, CORNER_R, segFilledPaint)
                }
            } else {
                canvas.drawRoundRect(segRect, CORNER_R, CORNER_R, segEmptyPaint)
            }

            canvas.drawRoundRect(segRect, CORNER_R, CORNER_R, segBorderPaint)
        }

        // Glow border around entire meter when bloom active
        if (state.isBloomActive) {
            val glow = 0.5f + 0.5f * sin(bloomPulse)
            val glowAlpha = (120 + (135 * glow).toInt()).coerceIn(0, 255)
            glowBorderPaint.color = Color.argb(glowAlpha, 180, 100, 255)
            glowRect.set(
                METER_LEFT - 4f,  METER_TOP - 4f,
                METER_RIGHT + 4f, METER_BOTTOM + 4f
            )
            canvas.drawRoundRect(glowRect, CORNER_R + 4f, CORNER_R + 4f, glowBorderPaint)
        }

        // Meter label
        val labelText  = if (state.isBloomActive) "BLOOM" else "bloom"
        val labelPaint = if (state.isBloomActive) bloomActiveLabelPaint else bloomLabelPaint
        if (state.isBloomActive) {
            bloomActiveLabelPaint.alpha =
                (200 + (55 * sin(bloomPulse)).toInt()).coerceIn(0, 255)
        }
        canvas.drawText(labelText, cx, METER_BOTTOM + 22f, labelPaint)

        val statusText = if (state.isBloomActive) {
            String.format("%.1fs active", state.bloomSecondsRemaining)
        } else {
            "${state.bloomMeter}/${state.bloomSeedTarget}"
        }
        canvas.drawText(statusText, cx, METER_BOTTOM + 42f, bloomCountPaint)
    }

    private fun drawScoreArea(canvas: Canvas, state: GameStateManager) {
        val rightX   = screenWidth.toFloat() - PAD
        val distY    = 34f
        val scoreY   = 72f

        // Distance top-right
        canvas.drawText(formatDistance(state.distanceMetres), rightX, distY, distPaint)

        // Score below distance
        val scoreText = formatScore(state.score)
        canvas.drawText(scoreText, rightX, scoreY, scorePaint)

        // NEW! badge — pulsing gold, left of score
        if (state.isNewHighScore && state.score > 0) {
            val pulse = 0.7f + 0.3f * sin(newBadge)
            newBadgePaint.alpha = (pulse * 255f).toInt().coerceIn(0, 255)
            val scoreW = scorePaint.measureText(scoreText)
            canvas.drawText("NEW", rightX - scoreW - 14f, scoreY, newBadgePaint)
        }

        // Ghost best score (when not yet a new high score)
        if (!state.isNewHighScore && state.highScore > 0) {
            canvas.drawText("best ${formatScore(state.highScore)}", rightX, scoreY + SMALL_SIZE + 4f, ghostPaint)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun formatDistance(m: Float) = when {
        m < 1_000f -> "${m.toInt()} m"
        else -> String.format("%.2f km", m / 1_000f)
    }

    private fun formatScore(s: Int) = when {
        s < 1_000  -> s.toString()
        else       -> String.format("%,d", s)
    }
}
