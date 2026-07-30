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
     * Atomically claims this Orb. A collected Orb becomes inactive immediately,
     * so repeated collision checks can never grant duplicate Seeds.
     */
    fun checkCollection(playerHitbox: RectF): Boolean {
        if (!isActive || isCollected || playerHitbox.isEmpty) return false
        checkRect.set(
            bobRect.centerX() - RADIUS,
            bobRect.centerY() - RADIUS,
            bobRect.centerX() + RADIUS,
            bobRect.centerY() + RADIUS
        )
        if (!RectF.intersects(playerHitbox, checkRect)) return false
        isCollected = true
        isActive = false
        return true
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
