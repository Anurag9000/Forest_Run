package com.yourname.forest_run.entities.animals

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.ui.FlavorTextManager

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

    private val hogW  = 35f
    private val hogH  = 28f  // Very low to the ground

    private var hasHit = false  // Only apply debuff once per instance

    init {
        x = startX
        y = groundY - hogH
        hitbox.set(x + 2f, y + 2f, x + hogW - 2f, y + hogH)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= (scrollSpeed * 1.15f) * deltaTime  // Slightly faster than scroll speed (sneaky!)
        hitbox.offsetTo(x + 2f, y + 2f)
        sprite.update(deltaTime)
        if (x < -hogW - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val drawRect = RectF(x, y, x + hogW, y + hogH)
        sprite.draw(canvas, drawRect)
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) {
            if (!hasHit) {
                hasHit = true
                // Speed debuff: 50% speed for 3 seconds
                gameState.applySpeedDebuff(0.5f, 3000)
                // curl — freeze on last sprite frame
                sprite.setFrame(sprite.frameCount - 1)
                FlavorTextManager.spawn("Oof!", player.x, player.y - 40f, Color.rgb(255, 180, 80))
            }
            // Hedgehog is NOT a game-over — return NONE so the system doesn't kill the player
            return CollisionResult.NONE
        }

        val mercy = RectF(hitbox.left - 12f, hitbox.top - 12f, hitbox.right + 12f, hitbox.bottom + 12f)
        if (RectF.intersects(player.hitbox, mercy)) {
            FlavorTextManager.spawn("Eep!", x + 5f, y - 30f, Color.rgb(255, 220, 120))
            return CollisionResult.MERCY_MISS
        }

        return CollisionResult.NONE
    }
}
