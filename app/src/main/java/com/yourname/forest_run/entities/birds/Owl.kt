package com.yourname.forest_run.entities.birds

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.Player

/**
 * Owl (Phase 10) — Night only.
 * Perches high-mid. Dives toward player if player jumps while owl is on screen.
 * If player doesn't jump, owl stays put and is passable underneath.
 */
class Owl(
    context: Context,
    startX: Float,
    private val groundY: Float,
    private val idleSprite: SpriteSheet,
    private val actionSprite: SpriteSheet
) : Entity(context) {

    private val birdW = 50f
    private val birdH = 60f
    private val perchY = groundY * 0.2f // High-up perch

    private enum class OwlState { SLEEPING, DIVING }
    private var owlState = OwlState.SLEEPING
    private var currentSprite = idleSprite

    private var velX = 0f
    private var velY = 0f

    init {
        x = startX
        y = perchY
        hitbox.set(x + 5f, y + 5f, x + birdW - 5f, y + birdH - 5f)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        currentSprite.update(deltaTime)

        when (owlState) {
            OwlState.SLEEPING -> {
                x -= scrollSpeed * deltaTime
                // Amber eye glow is handled by particle manager in Phase 14
            }
            OwlState.DIVING -> {
                x += velX * deltaTime
                y += velY * deltaTime
            }
        }

        hitbox.offsetTo(x + 5f, y + 5f)
        if (x < -birdW - 50f || y > groundY + birdH) isActive = false
    }

    /**
     * Call from the EntityManager or Player physics when the player jumps while the owl is visible.
     * Triggers the dive toward the player's current position.
     */
    fun triggerDive(targetX: Float, targetY: Float) {
        if (owlState == OwlState.SLEEPING) {
            owlState = OwlState.DIVING
            currentSprite = actionSprite
            currentSprite.reset()
            val diveSpeed = 600f
            val dx = targetX - x
            val dy = targetY - y
            val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat().coerceAtLeast(1f)
            velX = dx / dist * diveSpeed
            velY = dy / dist * diveSpeed
        }
    }

    override fun draw(canvas: Canvas) {
        val drawRect = RectF(x, y, x + birdW, y + birdH)
        currentSprite.draw(canvas, drawRect)
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        // If player jumps, owl should begin its dive (checked in EntityManager/GameView)
        if (player.state in listOf(
                com.yourname.forest_run.entities.PlayerState.JUMPING,
                com.yourname.forest_run.entities.PlayerState.APEX,
                com.yourname.forest_run.entities.PlayerState.JUMP_START
            ) && owlState == OwlState.SLEEPING
        ) {
            triggerDive(player.x, player.y)
        }

        if (owlState == OwlState.DIVING && RectF.intersects(player.hitbox, hitbox)) {
            return CollisionResult.HIT
        }

        val mercy = RectF(hitbox.left - 12f, hitbox.top - 12f, hitbox.right + 12f, hitbox.bottom + 12f)
        if (owlState == OwlState.DIVING && RectF.intersects(player.hitbox, mercy)) {
            return CollisionResult.MERCY_MISS
        }
        return CollisionResult.NONE
    }
}
