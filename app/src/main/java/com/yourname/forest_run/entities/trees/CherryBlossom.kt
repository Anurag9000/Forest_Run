package com.yourname.forest_run.entities.trees

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SpriteSizing
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.engine.SwayComponent
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.Player

/**
 * Cherry Blossom — Phase 27: mid-height branch hitbox, sprite rendered with gentle sway.
 */
class CherryBlossom(
    context: Context,
    startX: Float,
    private val screenHeight: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val treeHeight       = screenHeight * 0.56f
    private val treeWidth        = SpriteSizing.widthForHeight(sprite, treeHeight, minWidth = screenHeight * 0.22f)
    private val trunkWidth       = treeWidth * 0.16f
    private val branchHeightLow  = groundY - treeHeight * 0.26f
    private val branchHeightHigh = groundY - treeHeight * 0.58f
    private val trunkTop         = groundY - treeHeight * 0.34f
    private val branchHitbox    = RectF()
    private val drawRect        = RectF()

    init {
        x = startX
        y = groundY - treeHeight
        swayComponent = SwayComponent(speed = 0.6f, intensity = 12f)
        hitbox.set(x + treeWidth / 2f - trunkWidth / 2f, trunkTop,
                   x + treeWidth / 2f + trunkWidth / 2f, groundY)
        branchHitbox.set(x + treeWidth * 0.08f, branchHeightHigh, x + treeWidth * 0.92f, branchHeightLow)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x + treeWidth / 2f - trunkWidth / 2f, trunkTop)
        branchHitbox.set(x + treeWidth * 0.08f + sway, branchHeightHigh, x + treeWidth * 0.92f + sway, branchHeightLow)
        sprite.update(deltaTime)
        if (x < -treeWidth - 50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f
        drawRect.set(x, groundY - treeHeight, x + treeWidth, groundY)
        canvas.save()
        canvas.rotate(sway * 0.6f, x + treeWidth / 2f, groundY)
        sprite.draw(canvas, drawRect)
        canvas.restore()
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        // Phase 14: temporarily increase global wind speed, spike petal emission
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox) ||
            RectF.intersects(player.hitbox, branchHitbox)) return CollisionResult.HIT
        val bm = RectF(branchHitbox.left - 12f, branchHitbox.top - 12f, branchHitbox.right + 12f, branchHitbox.bottom + 12f)
        val tm = RectF(hitbox.left - 12f, hitbox.top - 12f, hitbox.right + 12f, hitbox.bottom + 12f)
        if (RectF.intersects(player.hitbox, bm) || RectF.intersects(player.hitbox, tm)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
