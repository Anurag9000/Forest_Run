package com.yourname.forest_run.utils

import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight math helpers used throughout the game.
 * All functions are pure – no Android imports.
 */
object MathUtils {

    /**
     * Linear interpolation between [a] and [b] by factor [t] (0..1).
     * If t is outside 0..1 the result extrapolates.
     */
    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /**
     * Clamp [value] so it is never less than [lo] or greater than [hi].
     */
    fun clamp(value: Float, lo: Float, hi: Float): Float = max(lo, min(hi, value))

    /**
     * Clamp to 0..1.
     */
    fun clamp01(value: Float): Float = clamp(value, 0f, 1f)

    /**
     * Normalise [value] from the range [inMin]..[inMax] to 0..1.
     * Returns 0 if the input range is zero to avoid division by zero.
     */
    fun normalise(value: Float, inMin: Float, inMax: Float): Float {
        val range = inMax - inMin
        return if (range == 0f) 0f else clamp01((value - inMin) / range)
    }

    /**
     * Map [value] from input range to output range.
     */
    fun map(
        value: Float,
        inMin: Float, inMax: Float,
        outMin: Float, outMax: Float
    ): Float = lerp(outMin, outMax, normalise(value, inMin, inMax))

    /**
     * Smooth-step (S-curve) version of [lerp] — ease in/out between a and b.
     */
    fun smoothStep(a: Float, b: Float, t: Float): Float {
        val s = clamp01(t)
        val smooth = s * s * (3f - 2f * s)
        return lerp(a, b, smooth)
    }

    /**
     * Returns sine of [angleDegrees].
     */
    fun sinDeg(angleDegrees: Float): Float = Math.sin(Math.toRadians(angleDegrees.toDouble())).toFloat()

    /**
     * Returns cosine of [angleDegrees].
     */
    fun cosDeg(angleDegrees: Float): Float = Math.cos(Math.toRadians(angleDegrees.toDouble())).toFloat()

    /**
     * Convert dp to pixel using the provided [density].
     */
    fun dpToPx(dp: Float, density: Float): Float = dp * density

    /**
     * Returns the sign of [value]: -1, 0, or 1.
     */
    fun sign(value: Float): Float = when {
        value > 0f -> 1f
        value < 0f -> -1f
        else -> 0f
    }
}
