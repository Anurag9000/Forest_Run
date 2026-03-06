package com.yourname.forest_run.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.yourname.forest_run.utils.MathUtils

/**
 * Manages floating dialogue / flavour text overlays.
 *
 * Fully implemented in Phase 16:
 *  - PressStart2P pixel font (loaded once from assets).
 *  - Drop-shadow offset for legibility on all backgrounds.
 *  - Per-entry colour, size, and lifetime.
 *  - Fast-appear / slow-fade lifecycle: text pops in at full size, then
 *    shrinks and fades as it rises.
 *
 * Call from GameView (inside CameraSystem.applyTo block):
 *   FlavorTextManager.update(deltaTime)   // in update()
 *   FlavorTextManager.draw(canvas)        // in draw(), world-space layer
 */
object FlavorTextManager {

    // ── Configuration ────────────────────────────────────────────────────
    private const val FLOAT_SPEED  = 55f     // px/s upward drift
    private const val SHADOW_DX    = 2f
    private const val SHADOW_DY    = 2f
    private const val FADE_START   = 0.55f   // fraction of lifetime where fade begins

    // ── Font (loaded lazily on first spawn) ──────────────────────────────
    private var pixelFont: Typeface? = null

    /** Call once from GameView.surfaceCreated() so the font loads before first spawn. */
    fun init(context: Context) {
        pixelFont = runCatching {
            Typeface.createFromAsset(context.assets, "fonts/PressStart2P-Regular.ttf")
        }.getOrNull()
    }

    // ── Data class ────────────────────────────────────────────────────────

    data class FlavorText(
        val text:     String,
        var x:        Float,
        var y:        Float,
        val colour:   Int   = Color.WHITE,
        val lifetime: Float = 1.4f,
        val baseSize: Float = 30f,
        var elapsed:  Float = 0f
    ) {
        val progress: Float get() = (elapsed / lifetime).coerceIn(0f, 1f)

        /** 0..1 opacity — full until FADE_START, then linearly drops to 0. */
        val alpha: Int get() {
            val fade = MathUtils.normalise(progress, FADE_START, 1f)
            return ((1f - fade) * 255f).toInt().coerceIn(0, 255)
        }

        /** Slight pop-in on birth then steady. */
        val currentSize: Float get() {
            val popIn = MathUtils.normalise(1f - progress, 0.7f, 1.0f) // slight shrink over lifetime
            return (baseSize * (0.85f + 0.15f * popIn))
        }

        val isDead: Boolean get() = elapsed >= lifetime
    }

    // ── Pool ──────────────────────────────────────────────────────────────
    private val active = mutableListOf<FlavorText>()

    // ── Paints (created once, reused) ─────────────────────────────────────
    private val textPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 0, 0, 0)
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Spawn a floating text at world position (x, y).
     *
     * @param text      The string to display (keep short — 1-4 words).
     * @param x         World X (left edge of text anchor).
     * @param y         World Y (baseline of text).
     * @param colour    Integer ARGB.
     * @param lifetime  Seconds before the text fully fades.
     * @param size      Base text size in px (default 30).
     */
    fun spawn(
        text:     String,
        x:        Float,
        y:        Float,
        colour:   Int   = Color.WHITE,
        lifetime: Float = 1.4f,
        size:     Float = 30f
    ) {
        active.add(FlavorText(text, x, y, colour, lifetime, size))
    }

    /** Advance all active texts. */
    fun update(deltaTime: Float) {
        val iter = active.iterator()
        while (iter.hasNext()) {
            val ft = iter.next()
            ft.elapsed += deltaTime
            ft.y -= FLOAT_SPEED * deltaTime
            if (ft.isDead) iter.remove()
        }
    }

    /** Draw all active texts. Must be called inside CameraSystem.applyTo(). */
    fun draw(canvas: Canvas) {
        val font = pixelFont ?: Typeface.MONOSPACE
        for (ft in active) {
            val size  = ft.currentSize
            val alpha = ft.alpha
            if (alpha <= 0) continue

            textPaint.typeface = font
            textPaint.textSize = size

            // Drop shadow
            shadowPaint.textSize = size
            shadowPaint.alpha    = (alpha * 0.6f).toInt().coerceIn(0, 255)
            canvas.drawText(ft.text, ft.x + SHADOW_DX, ft.y + SHADOW_DY, shadowPaint)

            // Main text
            textPaint.color = ft.colour
            textPaint.alpha = alpha
            canvas.drawText(ft.text, ft.x, ft.y, textPaint)
        }
    }

    /** Clear all active entries on run reset. */
    fun clear() = active.clear()
}
