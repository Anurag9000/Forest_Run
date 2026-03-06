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
 * Vanilla Orchid — Phase 27: rendered as two-segment sprite (low vine + overhead branch).
 * Two independent hitboxes with a safe gap between them.
 * Uses two separate draws of the same sprite: bottom half and top half scaled.
 */
class VanillaOrchid(
    context: Context,
    startX: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val floraWidth  = 64f
    private val floraHeight = 160f

    // Two distinct hitboxes
    private val bottomHitbox = RectF()
    private val topHitbox    = RectF()

    private val bottomRect   = RectF()
    private val topRect      = RectF()

    init {
        x = startX
        y = groundY - floraHeight
        swayComponent = SwayComponent(speed = 1.2f, intensity = 8f)
        bottomHitbox.set(x + 12f, groundY - 42f, x + 36f, groundY)
        topHitbox.set(x + 22f, y, x + floraWidth - 8f, groundY - 92f)
        hitbox.set(x, y, x + floraWidth, groundY)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x, y)
        bottomHitbox.offsetTo(x + 12f + sway, groundY - 42f)
        topHitbox.offsetTo(x + 22f + sway * 0.5f, y)
        sprite.update(deltaTime)
        if (x < -floraWidth - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f

        // Bottom vine segment
        bottomRect.set(x, groundY - 48f, x + floraWidth * 0.6f, groundY)
        canvas.save()
        canvas.rotate(sway * 2f, x + 24f, groundY)
        sprite.draw(canvas, bottomRect)
        canvas.restore()

        // Top branch + flower
        topRect.set(x + 12f, y, x + floraWidth, groundY - 80f)
        canvas.save()
        canvas.rotate(sway * 0.8f, x + 40f, y)
        sprite.draw(canvas, topRect)
        canvas.restore()
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        // Phase 14: emit white sparkle particles on pass
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, bottomHitbox) ||
            RectF.intersects(player.hitbox, topHitbox)) return CollisionResult.HIT
        val bm = RectF(bottomHitbox.left - 12f, bottomHitbox.top - 12f, bottomHitbox.right + 12f, bottomHitbox.bottom + 12f)
        val tm = RectF(topHitbox.left - 12f, topHitbox.top - 12f, topHitbox.right + 12f, topHitbox.bottom + 12f)
        if (RectF.intersects(player.hitbox, bm) || RectF.intersects(player.hitbox, tm)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
