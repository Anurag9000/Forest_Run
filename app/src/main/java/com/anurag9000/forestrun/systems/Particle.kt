package com.anurag9000.forestrun.systems

import android.graphics.Color
import com.anurag9000.forestrun.engine.FeedbackSettings
import kotlin.math.pow

/** A pooled particle value object with fail-closed physics and appearance. */
data class Particle(
    var x: Float = 0f,
    var y: Float = 0f,
    var velX: Float = 0f,
    var velY: Float = 0f,
    var gravity: Float = 0f,
    var drag: Float = 0.92f,
    var lifetime: Float = 1f,
    var elapsed: Float = 0f,
    var startColor: Int = Color.WHITE,
    var endColor: Int = Color.TRANSPARENT,
    var startSize: Float = 8f,
    var endSize: Float = 0f,
    var isCircle: Boolean = true,
    var rotation: Float = 0f,
    var spinRate: Float = 0f,
    var isActive: Boolean = false
) {
    /**
     * Pool acquisition calls [reset] on the game thread immediately before an
     * emitter configures this particle. Remembering the comfort setting at that
     * boundary lets a later reduced-motion toggle retire only already-active
     * full-motion particles without mutating the pool from the UI thread.
     */
    private var reducedMotionAtBirth: Boolean = FeedbackSettings.reducedMotion

    val progress: Float
        get() = when {
            !lifetime.isFinite() || lifetime <= 0f -> 1f
            !elapsed.isFinite() -> 1f
            elapsed <= 0f -> 0f
            else -> (elapsed / lifetime).coerceIn(0f, 1f)
        }

    val isDead: Boolean
        get() = !lifetime.isFinite() || lifetime <= 0f ||
            !elapsed.isFinite() || elapsed >= lifetime

    val currentColor: Int
        get() {
            val t = progress
            val a = lerpInt(Color.alpha(startColor), Color.alpha(endColor), t)
            val r = lerpInt(Color.red(startColor), Color.red(endColor), t)
            val g = lerpInt(Color.green(startColor), Color.green(endColor), t)
            val b = lerpInt(Color.blue(startColor), Color.blue(endColor), t)
            return Color.argb(a, r, g, b)
        }

    val currentSize: Float
        get() {
            val interpolated = startSize.toDouble() +
                (endSize.toDouble() - startSize.toDouble()) * progress.toDouble()
            return if (interpolated.isFinite()) {
                interpolated.coerceIn(0.0, Float.MAX_VALUE.toDouble()).toFloat()
            } else {
                0f
            }
        }

    fun update(deltaTime: Float) {
        if (!isActive || isDead) return
        if (FeedbackSettings.reducedMotion && !reducedMotionAtBirth) {
            isActive = false
            return
        }
        if (!deltaTime.isFinite() || deltaTime <= 0f) return
        if (!hasFiniteKinematics()) {
            isActive = false
            return
        }

        elapsed = (elapsed.toDouble() + deltaTime.toDouble())
            .coerceAtMost(Float.MAX_VALUE.toDouble())
            .toFloat()
        if (isDead) {
            elapsed = lifetime
            return
        }

        val safeDrag = drag.takeIf { it.isFinite() && it in 0f..1f } ?: 1f
        val dragFactor = safeDrag.pow(deltaTime)
        velX = finiteCoordinate(velX.toDouble() * dragFactor.toDouble())
        velY = finiteCoordinate(velY.toDouble() * dragFactor.toDouble())

        val safeGravity = gravity.takeIf { it.isFinite() } ?: 0f
        velY = finiteCoordinate(velY.toDouble() + safeGravity.toDouble() * deltaTime.toDouble())
        x = finiteCoordinate(x.toDouble() + velX.toDouble() * deltaTime.toDouble())
        y = finiteCoordinate(y.toDouble() + velY.toDouble() * deltaTime.toDouble())

        val safeSpin = spinRate.takeIf { it.isFinite() } ?: 0f
        val nextRotation = rotation.toDouble() + safeSpin.toDouble() * deltaTime.toDouble()
        rotation = if (nextRotation.isFinite()) {
            (nextRotation % 360.0).toFloat()
        } else {
            0f
        }
    }

    fun reset() {
        x = 0f
        y = 0f
        velX = 0f
        velY = 0f
        gravity = 0f
        drag = 0.92f
        lifetime = 1f
        elapsed = 0f
        startColor = Color.WHITE
        endColor = Color.TRANSPARENT
        startSize = 8f
        endSize = 0f
        isCircle = true
        rotation = 0f
        spinRate = 0f
        isActive = false
        reducedMotionAtBirth = FeedbackSettings.reducedMotion
    }

    private fun hasFiniteKinematics(): Boolean =
        x.isFinite() && y.isFinite() && velX.isFinite() && velY.isFinite() && rotation.isFinite()

    private fun finiteCoordinate(value: Double): Float =
        value.coerceIn(-Float.MAX_VALUE.toDouble(), Float.MAX_VALUE.toDouble()).toFloat()

    private fun lerpInt(a: Int, b: Int, t: Float): Int =
        (a + (b - a) * t).toInt().coerceIn(0, 255)
}
