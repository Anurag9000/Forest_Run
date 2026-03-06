package com.yourname.forest_run.systems

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.sin
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager

/**
 * A collectible seed orb that floats above the ground.
 *
 * Visual:
 *  - Pulsing circle with a halo ring (sine-wave radius oscillation).
 *  - Colour shifts from gold → green as Bloom Meter fills.
 *  - Emits a brief sparkle ring (via ParticleManager.emit SEED_COLLECT) on collection.
 *
 * Lifetime:
 *  - Spawned by SeedOrbManager directly above a passed entity.
 *  - Scrolls left at game speed + small upward bob.
 *  - Collected when player rect overlaps the orb rect.
 *  - Despawns after LIFETIME_S seconds if uncollected.
 */
class SeedOrb(
    var x: Float,
    var y: Float
) {
    companion object {
        const val RADIUS       = 18f
        const val BOB_SPEED    = 3.0f   // Hz
        const val BOB_AMP      = 8f     // px
        const val LIFETIME_S   = 6f
        const val HALO_MARGIN  = 8f
    }

    var isActive        = true
    var isCollected     = false
    private var elapsed = 0f
    private var bobTime = 0f

    // Bobbing Y offset
    private val bobRect  = RectF()

    // ── Paints ─────────────────────────────────────────────────────────────
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = 3f
    }

    fun update(deltaTime: Float, scrollSpeed: Float, gameState: GameStateManager): Boolean {
        if (!isActive) return false

        elapsed += deltaTime
        bobTime += deltaTime

        // Scroll left with the world
        x -= scrollSpeed * deltaTime
        // Sine bob
        val bob = sin((bobTime * BOB_SPEED * 2f * Math.PI.toFloat())) * BOB_AMP

        // Recompute rect for this frame
        val cy = y + bob
        bobRect.set(x - RADIUS, cy - RADIUS, x + RADIUS, cy + RADIUS)

        // Check collection — caller passes player hitbox later via SeedOrbManager
        if (elapsed >= LIFETIME_S) {
            isActive = false
        }

        return isActive
    }

    fun draw(canvas: Canvas, bloomFraction: Float) {
        if (!isActive) return

        // Colour interpolation: gold → spring green as bloom fills
        val r = MathUtils.lerp(255f, 60f,  bloomFraction).toInt().coerceIn(0, 255)
        val g = MathUtils.lerp(210f, 220f, bloomFraction).toInt().coerceIn(0, 255)
        val b = MathUtils.lerp(40f,  80f,  bloomFraction).toInt().coerceIn(0, 255)
        val col = Color.rgb(r, g, b)

        // Pulsing radius
        val pulse = 1f + 0.08f * sin(bobTime * 5f)
        val radius = RADIUS * pulse

        // Halo (semi-transparent, slightly larger)
        haloPaint.color = col
        haloPaint.alpha = 100
        canvas.drawCircle(bobRect.centerX(), bobRect.centerY(), radius + HALO_MARGIN, haloPaint)

        // Core orb
        corePaint.color = col
        canvas.drawCircle(bobRect.centerX(), bobRect.centerY(), radius, corePaint)

        // Inner bright highlight
        corePaint.color = Color.argb(160, 255, 255, 255)
        canvas.drawCircle(
            bobRect.centerX() - radius * 0.25f,
            bobRect.centerY() - radius * 0.3f,
            radius * 0.35f,
            corePaint
        )
    }

    /** Returns true if player hitbox overlaps the orb. */
    fun checkCollection(playerHitbox: android.graphics.RectF): Boolean {
        if (!isActive || isCollected) return false
        val orbRect = RectF(
            bobRect.centerX() - RADIUS,
            bobRect.centerY() - RADIUS,
            bobRect.centerX() + RADIUS,
            bobRect.centerY() + RADIUS
        )
        return android.graphics.RectF.intersects(playerHitbox, orbRect)
    }
}

/** Alias so SeedOrb can use MathUtils without circular import. */
private object MathUtils {
    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
}
