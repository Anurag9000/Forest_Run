package com.yourname.forest_run.engine

import android.graphics.Canvas
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Trauma-based screen shake system.
 *
 * Inspired by the "Game Feel" trauma + decay model:
 *   - [addTrauma] adds 0..1 trauma. Multiple simultaneous hits compound.
 *   - Every frame, trauma decays exponentially.
 *   - Shake displacement = trauma² × maxOffset (quadratic gives smoother envelope).
 *   - Two independent noise streams (X and Y) give a natural, non-repetitive shake.
 *
 * Usage:
 *   CameraSystem.addTrauma(0.8f)             // heavy hit
 *   CameraSystem.update(deltaTime)           // called first in GameView.update()
 *   canvas.translate(CameraSystem.offsetX, CameraSystem.offsetY)  // before drawing
 *   canvas.translate(-CameraSystem.offsetX, -CameraSystem.offsetY) // after drawing
 *
 * GameView wraps all gameplay drawing (not the HUD) with the camera offset.
 */
object CameraSystem {

    // ── Tuning ────────────────────────────────────────────────────────────
    private const val TRAUMA_DECAY  = 2.2f   // units/second (higher = snappier recovery)
    private const val MAX_OFFSET_X  = 22f    // px – maximum horizontal displacement
    private const val MAX_OFFSET_Y  = 14f    // px – maximum vertical displacement
    private const val SHAKE_FREQ    = 18f    // Hz – oscillation frequency (samples/sec)

    // ── State ─────────────────────────────────────────────────────────────
    private var trauma: Float = 0f           // 0..1

    /** Current X displacement in pixels. Apply to canvas.translate() before drawing. */
    var offsetX: Float = 0f
        private set

    /** Current Y displacement in pixels. Apply to canvas.translate() before drawing. */
    var offsetY: Float = 0f
        private set

    /** Time accumulator used to drive the sine-noise shake pattern. */
    private var shakeTime: Float = 0f

    /** Phase offsets ensure X and Y don't look identical. */
    private var phaseX: Float = 0f
    private var phaseY: Float = 0.5f   // half-cycle offset

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Add trauma. Clamps total trauma to [0..1].
     *
     * Predefined trauma amounts (call these from GameView collision handler):
     * - 0.9f — HIT / game over
     * - 0.5f — Heavy landing after full-height jump
     * - 0.35f — Wolf howl shockwave
     * - 0.25f — Eagle dive near-miss
     * - 0.15f — MERCY_MISS hit feedback
     */
    fun addTrauma(amount: Float) {
        trauma = (trauma + amount).coerceIn(0f, 1f)
        // Randomise phase so repeated rapid hits vary the shake pattern
        phaseX = Random.nextFloat() * 6.28f
        phaseY = Random.nextFloat() * 6.28f
    }

    /** Reset to zero shake — call on run start/reset. */
    fun reset() {
        trauma   = 0f
        offsetX  = 0f
        offsetY  = 0f
        shakeTime = 0f
    }

    /**
     * Advance the camera shake. Call every frame BEFORE translating the canvas.
     */
    fun update(deltaTime: Float) {
        if (trauma <= 0f) {
            offsetX = 0f; offsetY = 0f
            return
        }

        // Decay trauma exponentially
        trauma = (trauma - TRAUMA_DECAY * deltaTime).coerceAtLeast(0f)
        shakeTime += deltaTime

        // Quadratic shake amplitude: shake = trauma²
        val shake = trauma * trauma

        // Two-channel sine noise (cheap but natural-feeling)
        offsetX = shake * MAX_OFFSET_X * sin(shakeTime * SHAKE_FREQ * 6.28f + phaseX)
        offsetY = shake * MAX_OFFSET_Y * sin(shakeTime * SHAKE_FREQ * 6.28f + phaseY)
    }

    /**
     * Apply the camera offset to [canvas] for the duration of [block], then restore.
     * Use this to wrap all gameplay drawing (background, entities, player, particles).
     * The HUD must NOT be inside this block.
     */
    inline fun applyTo(canvas: Canvas, block: () -> Unit) {
        if (offsetX == 0f && offsetY == 0f) {
            block()
            return
        }
        canvas.save()
        canvas.translate(offsetX, offsetY)
        block()
        canvas.restore()
    }

    // ── Predefined shakes (named API so call-sites stay readable) ─────────

    /** Full death/collision shake. */
    fun shakeHit()        = addTrauma(0.90f)
    /** Hard landing from max-height jump. */
    fun shakeHeavyLand()  = addTrauma(0.50f)
    /** Wolf howl shockwave. */
    fun shakeWolfHowl()   = addTrauma(0.35f)
    /** Eagle dive near-miss. */
    fun shakeEagle()      = addTrauma(0.25f)
    /** Near-miss feedback. */
    fun shakeMercyMiss()  = addTrauma(0.15f)
}
