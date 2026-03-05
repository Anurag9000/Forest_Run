package com.yourname.forest_run.entities.flora

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SwayComponent
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.Player

/**
 * Vanilla Orchid (Phase 8)
 * Complex flora with two hitboxes: a low vine body and an overhead branch.
 * Hitting either is a HIT. The gap between them is safe (requires jumping at the right height).
 */
class VanillaOrchid(context: Context, startX: Float, groundY: Float) : Entity(context) {

    private val floraWidth = 60f
    private val floraHeight = 160f // Total height (from ground to overhead branch)

    private val vinePaint = Paint().apply {
        color = Color.rgb(100, 200, 100)
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val branchPaint = Paint().apply {
        color = Color.rgb(139, 69, 19) // Brownish branch
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val flowerPaint = Paint().apply {
        color = Color.rgb(255, 250, 205) // Pale yellow/white
        style = Paint.Style.FILL
    }

    // Two distinct hitboxes
    private val bottomHitbox = RectF()
    private val topHitbox = RectF()

    init {
        x = startX
        y = groundY - floraHeight
        swayComponent = SwayComponent(speed = 1.2f, intensity = 8f) // Vine sways
        
        // Let's define the safe window
        // Bottom vine starts from ground, goes up 40px
        bottomHitbox.set(x + 10f, groundY - 40f, x + 30f, groundY)
        
        // Top branch hangs down from 160px above ground, down to 90px above ground
        // Safe gap is between y=groundY-90 and y=groundY-40
        topHitbox.set(x + 20f, y, x + 60f, groundY - 90f)
        
        // Set the primary hitbox to encompass both so the EntityManager doesn't cull it early
        hitbox.set(x, y, x + floraWidth, groundY)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        
        // Update all hitboxes
        hitbox.offsetTo(x, y)
        bottomHitbox.offsetTo(x + 10f + sway, y + floraHeight - 40f)
        // Top branch sways less, or independently
        topHitbox.offsetTo(x + 20f + sway * 0.5f, y)
        
        if (x < -floraWidth) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f

        canvas.save()
        val swayDegrees = sway * 2f
        
        // Draw top branch
        canvas.save()
        canvas.rotate(swayDegrees * 0.5f, x + 40f, y)
        canvas.drawLine(x + 40f, y, x + 40f, y + 70f, branchPaint)
        // Draw flower
        canvas.drawCircle(x + 40f, y + 70f, 12f, flowerPaint)
        canvas.restore()

        // Draw bottom vine
        canvas.save()
        canvas.rotate(swayDegrees, x + 20f, y + floraHeight)
        canvas.drawLine(x + 20f, y + floraHeight, x + 20f, y + floraHeight - 40f, vinePaint)
        // Little leaf
        canvas.drawCircle(x + 15f, y + floraHeight - 20f, 8f, vinePaint)
        canvas.restore()

        canvas.restore()
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        // Phase 14: emit white sparkle particles on pass
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        // Check both hitboxes
        if (RectF.intersects(player.hitbox, bottomHitbox) || RectF.intersects(player.hitbox, topHitbox)) {
            return CollisionResult.HIT
        }
        
        // Mercy box around both
        val bottomMercy = RectF(
            bottomHitbox.left - 12f, bottomHitbox.top - 12f,
            bottomHitbox.right + 12f, bottomHitbox.bottom + 12f
        )
        val topMercy = RectF(
            topHitbox.left - 12f, topHitbox.top - 12f,
            topHitbox.right + 12f, topHitbox.bottom + 12f
        )

        if (RectF.intersects(player.hitbox, bottomMercy) || RectF.intersects(player.hitbox, topMercy)) {
            return CollisionResult.MERCY_MISS
        }

        return CollisionResult.NONE
    }
}
