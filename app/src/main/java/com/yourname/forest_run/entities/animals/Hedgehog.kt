package com.yourname.forest_run.entities.animals

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SpriteSizing
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.ui.DialogueBubbleManager

/**
 * Hedgehog (Phase 11)
 *
 * Fast, tiny, very low to the ground. Hard to dodge at speed.
 * Collision → NOT game over. Instead:
 *   - Apply 50% speed debuff for 3 seconds.
 *   - Play curl animation (set sprite to last frame and hold).
 *   - Dialogue "Eep!" on near-miss.
 */
class Hedgehog(
    context: Context,
    startX: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val hogH  = 64f
    private val hogW  = SpriteSizing.widthForHeight(sprite, hogH, minWidth = 48f)
    private val insetX = hogW * 0.08f
    private val insetY = hogH * 0.08f
    private val warningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(56, 255, 220, 164)
        style = Paint.Style.FILL
    }
    private val warningStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(118, 214, 160, 88)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private var hasHit = false  // Only apply debuff once per instance

    init {
        x = startX
        y = groundY - hogH
        hitbox.set(x + insetX, y + insetY, x + hogW - insetX, y + hogH)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= (scrollSpeed * 1.15f) * deltaTime  // Slightly faster than scroll speed (sneaky!)
        hitbox.offsetTo(x + insetX, y + insetY)
        sprite.update(deltaTime)
        if (x < -hogW - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        canvas.drawOval(x - 8f, y + hogH * 0.15f, x + hogW + 8f, y + hogH + 4f, warningPaint)
        canvas.drawOval(x - 8f, y + hogH * 0.15f, x + hogW + 8f, y + hogH + 4f, warningStrokePaint)
        val drawRect = RectF(x, y, x + hogW, y + hogH)
        sprite.draw(canvas, drawRect)
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        gameState.addBonus(points = 95)
        DialogueBubbleManager.spawn("Careful...", x + hogW * 0.5f, y - 14f, Color.rgb(255, 246, 220), Color.rgb(160, 120, 70))
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) {
            if (!hasHit) {
                hasHit = true
                // Speed debuff: 50% speed for 3 seconds
                gameState.applySpeedDebuff(0.5f, 3000)
                // curl — freeze on last sprite frame
                sprite.isLooping = false
                sprite.setFrame(sprite.frameCount - 1)
                DialogueBubbleManager.spawn("Oof!", player.x + Player.BASE_WIDTH * 0.5f, player.y - 20f, Color.rgb(255, 242, 220), Color.rgb(160, 120, 70))
            }
            // Hedgehog is NOT a game-over — return NONE so the system doesn't kill the player
            return CollisionResult.NONE
        }

        val mercy = RectF(hitbox.left - 12f, hitbox.top - 12f, hitbox.right + 12f, hitbox.bottom + 12f)
        if (RectF.intersects(player.hitbox, mercy)) {
            DialogueBubbleManager.spawn("Eep!", x + hogW * 0.5f, y - 14f, Color.rgb(255, 246, 220), Color.rgb(160, 120, 70))
            return CollisionResult.MERCY_MISS
        }

        return CollisionResult.NONE
    }
}
