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
import com.yourname.forest_run.entities.PlayerState
import com.yourname.forest_run.ui.FlavorTextManager

/**
 * Fox (Phase 11)
 *
 * Detection zone = 3× body width ahead (ahead means to the left, where the player is).
 * If the player jumps while within detection range AND the fox hasn't jumped yet:
 *   Fox mirrors the jump — leaps up and over, landing back on the ground.
 *   Dialogue: "Heh." from fox.
 *
 * After fox lands from its mirror-jump, if player passed cleanly: "Next time..."
 * At 5 mercy hearts: Fox just sits and doesn't jump. Spare.
 */
class Fox(
    context: Context,
    startX: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val foxW = 65f
    private val foxH = 55f

    private enum class FoxState { WALKING, JUMPING, LANDING, SPARED }
    private var foxState = FoxState.WALKING

    private val walkSpeed = 200f
    private val detectionRange get() = foxW * 3f
    private var hasJumped = false

    // Fox jump physics (mirrors player jump mini version)
    private var foxVelY = 0f
    private val foxJumpForce = -700f
    private val foxGravity   = 2200f

    private var spared = false

    init {
        x = startX
        y = groundY - foxH
        velocityX = -walkSpeed
        hitbox.set(x + 8f, y + 5f, x + foxW - 8f, y + foxH)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        sprite.update(deltaTime)

        when (foxState) {
            FoxState.WALKING -> {
                x -= (walkSpeed + scrollSpeed * 0.05f) * deltaTime
            }
            FoxState.JUMPING -> {
                x -= walkSpeed * 0.6f * deltaTime  // Fox still drifts forward during jump
                y += foxVelY * deltaTime
                foxVelY += foxGravity * deltaTime
                if (y >= groundY - foxH) {
                    y = groundY - foxH
                    foxVelY = 0f
                    foxState = FoxState.LANDING
                    FlavorTextManager.spawn("Next time...", x, y - 40f, Color.rgb(220, 140, 60))
                }
            }
            FoxState.LANDING -> {
                x -= walkSpeed * deltaTime
            }
            FoxState.SPARED -> {
                // Fox sits — doesn't move, just scrolls with the world then despawns
                x -= scrollSpeed * deltaTime
            }
        }

        hitbox.offsetTo(x + 8f, y + 5f)
        if (x < -foxW - 50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val drawRect = RectF(x, y, x + foxW, y + foxH)
        sprite.draw(canvas, drawRect)
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        // Check for Spare  threshold
        if (!spared && gameState.mercyHearts >= 5) {
            spared   = true
            foxState = FoxState.SPARED
            gameState.addBonus(points = 120, seeds = 2)
            FlavorTextManager.spawn("Fine.", x, y - 30f, Color.rgb(255, 200, 120))
            return
        }

        // Mirror jump: player is jumping and fox is in detection range
        if (!hasJumped && !spared && foxState == FoxState.WALKING) {
            val playerIsJumping = player.state in listOf(
                PlayerState.JUMPING, PlayerState.JUMP_START, PlayerState.APEX
            )
            val inRange = (player.x + Player.BASE_WIDTH) > (x - detectionRange) &&
                          player.x < (x + foxW)

            if (playerIsJumping && inRange) {
                hasJumped = true
                foxState  = FoxState.JUMPING
                foxVelY   = foxJumpForce
                FlavorTextManager.spawn("Heh.", x + 5f, y - 35f, Color.rgb(220, 120, 60))
            }
        }
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        // Trigger mirror jump check during every frame while player is nearby
        performUniqueAction(player, gameState)

        if (RectF.intersects(player.hitbox, hitbox)) {
            return CollisionResult.HIT
        }
        val mercy = RectF(hitbox.left - 12f, hitbox.top - 12f, hitbox.right + 12f, hitbox.bottom + 12f)
        if (RectF.intersects(player.hitbox, mercy)) {
            return CollisionResult.MERCY_MISS
        }
        return CollisionResult.NONE
    }
}
