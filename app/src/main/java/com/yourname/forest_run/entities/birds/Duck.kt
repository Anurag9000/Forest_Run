package com.yourname.forest_run.entities.birds

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.Player

/**
 * Duck (Phase 10)
 * Flies at head/waist height. Player must duck under it.
 */
class Duck(
    context: Context,
    startX: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val birdW = 70f
    private val birdH = 40f
    // Duck flies at ~60% screen height above ground — roughly head height
    private val flyY = groundY - groundY * 0.30f

    private val paint = Paint().apply { color = Color.rgb(200, 200, 50) }

    init {
        x = startX
        y = flyY - birdH
        hitbox.set(x + 5f, y + 5f, x + birdW - 5f, y + birdH - 5f)
    }

    // Phase 20: play quack SFX 0.5s before entering screen here

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        hitbox.offsetTo(x + 5f, y + 5f)
        sprite.update(deltaTime)
        if (x < -birdW - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val drawRect = RectF(x, y, x + birdW, y + birdH)
        sprite.draw(canvas, drawRect)
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) return CollisionResult.HIT
        val mercy = RectF(hitbox.left - 12f, hitbox.top - 12f, hitbox.right + 12f, hitbox.bottom + 12f)
        if (RectF.intersects(player.hitbox, mercy)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
