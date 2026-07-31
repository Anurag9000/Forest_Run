package com.anurag9000.forestrun.systems

import android.graphics.Color
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/** Configuration and spawn helper for the fixed-capacity particle pool. */
class ParticleEmitter(
    x: Float,
    y: Float,
    val isBurst: Boolean = true,
    val count: Int = 8,
    val angleMin: Float = 200f,
    val angleMax: Float = 340f,
    val speedMin: Float = 80f,
    val speedMax: Float = 220f,
    val startColor: Int = Color.WHITE,
    val endColor: Int = Color.TRANSPARENT,
    val startSize: Float = 8f,
    val endSize: Float = 0f,
    val isCircle: Boolean = true,
    val spinRateMin: Float = 0f,
    val spinRateMax: Float = 0f,
    val gravity: Float = 800f,
    val drag: Float = 0.88f,
    val lifetimeMin: Float = 0.4f,
    val lifetimeMax: Float = 0.9f,
    val spawnRadiusX: Float = 0f,
    val spawnRadiusY: Float = 0f
) {
    companion object {
        const val MAX_CONFIGURED_COUNT = 512
        const val MAX_CONTINUOUS_SPAWN_PER_UPDATE = 512
        private const val EMISSION_EPSILON_PARTICLES = 1e-6
    }

    var x: Float = x
        set(value) {
            if (value.isFinite()) field = value
        }
    var y: Float = y
        set(value) {
            if (value.isFinite()) field = value
        }

    /** Fractional seconds retained toward the next continuous particle. */
    private var continuousTimer = 0.0
    private var continuousActive = !isBurst

    init {
        require(x.isFinite() && y.isFinite()) { "Particle emitter origin must be finite." }
        require(count in 1..MAX_CONFIGURED_COUNT) {
            "Particle emitter count must be between 1 and $MAX_CONFIGURED_COUNT."
        }
        require(angleMin.isFinite() && angleMax.isFinite()) { "Particle angles must be finite." }
        require(speedMin.isFinite() && speedMax.isFinite() && speedMin >= 0f && speedMax >= speedMin) {
            "Particle speed range must be finite, non-negative, and ordered."
        }
        require(startSize.isFinite() && endSize.isFinite() && startSize >= 0f && endSize >= 0f) {
            "Particle sizes must be finite and non-negative."
        }
        require(spinRateMin.isFinite() && spinRateMax.isFinite() && spinRateMax >= spinRateMin) {
            "Particle spin range must be finite and ordered."
        }
        require(gravity.isFinite()) { "Particle gravity must be finite." }
        require(drag.isFinite() && drag in 0f..1f) { "Particle drag must be between 0 and 1." }
        require(
            lifetimeMin.isFinite() && lifetimeMax.isFinite() &&
                lifetimeMin > 0f && lifetimeMax >= lifetimeMin
        ) {
            "Particle lifetime range must be finite, positive, and ordered."
        }
        require(
            spawnRadiusX.isFinite() && spawnRadiusY.isFinite() &&
                spawnRadiusX >= 0f && spawnRadiusY >= 0f
        ) {
            "Particle spawn radii must be finite and non-negative."
        }
    }

    /** Fill [particle] with randomized, validated values from this emitter. */
    fun configure(particle: Particle) {
        val angle = angleMin + Random.nextFloat() * (angleMax - angleMin)
        val angleRad = angle * kotlin.math.PI.toFloat() / 180f
        val speed = speedMin + Random.nextFloat() * (speedMax - speedMin)
        val jitterX = (Random.nextFloat() - 0.5f) * 2f * spawnRadiusX
        val jitterY = (Random.nextFloat() - 0.5f) * 2f * spawnRadiusY

        particle.x = finiteCoordinate(x.toDouble() + jitterX.toDouble())
        particle.y = finiteCoordinate(y.toDouble() + jitterY.toDouble())
        particle.velX = finiteCoordinate(cos(angleRad).toDouble() * speed.toDouble())
        particle.velY = finiteCoordinate(sin(angleRad).toDouble() * speed.toDouble())
        particle.gravity = gravity
        particle.drag = drag
        particle.lifetime = lifetimeMin + Random.nextFloat() * (lifetimeMax - lifetimeMin)
        particle.elapsed = 0f
        particle.startColor = startColor
        particle.endColor = endColor
        particle.startSize = startSize
        particle.endSize = endSize
        particle.isCircle = isCircle
        particle.spinRate = spinRateMin + Random.nextFloat() * (spinRateMax - spinRateMin)
        particle.rotation = Random.nextFloat() * 360f
        particle.isActive = true
    }

    /**
     * Advance a continuous emitter in O(1). Excess catch-up particles are
     * deliberately dropped rather than creating a multi-frame backlog.
     */
    fun updateContinuous(deltaTime: Float): Int {
        if (!continuousActive || isBurst || !deltaTime.isFinite() || deltaTime <= 0f) return 0

        val accumulatedSeconds = (continuousTimer + deltaTime.toDouble())
            .coerceAtMost(Float.MAX_VALUE.toDouble())
        val particleCredit = accumulatedSeconds * count.toDouble()
        val due = floor(particleCredit + EMISSION_EPSILON_PARTICLES)
        val wholeDue = due.coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()

        // Preserve only the fractional credit. If catch-up was capped, the
        // unbounded whole-particle backlog is intentionally discarded.
        val fractionalCredit = (particleCredit - due).coerceIn(0.0, 1.0)
        continuousTimer = fractionalCredit / count.toDouble()
        return wholeDue.coerceAtMost(MAX_CONTINUOUS_SPAWN_PER_UPDATE)
    }

    fun stop() {
        continuousActive = false
    }

    fun resume() {
        continuousActive = true
    }

    private fun finiteCoordinate(value: Double): Float =
        value.coerceIn(-Float.MAX_VALUE.toDouble(), Float.MAX_VALUE.toDouble()).toFloat()
}
