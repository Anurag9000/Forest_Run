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
 * Wolf (Phase 11)
 *
 * State machine:
 * 1. WALKING  — spawns on screen right, moves left slowly.
 * 2. HOWLING  — triggered when wolf's x crosses 50% screen width. Plays howl SFX cue (Phase 20).
 *               Lasts HOWL_DURATION seconds. Dirt particles (Phase 14).
 * 3. CHARGING — after howl, velocityX doubles. Fast charge toward player.
 *
 * Spare: after 8 mercy hearts, wolf stops, turns, trots off screen.
 */
class Wolf(
    context: Context,
    startX: Float,
    private val groundY: Float,
    private val screenWidth: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val wolfW = 90f
    private val wolfH = 70f

    private enum class WolfState { WALKING, HOWLING, CHARGING, SPARED }
    private var wolfState = WolfState.WALKING

    private val walkSpeed = 150f
    private var howlTimer = 0f
    private val howlDuration = 1.0f

    private var spared = false

    init {
        x = startX
        y = groundY - wolfH
        velocityX = -walkSpeed
        hitbox.set(x + 10f, y + 5f, x + wolfW - 10f, y + wolfH)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        sprite.update(deltaTime)

        when (wolfState) {
            WolfState.WALKING -> {
                x -= walkSpeed * deltaTime
                // Trigger howl at mid-screen
                if (x < screenWidth * 0.5f && !spared) {
                    wolfState = WolfState.HOWLING
                    howlTimer = 0f
                    FlavorTextManager.spawn("GRRR...", x, y - 30f, Color.rgb(200, 60, 60))
                    // Phase 20: play howl SFX
                }
            }
            WolfState.HOWLING -> {
                // Stand still during howl wind-up
                howlTimer += deltaTime
                if (howlTimer >= howlDuration) {
                    wolfState  = WolfState.CHARGING
                    velocityX  = -(walkSpeed * 2f + scrollSpeed * 0.3f) // Double + partial scroll speed
                }
            }
            WolfState.CHARGING -> {
                x += velocityX * deltaTime
                // Phase 14: emit dust/dirt particles
            }
            WolfState.SPARED -> {
                // Turn and trot right slowly
                x += walkSpeed * 0.5f * deltaTime
                if (x > screenWidth + wolfW) isActive = false
            }
        }

        hitbox.offsetTo(x + 10f, y + 5f)
        if (x < -wolfW - 50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val drawRect = RectF(x, y, x + wolfW, y + wolfH)
        sprite.draw(canvas, drawRect)
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        // Called when player fully passes without collision
        if (!spared && gameState.mercyHearts >= 8) {
            spared    = true
            wolfState = WolfState.SPARED
            gameState.addBonus(points = 200, seeds = 3)
            FlavorTextManager.spawn("...", x, y - 30f, Color.rgb(200, 200, 220))
        }
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
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
