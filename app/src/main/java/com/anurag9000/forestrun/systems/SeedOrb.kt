package com.anurag9000.forestrun.systems

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.anurag9000.forestrun.engine.GameStateManager
import kotlin.math.sin

/** Collectible seed reward staged ahead of the player after a clean pass. */
class SeedOrb(
    var x: Float,
    var y: Float
) {
    companion object {
        const val RADIUS = 26f
        const val BOB_SPEED = 2.5f
        const val BOB_AMP = 10f
        const val LIFETIME_S = 6f
        const val HALO_MARGIN = 12f
        private const val OFFSCREEN_MARGIN = 24f
    }

    var isActive = true
        private set
    var isCollected = false
        private set
    private var elapsed = 0f
    private var bobTime = 0f
    // The normal 50 ms recovery step can move a max-speed Orb 100 px.
    // Store the preceding core centre so an in-between pickup is not missed.
    private var previousCentreX = x
    private var previousCentreY = y

    private val bobRect = RectF()
    private val checkRect = RectF()
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    init {
        require(x.isFinite() && y.isFinite()) { "Seed Orb coordinates must be finite." }
        updateGeometry()
    }

    val centreX: Float
        get() = bobRect.centerX()

    val centreY: Float
        get() = bobRect.centerY()

    fun update(
        deltaTime: Float,
        scrollSpeed: Float,
        @Suppress("UNUSED_PARAMETER") gameState: GameStateManager
    ): Boolean {
        if (!isActive) return false
        if (!deltaTime.isFinite() || deltaTime < 0f) return true
        if (!scrollSpeed.isFinite() || scrollSpeed < 0f) return true

        previousCentreX = centreX
        previousCentreY = centreY
        elapsed = finiteSaturatingAdd(elapsed, deltaTime)
        bobTime = finiteSaturatingAdd(bobTime, deltaTime)
        x = finiteSaturatingSubtract(x, scrollSpeed * deltaTime)
        updateGeometry()

        if (elapsed >= LIFETIME_S || bobRect.right < -OFFSCREEN_MARGIN) {
            isActive = false
        }
        return isActive
    }

    fun draw(canvas: Canvas, bloomFraction: Float) {
        if (!isActive) return

        val safeBloomFraction = bloomFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val red = MathUtils.lerp(255f, 60f, safeBloomFraction).toInt().coerceIn(0, 255)
        val green = MathUtils.lerp(210f, 220f, safeBloomFraction).toInt().coerceIn(0, 255)
        val blue = MathUtils.lerp(40f, 80f, safeBloomFraction).toInt().coerceIn(0, 255)
        val colour = Color.rgb(red, green, blue)
        val pulse = 1f + 0.08f * sin(bobTime * 5f)
        val radius = RADIUS * pulse

        haloPaint.color = colour
        haloPaint.alpha = 135
        canvas.drawCircle(
            bobRect.centerX(),
            bobRect.centerY(),
            radius + HALO_MARGIN,
            haloPaint
        )

        corePaint.color = colour
        canvas.drawCircle(bobRect.centerX(), bobRect.centerY(), radius, corePaint)

        corePaint.color = Color.argb(70, 255, 255, 240)
        canvas.drawCircle(bobRect.centerX(), bobRect.centerY(), radius + 5f, corePaint)

        corePaint.color = Color.argb(160, 255, 255, 255)
        canvas.drawCircle(
            bobRect.centerX() - radius * 0.25f,
            bobRect.centerY() - radius * 0.3f,
            radius * 0.35f,
            corePaint
        )
    }

    /**
     * Atomically claims this Orb. Current-frame overlap remains authoritative,
     * but a fast Orb can cross the narrower jumping Player entirely in one
     * bounded update. Check the centre segment against the Player rectangle
     * expanded by the existing square pickup core radius. This is a swept
     * pickup (not a larger stationary pickup radius), with no allocation.
     */
    fun checkCollection(playerHitbox: RectF): Boolean {
        if (!isActive || isCollected || playerHitbox.isEmpty ||
            !playerHitbox.left.isFinite() || !playerHitbox.top.isFinite() ||
            !playerHitbox.right.isFinite() || !playerHitbox.bottom.isFinite()
        ) return false
        checkRect.set(
            bobRect.centerX() - RADIUS,
            bobRect.centerY() - RADIUS,
            bobRect.centerX() + RADIUS,
            bobRect.centerY() + RADIUS
        )
        if (!RectF.intersects(playerHitbox, checkRect) &&
            !sweptCoreIntersects(playerHitbox)
        ) return false
        isCollected = true
        isActive = false
        return true
    }

    /**
     * Slab intersection of the two observed Orb centres with the stationary
     * Player hitbox inflated by the square pickup core. Requiring overlapping
     * time intervals on both axes avoids awarding a diagonal AABB-only miss.
     * The short sinusoidal bob is approximated by its frame-end centre segment.
     */
    private fun sweptCoreIntersects(playerHitbox: RectF): Boolean {
        val startX = previousCentreX.toDouble()
        val startY = previousCentreY.toDouble()
        val deltaX = centreX.toDouble() - startX
        val deltaY = centreY.toDouble() - startY
        var entry = 0.0
        var exit = 1.0

        val left = playerHitbox.left.toDouble() - RADIUS.toDouble()
        val right = playerHitbox.right.toDouble() + RADIUS.toDouble()
        if (deltaX == 0.0) {
            if (startX <= left || startX >= right) return false
        } else {
            val first = (left - startX) / deltaX
            val second = (right - startX) / deltaX
            entry = maxOf(entry, minOf(first, second))
            exit = minOf(exit, maxOf(first, second))
            if (entry >= exit) return false
        }

        val top = playerHitbox.top.toDouble() - RADIUS.toDouble()
        val bottom = playerHitbox.bottom.toDouble() + RADIUS.toDouble()
        if (deltaY == 0.0) {
            if (startY <= top || startY >= bottom) return false
        } else {
            val first = (top - startY) / deltaY
            val second = (bottom - startY) / deltaY
            entry = maxOf(entry, minOf(first, second))
            exit = minOf(exit, maxOf(first, second))
        }
        return entry < exit && exit > 0.0 && entry < 1.0
    }

    private fun updateGeometry() {
        val bob = sin(bobTime * BOB_SPEED * 2f * Math.PI.toFloat()) * BOB_AMP
        val centreY = y + bob
        bobRect.set(
            x - RADIUS,
            centreY - RADIUS,
            x + RADIUS,
            centreY + RADIUS
        )
    }

    private fun finiteSaturatingAdd(value: Float, delta: Float): Float {
        if (!value.isFinite()) return 0f
        if (!delta.isFinite() || delta <= 0f) return value
        return (value.toDouble() + delta.toDouble())
            .coerceAtMost(Float.MAX_VALUE.toDouble())
            .toFloat()
    }

    private fun finiteSaturatingSubtract(value: Float, delta: Float): Float {
        if (!value.isFinite()) return 0f
        if (!delta.isFinite() || delta <= 0f) return value
        return (value.toDouble() - delta.toDouble())
            .coerceIn(-Float.MAX_VALUE.toDouble(), Float.MAX_VALUE.toDouble())
            .toFloat()
    }
}

private object MathUtils {
    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)
}
