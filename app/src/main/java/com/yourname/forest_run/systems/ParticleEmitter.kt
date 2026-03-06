package com.yourname.forest_run.systems

import android.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Describes a burst or continuous stream of particles.
 *
 * An emitter does NOT own particles — it is a configuration + spawn helper
 * that submits particle config to [ParticleManager]. This keeps management
 * centralised in the pool.
 *
 * Use the builder DSL via [ParticleManager.emit].
 */
class ParticleEmitter(
    var x: Float,
    var y: Float,

    // ── Emission mode ──────────────────────────────────────────────────────
    /** true = all particles at once then done; false = continuous until [stop] */
    val isBurst: Boolean = true,
    /** Number of particles per burst, or per second in continuous mode. */
    val count: Int = 8,

    // ── Spread ────────────────────────────────────────────────────────────
    /** Angle of travel in degrees (0 = right, 90 = down, 270 = up). */
    val angleMin: Float = 200f,
    val angleMax: Float = 340f,

    // ── Speed ─────────────────────────────────────────────────────────────
    val speedMin: Float = 80f,
    val speedMax: Float = 220f,

    // ── Particle shape ────────────────────────────────────────────────────
    val startColor: Int  = Color.WHITE,
    val endColor:   Int  = Color.TRANSPARENT,
    val startSize:  Float = 8f,
    val endSize:    Float = 0f,
    val isCircle:   Boolean = true,
    val spinRateMin: Float = 0f,
    val spinRateMax: Float = 0f,

    // ── Physics ───────────────────────────────────────────────────────────
    val gravity:  Float = 800f,
    val drag:     Float = 0.88f,

    // ── Lifetime ──────────────────────────────────────────────────────────
    val lifetimeMin: Float = 0.4f,
    val lifetimeMax: Float = 0.9f,

    // ── Position jitter ───────────────────────────────────────────────────
    val spawnRadiusX: Float = 0f,
    val spawnRadiusY: Float = 0f
) {
    private var continuousTimer = 0f
    private var continuousActive = !isBurst

    /** Fill [particle] with randomised values from this emitter's config. */
    fun configure(particle: Particle) {
        val angleRad = (angleMin + Random.nextFloat() * (angleMax - angleMin)) * kotlin.math.PI.toFloat() / 180f
        val speed  = speedMin + Random.nextFloat() * (speedMax - speedMin)

        particle.x          = x + (Random.nextFloat() - 0.5f) * 2f * spawnRadiusX
        particle.y          = y + (Random.nextFloat() - 0.5f) * 2f * spawnRadiusY
        particle.velX       = cos(angleRad) * speed
        particle.velY       = sin(angleRad) * speed
        particle.gravity    = gravity
        particle.drag       = drag
        particle.lifetime   = lifetimeMin + Random.nextFloat() * (lifetimeMax - lifetimeMin)
        particle.elapsed    = 0f
        particle.startColor = startColor
        particle.endColor   = endColor
        particle.startSize  = startSize
        particle.endSize    = endSize
        particle.isCircle   = isCircle
        particle.spinRate   = spinRateMin + Random.nextFloat() * (spinRateMax - spinRateMin)
        particle.rotation   = Random.nextFloat() * 360f
        particle.isActive   = true
    }

    /**
     * Call every frame for continuous emitters.
     * Returns the number of new particles to spawn this frame.
     */
    fun updateContinuous(deltaTime: Float): Int {
        if (!continuousActive || isBurst) return 0
        continuousTimer += deltaTime
        val interval = 1f / count
        var spawned = 0
        while (continuousTimer >= interval) {
            continuousTimer -= interval
            spawned++
        }
        return spawned
    }

    fun stop() { continuousActive = false }
    fun resume() { continuousActive = true }
}
