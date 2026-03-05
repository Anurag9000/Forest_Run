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
 * Lily of the Valley (Phase 8)
 * Small, harmless flora that acts as a hazard mostly by looking like one.
 * Tiny hitbox. Sways gently.
 * PerformUniqueAction doubles seed spawn rate (Phase 12).
 */
class LilyOfValley(context: Context, startX: Float, groundY: Float) : Entity(context) {

    private val floraWidth = 30f
    private val floraHeight = 40f

    private val stemPaint = Paint().apply {
        color = Color.rgb(60, 180, 80)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val bellPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    init {
        x = startX
        y = groundY - floraHeight
        swayComponent = SwayComponent(speed = 1.5f, intensity = 5f)
        
        // Very tiny hitbox, only at the base
        hitbox.set(x + 10f, y + 20f, x + 20f, y + floraHeight)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        
        hitbox.offsetTo(x + 10f + sway, y + 20f)
        if (x < -floraWidth) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f // Just get current offset without advancing

        canvas.save()
        // Simple rotation based on sway from the bottom anchor (x+width/2, y+height)
        val swayDegrees = sway * 2f
        canvas.rotate(swayDegrees, x + floraWidth / 2f, y + floraHeight)

        // Draw curved stem
        canvas.drawLine(x + 15f, y + floraHeight, x + 15f, y + 10f, stemPaint)
        // Draw little bells
        canvas.drawCircle(x + 10f, y + 15f, 6f, bellPaint)
        canvas.drawCircle(x + 20f, y + 25f, 6f, bellPaint)
        canvas.drawCircle(x + 8f, y + 30f, 6f, bellPaint)

        canvas.restore()
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        // Double seed spawn rate logic goes here when the spawn manager is built
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) {
            return CollisionResult.HIT
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
