package com.yourname.forest_run.entities.trees

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
 * Jacaranda — Phase 27: sprite rendered with sway. Upper branch hitbox; player must duck to pass.
 */
class Jacaranda(
    context: Context,
    startX: Float,
    private val screenHeight: Float,
    groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val treeWidth   = 150f
    private val trunkWidth  = 30f
    private val branchHeight = screenHeight * 0.30f
    private val branchHitbox = RectF()
    private val drawRect     = RectF()

    init {
        x = startX
        y = 0f
        swayComponent = SwayComponent(speed = 0.8f, intensity = 15f)
        hitbox.set(x + treeWidth / 2f - trunkWidth / 2f, groundY - screenHeight * 0.8f,
                   x + treeWidth / 2f + trunkWidth / 2f, groundY)
        branchHitbox.set(x, 0f, x + treeWidth, branchHeight)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x + treeWidth / 2f - trunkWidth / 2f, hitbox.top)
        branchHitbox.set(x + sway, 0f, x + treeWidth + sway, branchHeight)
        sprite.update(deltaTime)
        if (x < -treeWidth - 50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f
        drawRect.set(x, 0f, x + treeWidth, screenHeight * 0.85f)
        canvas.save()
        canvas.rotate(sway * 0.8f, x + treeWidth / 2f, screenHeight * 0.85f)
        sprite.draw(canvas, drawRect)
        canvas.restore()
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        // Phase 14: spawn full-screen petal curtain FX
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox) ||
            RectF.intersects(player.hitbox, branchHitbox)) return CollisionResult.HIT
        val bm = RectF(branchHitbox.left - 12f, branchHitbox.top, branchHitbox.right + 12f, branchHitbox.bottom + 12f)
        val tm = RectF(hitbox.left - 12f, hitbox.top - 12f, hitbox.right + 12f, hitbox.bottom + 12f)
        if (RectF.intersects(player.hitbox, bm) || RectF.intersects(player.hitbox, tm)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
