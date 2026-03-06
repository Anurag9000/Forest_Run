package com.yourname.forest_run.entities.flora

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.engine.SwayComponent
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.Player

/**
 * Hyacinth — Phase 27: sprite rendered with sway rotation.
 * Brushing collision → MERCY_MISS. Full hit → HIT.
 */
class Hyacinth(
    context: Context,
    startX: Float,
    groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val floraWidth  = 52f
    private val floraHeight = 64f
    private val drawRect    = RectF()
    private val brushBox    = RectF()

    init {
        x = startX
        y = groundY - floraHeight
        swayComponent = SwayComponent(speed = 1.0f, intensity = 7f)
        hitbox.set(x + 14f, y + 20f, x + 38f, y + floraHeight)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x + 14f + sway, y + 20f)
        brushBox.set(hitbox.left - 10f, hitbox.top - 16f, hitbox.right + 10f, hitbox.bottom)
        sprite.update(deltaTime)
        if (x < -floraWidth - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f
        drawRect.set(x, y, x + floraWidth, y + floraHeight)
        canvas.save()
        canvas.rotate(sway * 1.5f, x + floraWidth / 2f, y + floraHeight)
        sprite.draw(canvas, drawRect)
        canvas.restore()
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) return CollisionResult.HIT
        if (RectF.intersects(player.hitbox, brushBox)) return CollisionResult.MERCY_MISS
        val mercy = RectF(hitbox.left - 12f, hitbox.top - 12f, hitbox.right + 12f, hitbox.bottom + 12f)
        if (RectF.intersects(player.hitbox, mercy)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
