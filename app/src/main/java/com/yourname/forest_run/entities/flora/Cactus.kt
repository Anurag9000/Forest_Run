package com.yourname.forest_run.entities.flora

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SpriteSheet
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
        hitbox.set(x + 8f, y + 8f, x + cactusWidth - 8f, y + cactusHeight)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        hitbox.offsetTo(x + 8f, y + 8f)
        sprite.update(deltaTime)
        if (x < -cactusWidth - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        drawRect.set(x, y, x + cactusWidth, y + cactusHeight)
        sprite.draw(canvas, drawRect)
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) return CollisionResult.HIT
        val mercy = RectF(hitbox.left - 12f, hitbox.top - 12f, hitbox.right + 12f, hitbox.bottom + 12f)
        if (RectF.intersects(player.hitbox, mercy)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
