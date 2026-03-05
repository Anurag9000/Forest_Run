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
 * Hyacinth (Phase 8)
 * Mid-sized cluster of 3 flowers.
 * Brushing collision applies a speed debuff. Full hit kills.
 */
class Hyacinth(context: Context, startX: Float, groundY: Float) : Entity(context) {

    private val floraWidth = 50f
    private val floraHeight = 60f

    private val stemPaint = Paint().apply {
        color = Color.rgb(40, 160, 60)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val flowerPaint = Paint().apply {
        color = Color.rgb(180, 100, 220)
        style = Paint.Style.FILL
    }

    // Hitbox specifically for the "brushing" partial collision
    private val brushBox = RectF()

    init {
        x = startX
        y = groundY - floraHeight
        swayComponent = SwayComponent(speed = 1.0f, intensity = 7f)
        
        hitbox.set(x + 15f, y + 20f, x + 35f, y + floraHeight)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        
        hitbox.offsetTo(x + 15f + sway, y + 20f)
        
        // Brush box is larger and higher
        brushBox.set(
            hitbox.left - 10f, hitbox.top - 15f,
            hitbox.right + 10f, hitbox.bottom
        )

        if (x < -floraWidth) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f

        canvas.save()
        val swayDegrees = sway * 1.5f
        canvas.rotate(swayDegrees, x + floraWidth / 2f, y + floraHeight)

        // Stems
        canvas.drawLine(x + 25f, y + floraHeight, x + 25f, y + 20f, stemPaint)
        canvas.drawLine(x + 25f, y + floraHeight - 10f, x + 10f, y + 30f, stemPaint)
        canvas.drawLine(x + 25f, y + floraHeight - 10f, x + 40f, y + 30f, stemPaint)

        // Flower clusters (simplified circles for now)
        canvas.drawCircle(x + 25f, y + 20f, 10f, flowerPaint)
        canvas.drawCircle(x + 10f, y + 30f, 8f, flowerPaint)
        canvas.drawCircle(x + 40f, y + 30f, 8f, flowerPaint)

        canvas.restore()
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) {
            return CollisionResult.HIT
        }
        
        if (RectF.intersects(player.hitbox, brushBox)) {
            // Player brushed the flowers.
            // In Phase 11/12, we will apply a speed debuff here.
            // For now, return MERCY_MISS but we can consider it a brush later.
            return CollisionResult.MERCY_MISS
        }

        val mercyBox = RectF(
            hitbox.left - 12f, hitbox.top - 12f,
            hitbox.right + 12f, hitbox.bottom + 12f
        )
        if (RectF.intersects(player.hitbox, mercyBox)) {
            return CollisionResult.MERCY_MISS
        }

        return CollisionResult.NONE
    }
}
