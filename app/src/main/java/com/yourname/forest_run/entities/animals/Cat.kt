package com.yourname.forest_run.entities.animals

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SpriteSizing
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.ui.FlavorTextManager

/**
 * Cat (Phase 11)
 *
 * Behaviour:
 * - Sits stationary on the ground. Tail-flick animation via SpriteSheet.
 * - Player PASSES it cleanly → awardKindnessBonus() (double seeds + 2× multiplier + "Meow?").
 * - MERCY_MISS overlap → flavour text but smaller reward.
 * - Direct HIT → game over (cat hisses).
 * - After 5 passed (spared): cat waves and exits. [Spare event — Phase 17 full handling]
 *
 * Spares / costumes tracked via Phase 18 PersistentMemoryManager.
 * For now the spare counter is per-instance and fires once.
 */
class Cat(
    context: Context,
    startX: Float,
    groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val catH = 50f
    private val catW = SpriteSizing.widthForHeight(sprite, catH, minWidth = 44f)
    private val insetX = catW * 0.14f
    private val insetY = catH * 0.10f

    // Tracks whether the player has already passed this specific cat instance
    private var playerHasPassed = false
    private var waving = false
    private var waveTimer = 0f

    init {
        x = startX
        y = groundY - catH
        hitbox.set(x + insetX, y + insetY, x + catW - insetX, y + catH)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        if (!waving) {
            x -= scrollSpeed * deltaTime
        } else {
            // Cat trots off screen to the right during Spare event
            x += scrollSpeed * 0.4f * deltaTime
            waveTimer -= deltaTime
            if (waveTimer <= 0f || x > x + 200f) isActive = false
        }
        hitbox.offsetTo(x + insetX, y + insetY)
        sprite.update(deltaTime)
        if (x < -catW - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val drawRect = RectF(x, y, x + catW, y + catH)
        sprite.draw(canvas, drawRect)
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        // Called by EntityManager when player has fully passed (player.hitbox.left > hitbox.right)
        if (!playerHasPassed) {
            playerHasPassed = true
            // Kindness bonus: flat 500 pts + double seeds (removed broken permanent 2x multiplier)
            gameState.addBonus(points = 500, seeds = 2)
            FlavorTextManager.spawn("Meow?", x - 10f, y - 30f, Color.rgb(255, 200, 255))

            // Check for Spare threshold (5 clean passes for this cat type)
            // Phase 18: PersistentMemoryManager.getSpared(CAT) >= 5
            // For now, use a simple run-level mercy heart check
            if (gameState.mercyHearts >= 5 && !waving) {
                triggerSpare()
            }
        }
    }

    private fun triggerSpare() {
        waving = true
        waveTimer = 2.5f
        FlavorTextManager.spawn("See you!", x, y - 50f, Color.rgb(255, 220, 255))
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) {
            FlavorTextManager.spawn("Hiss!", x, y - 30f, Color.RED)
            return CollisionResult.HIT
        }

        val mercy = RectF(hitbox.left - 12f, hitbox.top - 12f, hitbox.right + 12f, hitbox.bottom + 12f)
        if (RectF.intersects(player.hitbox, mercy)) {
            if (!playerHasPassed) {
                FlavorTextManager.spawn("Phew~", player.x, player.y - 40f, Color.rgb(255, 200, 100))
            }
            return CollisionResult.MERCY_MISS
        }

        return CollisionResult.NONE
    }
}
