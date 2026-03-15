package com.yourname.forest_run.entities.flora

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
 * Hyacinth — Phase 27: sprite rendered with sway rotation.
 * Brushing collision → MERCY_MISS. Full hit → HIT.
 */
class Hyacinth(
    context: Context,
    startX: Float,
    groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val floraHeight = 118f
    private val floraWidth  = SpriteSizing.widthForHeight(sprite, floraHeight, minWidth = 48f)
    private val hitInsetX   = floraWidth * 0.22f
    private val hitTopY     = floraHeight * 0.28f
    private val drawRect    = RectF()
    private val brushBox    = RectF()
    private val brushPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(52, 188, 120, 228)
        style = Paint.Style.FILL
    }

    init {
        x = startX
        y = groundY - floraHeight
        swayComponent = SwayComponent(speed = 1.0f, intensity = 7f)
        hitbox.set(x + hitInsetX, y + hitTopY, x + floraWidth - hitInsetX, y + floraHeight)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x + hitInsetX + sway, y + hitTopY)
        brushBox.set(hitbox.left - 10f, hitbox.top - 16f, hitbox.right + 10f, hitbox.bottom)
        sprite.update(deltaTime)
        if (x < -floraWidth - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f
        canvas.drawRoundRect(brushBox, 12f, 12f, brushPaint)
        drawRect.set(x, y, x + floraWidth, y + floraHeight)
        canvas.save()
        canvas.rotate(sway * 1.5f, x + floraWidth / 2f, y + floraHeight)
        sprite.draw(canvas, drawRect)
        canvas.restore()
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        gameState.addBonus(points = 100)
        ParticleManager.emit(FxPreset.POLLEN_BURST, x + floraWidth * 0.5f, y + floraHeight * 0.32f)
        DialogueBubbleManager.spawn("Brush the petals", x + floraWidth * 0.5f, y - 12f, Color.rgb(246, 232, 255), Color.rgb(150, 110, 190))
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) return CollisionResult.HIT
        if (RectF.intersects(player.hitbox, brushBox)) return CollisionResult.MERCY_MISS
        val mercy = RectF(hitbox.left - 12f, hitbox.top - 12f, hitbox.right + 12f, hitbox.bottom + 12f)
        if (RectF.intersects(player.hitbox, mercy)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
