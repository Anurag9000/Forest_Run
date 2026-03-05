package com.yourname.forest_run.entities

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.yourname.forest_run.engine.GameStateManager

/**
 * Cactus (Phase 8)
 * Static flora. No sway. Tight hitbox.
 * Hits result in game over.
 * Phase 18 will add the Skull badge overlay if hit 5+ times.
 */
class Cactus(context: Context, startX: Float, groundY: Float) : Entity(context) {

    private val cactusWidth = 40f
    private val cactusHeight = 80f

    private val paint = Paint().apply {
        color = Color.rgb(30, 140, 50)
        style = Paint.Style.FILL
    }

    private val spikePaint = Paint().apply {
        color = Color.rgb(20, 80, 20)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    init {
        x = startX
        y = groundY - cactusHeight
        // Tight hitbox (inset by 5px)
        hitbox.set(x + 5f, y + 5f, x + cactusWidth - 5f, y + cactusHeight)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        hitbox.offsetTo(x + 5f, y + 5f)
        if (x < -cactusWidth) isActive = false
    }

    override fun draw(canvas: Canvas) {
        // Draw main body
        canvas.drawRoundRect(x, y, x + cactusWidth, y + cactusHeight, 10f, 10f, paint)
        
        // Draw some simple spikes
        canvas.drawLine(x - 5f, y + 20f, x + 5f, y + 20f, spikePaint)
        canvas.drawLine(x + cactusWidth - 5f, y + 40f, x + cactusWidth + 5f, y + 40f, spikePaint)
        canvas.drawLine(x - 5f, y + 60f, x + 5f, y + 60f, spikePaint)

        // Phase 18: draw skull badge if encounters > threshold
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) {
            return CollisionResult.HIT
        }
        
        // Check for mercy miss (within 12px)
        val mercyBox = android.graphics.RectF(
            hitbox.left - 12f, hitbox.top - 12f,
            hitbox.right + 12f, hitbox.bottom + 12f
        )
        if (android.graphics.RectF.intersects(player.hitbox, mercyBox)) {
            return CollisionResult.MERCY_MISS
        }

        return CollisionResult.NONE
    }
}
