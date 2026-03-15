package com.yourname.forest_run.entities.flora

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SpriteSizing
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.engine.SwayComponent
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.systems.FxPreset
import com.yourname.forest_run.systems.ParticleManager
import com.yourname.forest_run.ui.DialogueBubbleManager

/**
 * Lily of the Valley — Phase 27: sprite rendered with sway rotation.
 */
class LilyOfValley(
    context: Context,
    startX: Float,
    groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val floraHeight = 92f
    private val floraWidth  = SpriteSizing.widthForHeight(sprite, floraHeight, minWidth = 56f)
    private val hitInsetX   = floraWidth * 0.22f
    private val hitTopY     = floraHeight * 0.45f
    private val drawRect    = RectF()

    init {
        x = startX
        y = groundY - floraHeight
        swayComponent = SwayComponent(speed = 1.5f, intensity = 5f)
        hitbox.set(x + hitInsetX, y + hitTopY, x + floraWidth - hitInsetX, y + floraHeight)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x + hitInsetX + sway, y + hitTopY)
        sprite.update(deltaTime)
        if (x < -floraWidth - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f
        drawRect.set(x, y, x + floraWidth, y + floraHeight)
        canvas.save()
        canvas.rotate(sway * 2f, x + floraWidth / 2f, y + floraHeight)
        sprite.draw(canvas, drawRect)
        canvas.restore()
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        gameState.addBonus(points = 90)
        ParticleManager.emit(FxPreset.LILY_NIGHT_GLOW, x + floraWidth * 0.5f, y + floraHeight * 0.25f)
        DialogueBubbleManager.spawn("Soft glow", x + floraWidth * 0.5f, y - 10f, Color.rgb(242, 255, 252), Color.rgb(110, 170, 150))
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) return CollisionResult.HIT
        val mercy = RectF(hitbox.left - 12f, hitbox.top - 12f, hitbox.right + 12f, hitbox.bottom + 12f)
        if (RectF.intersects(player.hitbox, mercy)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
