package com.yourname.forest_run.entities.flora

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SwayComponent
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.Player

/**
 * Eucalyptus (Phase 8)
 * Slanted trapezoid-like plant swaying very quickly.
 */
class Eucalyptus(context: Context, startX: Float, groundY: Float) : Entity(context) {

    private val floraWidth = 40f
    private val floraHeight = 90f

    private val paint = Paint().apply {
        color = Color.rgb(80, 160, 120)
        style = Paint.Style.FILL
    }
    
    private val path = Path()

    init {
        x = startX
        y = groundY - floraHeight
        swayComponent = SwayComponent(speed = 2.5f, intensity = 6f) // Fast whip
        
        // Trapezoid roughly bounded by this rect
        hitbox.set(x + 5f, y + 10f, x + floraWidth - 5f, y + floraHeight)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        
        hitbox.offsetTo(x + 5f + sway, y + 10f)
        if (x < -floraWidth) isActive = false
        
        // Phase 14: emit green leaf particles at sway peak
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f

        canvas.save()
        val swayDegrees = sway * 3f
        canvas.rotate(swayDegrees, x + floraWidth / 2f, y + floraHeight)

        // Draw a slanted trapezoid leaf structure
        path.reset()
        path.moveTo(x + 20f, y) // Top point
        path.lineTo(x + floraWidth, y + floraHeight * 0.7f) // Right bulge
        path.lineTo(x + 25f, y + floraHeight) // Bottom right
        path.lineTo(x + 15f, y + floraHeight) // Bottom left
        path.lineTo(x, y + floraHeight * 0.5f) // Left bulge
        path.close()

        canvas.drawPath(path, paint)

        canvas.restore()
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
