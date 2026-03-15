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
 * Weeping Willow — Phase 27: sprite rendered at 2× height, sway applied via canvas rotation.
 * Player must duck under the curtain of leaves (lower curtainHitbox).
 * Trunk hitbox remains full-height.
 */
class WeepingWillow(
    context: Context,
    startX: Float,
    private val screenHeight: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val treeHeight    = screenHeight * 0.56f
    private val treeWidth     = SpriteSizing.widthForHeight(sprite, treeHeight, minWidth = screenHeight * 0.22f)
    private val trunkWidth    = treeWidth * 0.18f
    private val trunkTop      = groundY - treeHeight * 0.42f
    private val curtainTop    = groundY - treeHeight * 0.78f
    private val curtainBottom = groundY - treeHeight * 0.16f

    private val curtainHitbox = RectF()
    private val drawRect      = RectF()

    init {
        x = startX
        y = groundY - treeHeight
        swayComponent = SwayComponent(speed = 0.5f, intensity = 20f)
        hitbox.set(x + treeWidth / 2f - trunkWidth / 2f, trunkTop,
                   x + treeWidth / 2f + trunkWidth / 2f, groundY)
        curtainHitbox.set(x + treeWidth * 0.08f, curtainTop, x + treeWidth * 0.92f, curtainBottom)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x + treeWidth / 2f - trunkWidth / 2f, trunkTop)
        curtainHitbox.set(x + treeWidth * 0.08f + sway, curtainTop, x + treeWidth * 0.92f + sway, curtainBottom)
        sprite.update(deltaTime)
        if (x < -treeWidth - 50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f
        drawRect.set(x, groundY - treeHeight, x + treeWidth, groundY)
        canvas.save()
        canvas.rotate(sway * 0.5f, x + treeWidth / 2f, groundY)
        sprite.draw(canvas, drawRect)
        canvas.restore()
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox) ||
            RectF.intersects(player.hitbox, curtainHitbox)) return CollisionResult.HIT
        val cm = RectF(curtainHitbox.left - 12f, curtainHitbox.top, curtainHitbox.right + 12f, curtainHitbox.bottom + 12f)
        val tm = RectF(hitbox.left - 12f, hitbox.top - 12f, hitbox.right + 12f, hitbox.bottom + 12f)
        if (RectF.intersects(player.hitbox, cm) || RectF.intersects(player.hitbox, tm)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
