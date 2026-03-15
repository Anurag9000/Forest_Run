package com.yourname.forest_run.systems

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Central particle engine — singleton.
 *
 * Maintains a fixed-capacity object pool ([MAX_PARTICLES]) so no heap
 * allocation ever occurs during gameplay. All active particles are updated
 * and drawn every frame.
 *
 * Usage:
 *   ParticleManager.emit(jumpDustEmitter)     // burst at emitter.x/y
 *   ParticleManager.update(deltaTime)         // every frame in GameView.update()
 *   ParticleManager.draw(canvas)              // every frame in GameView.draw()
 *   ParticleManager.clear()                   // on run reset
 */
object ParticleManager {

    private const val MAX_PARTICLES = 512

    private val pool = Array(MAX_PARTICLES) { Particle() }
    private var poolHead = 0  // next candidate in circular scan

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val squarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val squareRect = RectF()

    // ── Continuous emitters ─────────────────────────────────────────────────
    // Entities register continuous emitters here; manager updates them each frame.
    private val continuousEmitters = mutableListOf<ParticleEmitter>()

    // ── Update ────────────────────────────────────────────────────────────

    fun update(deltaTime: Float) {
        // Update continuous emitters
        for (emitter in continuousEmitters) {
            val n = emitter.updateContinuous(deltaTime)
            repeat(n) { emit(emitter) }
        }

        // Update all particles
        for (p in pool) {
            if (p.isActive) {
                p.update(deltaTime)
                if (p.isDead) p.isActive = false
            }
        }
    }

    // ── Draw ──────────────────────────────────────────────────────────────

    fun draw(canvas: Canvas) {
        for (p in pool) {
            if (!p.isActive) continue

            val col  = p.currentColor
            val size = p.currentSize

            if (p.isCircle) {
                circlePaint.color = col
                canvas.drawCircle(p.x, p.y, size, circlePaint)
            } else {
                squarePaint.color = col
                squareRect.set(p.x - size, p.y - size, p.x + size, p.y + size)
                val save = canvas.save()
                canvas.rotate(p.rotation, p.x, p.y)
                canvas.drawRect(squareRect, squarePaint)
                canvas.restoreToCount(save)
            }
        }
    }

    // ── Emit helpers ──────────────────────────────────────────────────────

    /**
     * Emit a burst from an emitter at its current x/y.
     * Finds [emitter.count] free slots from the pool and initialises them.
     */
    fun emit(emitter: ParticleEmitter) {
        repeat(emitter.count) {
            val p = acquireParticle() ?: return@repeat
            emitter.configure(p)
        }
    }

    /** Emit a burst from a named preset at screen position (x, y). */
    fun emit(preset: FxPreset, x: Float, y: Float) {
        val emitter = preset.build(x, y)
        emit(emitter)
    }

    /** Register a continuous emitter (e.g. Bloom aura). Returns a handle to stop it. */
    fun addContinuous(emitter: ParticleEmitter): ParticleEmitter {
        continuousEmitters.add(emitter)
        return emitter
    }

    /** Remove a continuous emitter. */
    fun removeContinuous(emitter: ParticleEmitter) {
        continuousEmitters.remove(emitter)
    }

    /** Clear all active particles and continuous emitters (call on run reset). */
    fun clear() {
        for (p in pool) p.isActive = false
        continuousEmitters.clear()
        poolHead = 0
    }

    // ── Pool management ────────────────────────────────────────────────────

    private fun acquireParticle(): Particle? {
        // Circular scan for an inactive slot
        val start = poolHead
        do {
            val p = pool[poolHead]
            poolHead = (poolHead + 1) % MAX_PARTICLES
            if (!p.isActive) {
                p.reset()
                return p
            }
        } while (poolHead != start)
        // Pool exhausted — silently skip (no crash, no GC)
        return null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FX Presets — every named particle effect in the game
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Predefined particle effect configurations.
 * Each preset builds a [ParticleEmitter] ready to burst from (x, y).
 *
 * Add new effects here as the game grows.
 */
enum class FxPreset {

    // ── Player ───────────────────────────────────────────────────────────

    /** Small dust puff when player leaves ground on jump. */
    JUMP_DUST {
        override fun build(x: Float, y: Float) = ParticleEmitter(
            x = x, y = y,
            isBurst = true, count = 6,
            angleMin = 190f, angleMax = 350f,
            speedMin = 60f, speedMax = 200f,
            startColor = Color.argb(200, 200, 180, 140),
            endColor   = Color.TRANSPARENT,
            startSize = 7f, endSize = 0f,
            gravity = 300f, drag = 0.85f,
            lifetimeMin = 0.25f, lifetimeMax = 0.45f,
            spawnRadiusX = 20f, spawnRadiusY = 4f
        )
    },

    /** Heavy dirt clod burst on landing. */
    LAND_THUD {
        override fun build(x: Float, y: Float) = ParticleEmitter(
            x = x, y = y,
            isBurst = true, count = 12,
            angleMin = 200f, angleMax = 340f,
            speedMin = 80f,  speedMax = 280f,
            startColor = Color.argb(220, 160, 130, 90),
            endColor   = Color.TRANSPARENT,
            startSize = 9f, endSize = 0f,
            gravity = 600f, drag = 0.80f,
            lifetimeMin = 0.3f, lifetimeMax = 0.6f,
            spawnRadiusX = 30f, spawnRadiusY = 4f
        )
    },

    /** Slide / duck ground scrape — low horizontal streak. */
    SLIDE_GRASS {
        override fun build(x: Float, y: Float) = ParticleEmitter(
            x = x, y = y,
            isBurst = true, count = 10,
            angleMin = 160f, angleMax = 200f,   // Mostly shooting backward-right
            speedMin = 100f, speedMax = 320f,
            startColor = Color.argb(220, 80, 160, 60),
            endColor   = Color.TRANSPARENT,
            startSize = 5f, endSize = 0f,
            gravity = 250f, drag = 0.82f,
            lifetimeMin = 0.2f, lifetimeMax = 0.4f,
            spawnRadiusX = 25f, spawnRadiusY = 3f
        )
    },

    // ── Flora ─────────────────────────────────────────────────────────────

    /** Pollen burst when player jumps over Hyacinth. */
    POLLEN_BURST {
        override fun build(x: Float, y: Float) = ParticleEmitter(
            x = x, y = y,
            isBurst = true, count = 14,
            angleMin = 210f, angleMax = 330f,
            speedMin = 50f, speedMax = 180f,
            startColor = Color.argb(220, 220, 160, 240),
            endColor   = Color.TRANSPARENT,
            startSize = 5f, endSize = 0f,
            gravity = 80f, drag = 0.94f,   // Light, drifts up and outward
            lifetimeMin = 0.6f, lifetimeMax = 1.2f,
            spawnRadiusX = 30f, spawnRadiusY = 20f
        )
    },

    /** Pink petals drifting from CherryBlossom/Jacaranda. Continuous. */
    PETAL_DRIFT {
        override fun build(x: Float, y: Float) = ParticleEmitter(
            x = x, y = y,
            isBurst = false, count = 3,    // 3 per second continuous
            angleMin = 80f, angleMax = 100f,  // Drift mostly downward
            speedMin = 20f, speedMax = 60f,
            startColor = Color.argb(200, 255, 180, 200),
            endColor   = Color.argb(0, 255, 180, 200),
            startSize = 6f, endSize = 2f,
            isCircle = false,   // Square petals tumble
            spinRateMin = 60f, spinRateMax = 200f,
            gravity = 40f, drag = 0.98f,
            lifetimeMin = 1.5f, lifetimeMax = 2.5f,
            spawnRadiusX = 60f, spawnRadiusY = 10f
        )
    },

    /** White sparkle wisps from Lily at Night. Continuous. */
    LILY_NIGHT_GLOW {
        override fun build(x: Float, y: Float) = ParticleEmitter(
            x = x, y = y,
            isBurst = false, count = 2,
            angleMin = 240f, angleMax = 300f,  // Float upward
            speedMin = 15f, speedMax = 50f,
            startColor = Color.argb(200, 220, 255, 255),
            endColor   = Color.TRANSPARENT,
            startSize = 4f, endSize = 0f,
            gravity = -20f,   // Negative gravity — floats up
            drag = 0.98f,
            lifetimeMin = 1.0f, lifetimeMax = 2.0f,
            spawnRadiusX = 12f, spawnRadiusY = 15f
        )
    },

    // ── Seeds ─────────────────────────────────────────────────────────────

    /** Golden sparkle when player collects a seed orb. */
    SEED_COLLECT {
        override fun build(x: Float, y: Float) = ParticleEmitter(
            x = x, y = y,
            isBurst = true, count = 10,
            angleMin = 0f, angleMax = 360f,   // Radial burst
            speedMin = 80f, speedMax = 250f,
            startColor = Color.argb(255, 255, 220, 60),
            endColor   = Color.TRANSPARENT,
            startSize = 8f, endSize = 1f,
            gravity = 200f, drag = 0.85f,
            lifetimeMin = 0.3f, lifetimeMax = 0.7f
        )
    },

    // ── Bloom ─────────────────────────────────────────────────────────────

    /** Continuous swirling aura around player during Bloom. */
    BLOOM_AURA {
        override fun build(x: Float, y: Float) = ParticleEmitter(
            x = x, y = y,
            isBurst = false, count = 8,
            angleMin = 0f, angleMax = 360f,
            speedMin = 30f, speedMax = 100f,
            startColor = Color.argb(200, 120, 255, 160),
            endColor   = Color.TRANSPARENT,
            startSize = 10f, endSize = 0f,
            gravity = -50f,    // Float up and out
            drag = 0.95f,
            lifetimeMin = 0.5f, lifetimeMax = 1.0f,
            spawnRadiusX = 30f, spawnRadiusY = 40f
        )
    },

    /** Burst when Bloom converts a passed encounter into reward. */
    BLOOM_CONVERT {
        override fun build(x: Float, y: Float) = ParticleEmitter(
            x = x, y = y,
            isBurst = true, count = 16,
            angleMin = 0f, angleMax = 360f,
            speedMin = 90f, speedMax = 260f,
            startColor = Color.argb(255, 255, 210, 110),
            endColor   = Color.TRANSPARENT,
            startSize = 11f, endSize = 0f,
            gravity = -30f, drag = 0.90f,
            lifetimeMin = 0.25f, lifetimeMax = 0.65f,
            spawnRadiusX = 18f, spawnRadiusY = 18f
        )
    },

    // ── Animal FX ─────────────────────────────────────────────────────────

    /** Wolf charge dust trail. Continuous — register on CHARGING transition. */
    WOLF_CHARGE_DUST {
        override fun build(x: Float, y: Float) = ParticleEmitter(
            x = x, y = y,
            isBurst = false, count = 6,
            angleMin = 0f, angleMax = 30f,   // Shoots backward/right from wolf
            speedMin = 150f, speedMax = 350f,
            startColor = Color.argb(200, 160, 130, 100),
            endColor   = Color.TRANSPARENT,
            startSize = 10f, endSize = 0f,
            gravity = 200f, drag = 0.80f,
            lifetimeMin = 0.2f, lifetimeMax = 0.4f,
            spawnRadiusX = 20f, spawnRadiusY = 5f
        )
    },

    // ── Collision FX ──────────────────────────────────────────────────────

    /** Green star burst on MERCY_MISS. */
    MERCY_STARS {
        override fun build(x: Float, y: Float) = ParticleEmitter(
            x = x, y = y,
            isBurst = true, count = 8,
            angleMin = 0f, angleMax = 360f,
            speedMin = 100f, speedMax = 300f,
            startColor = Color.argb(255, 60, 240, 80),
            endColor   = Color.TRANSPARENT,
            startSize = 7f, endSize = 0f,
            isCircle = false,
            spinRateMin = 90f, spinRateMax = 270f,
            gravity = 150f, drag = 0.85f,
            lifetimeMin = 0.3f, lifetimeMax = 0.6f
        )
    },

    /** Red impact burst on HIT (before REST). */
    HIT_BURST {
        override fun build(x: Float, y: Float) = ParticleEmitter(
            x = x, y = y,
            isBurst = true, count = 16,
            angleMin = 0f, angleMax = 360f,
            speedMin = 120f, speedMax = 400f,
            startColor = Color.argb(255, 255, 60, 60),
            endColor   = Color.TRANSPARENT,
            startSize = 10f, endSize = 0f,
            isCircle = false,
            spinRateMin = 120f, spinRateMax = 360f,
            gravity = 300f, drag = 0.82f,
            lifetimeMin = 0.3f, lifetimeMax = 0.7f
        )
    },

    /** Feather and petal burst on player death. */
    DEATH_EXPLOSION {
        override fun build(x: Float, y: Float) = ParticleEmitter(
            x = x, y = y,
            isBurst = true, count = 24,
            angleMin = 0f, angleMax = 360f,
            speedMin = 100f, speedMax = 500f,
            startColor = Color.argb(255, 255, 180, 200), // Pink petals
            endColor   = Color.TRANSPARENT,
            startSize = 12f, endSize = 2f,
            isCircle = false,
            spinRateMin = 180f, spinRateMax = 720f,
            gravity = 150f, drag = 0.90f,
            lifetimeMin = 0.5f, lifetimeMax = 1.0f
        )
    };

    /** Construct a [ParticleEmitter] configured at position (x, y). */
    abstract fun build(x: Float, y: Float): ParticleEmitter
}
