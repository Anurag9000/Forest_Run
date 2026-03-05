package com.yourname.forest_run.systems

import android.graphics.Color

/**
 * A single particle instance.
 *
 * Entirely value-type — the [ParticleManager] pool recycles these so no GC
 * pressure occurs mid-run. Reset via [reset] before re-use.
 */
data class Particle(
    // ── Position & motion ─────────────────────────────────────────────────
    var x: Float = 0f,
    var y: Float = 0f,
    var velX: Float = 0f,
    var velY: Float = 0f,
    /** Gravity applied per second squared (positive = fall). 0 = weightless. */
    var gravity: Float = 0f,
    /** Drag multiplier applied each frame: velocity *= drag^deltaTime. */
    var drag: Float = 0.92f,

    // ── Lifecycle ─────────────────────────────────────────────────────────
    var lifetime: Float  = 1f,  // total seconds this particle lives
    var elapsed:  Float  = 0f,  // seconds since birth

    // ── Appearance ────────────────────────────────────────────────────────
    var startColor: Int  = Color.WHITE,
    var endColor:   Int  = Color.TRANSPARENT,
    var startSize:  Float = 8f,
    var endSize:    Float = 0f,
    /** Shape: true = circle, false = square. */
    var isCircle:   Boolean = true,
    /** Rotation in degrees (square particles only). */
    var rotation:   Float = 0f,
    /** Degrees per second of spin (square particles only). */
    var spinRate:   Float = 0f,

    // ── State ─────────────────────────────────────────────────────────────
    var isActive: Boolean = false
) {
    /** Progress 0..1 (birth → death). */
    val progress: Float get() = (elapsed / lifetime).coerceIn(0f, 1f)
    val isDead:   Boolean get() = elapsed >= lifetime

    /**
     * Current interpolated colour.
     * Blends component-wise from [startColor] to [endColor] over lifetime.
     */
    val currentColor: Int get() {
        val t = progress
        val a = lerpInt(Color.alpha(startColor), Color.alpha(endColor), t)
        val r = lerpInt(Color.red(startColor),   Color.red(endColor),   t)
        val g = lerpInt(Color.green(startColor), Color.green(endColor), t)
        val b = lerpInt(Color.blue(startColor),  Color.blue(endColor),  t)
        return Color.argb(a, r, g, b)
    }

    /** Current interpolated size. */
    val currentSize: Float get() = startSize + (endSize - startSize) * progress

    // ── Update ────────────────────────────────────────────────────────────

    fun update(deltaTime: Float) {
        if (!isActive || isDead) return

        elapsed += deltaTime

        // Drag
        val d = Math.pow(drag.toDouble(), deltaTime.toDouble()).toFloat()
        velX *= d
        velY *= d

        // Gravity
        velY += gravity * deltaTime

        // Move
        x += velX * deltaTime
        y += velY * deltaTime

        // Spin (square particles)
        rotation += spinRate * deltaTime
    }

    /** Re-initialise this instance for reuse from the pool. */
    fun reset() {
        x = 0f; y = 0f; velX = 0f; velY = 0f
        gravity = 0f; drag = 0.92f
        lifetime = 1f; elapsed = 0f
        startColor = Color.WHITE; endColor = Color.TRANSPARENT
        startSize = 8f; endSize = 0f
        isCircle = true; rotation = 0f; spinRate = 0f
        isActive = false
    }

    private fun lerpInt(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt().coerceIn(0, 255)
}
