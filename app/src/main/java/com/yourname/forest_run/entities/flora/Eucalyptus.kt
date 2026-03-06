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
 * Eucalyptus — Phase 27: fast-whipping sway animation via SpriteSheet.
 */
class Eucalyptus(
    context: Context,
    startX: Float,
    groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val floraWidth  = 44f
    private val floraHeight = 90f
    private val drawRect    = RectF()

    init {
        x = startX
        y = groundY - floraHeight
        swayComponent = SwayComponent(speed = 2.5f, intensity = 6f)
        hitbox.set(x + 8f, y + 12f, x + floraWidth - 8f, y + floraHeight)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x + 8f + sway, y + 12f)
        sprite.update(deltaTime)
        if (x < -floraWidth - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f
        drawRect.set(x, y, x + floraWidth, y + floraHeight)
        canvas.save()
        canvas.rotate(sway * 3f, x + floraWidth / 2f, y + floraHeight)
        sprite.draw(canvas, drawRect)
        canvas.restore()
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) return CollisionResult.HIT
        val mercy = RectF(hitbox.left - 12f, hitbox.top - 12f, hitbox.right + 12f, hitbox.bottom + 12f)
        if (RectF.intersects(player.hitbox, mercy)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
