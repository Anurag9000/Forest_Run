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
 * Cactus — Phase 27: rendered via SpriteSheet. Sprite loaded from assets/sprites/plants/cactus_4frames.png.
 * Falls back to BitmapHelper placeholder automatically if file is missing.
 */
class Cactus(
    context: Context,
    startX: Float,
    groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val cactusWidth  = 48f
    private val cactusHeight = 80f
    private val drawRect     = RectF()

    init {
        x = startX
        y = groundY - cactusHeight
        swayComponent = SwayComponent(speed = 1.0f, intensity = 4f)
        hitbox.set(x + 8f, y + 8f, x + cactusWidth - 8f, y + cactusHeight)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x + 8f + sway, y + 8f)
        sprite.update(deltaTime)
        if (x < -cactusWidth - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f
        drawRect.set(x, y, x + cactusWidth, y + cactusHeight)
        canvas.save()
        canvas.rotate(sway * 1.5f, x + cactusWidth / 2f, y + cactusHeight)
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
