package com.yourname.forest_run.entities.birds

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.yourname.forest_run.engine.CameraSystem
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.Player
import kotlin.math.sqrt

/**
 * Eagle (Phase 10)
 * Spawns off-screen top. Captures player's Y on spawn.
 * Dives diagonally toward that point at high speed.
 */
class Eagle(
    context: Context,
    startX: Float,
    private val screenWidth: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val birdW = 80f
    private val birdH = 55f
    private val diveSpeed = 700f

    private var velX = 0f
    private var velY = 0f

    init {
        // Spawn off-screen top at a random horizontal position
        x = startX
        y = -birdH - 20f
        hitbox.set(x + 8f, y + 8f, x + birdW - 8f, y + birdH - 8f)

        // Phase 20: play screech SFX on spawn
    }

    /**
     * Call once after spawning to aim the eagle at the player's current position.
     */
    fun lockOnTarget(targetX: Float, targetY: Float) {
        val dx = targetX - x
        val dy = targetY - y
        val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat().coerceAtLeast(1f)
        velX = dx / dist * diveSpeed
        velY = dy / dist * diveSpeed
        CameraSystem.shakeEagle()  // Phase 15: lock-on tremor
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x += velX * deltaTime
        y += velY * deltaTime
        hitbox.offsetTo(x + 8f, y + 8f)
        sprite.update(deltaTime)
        // Despawn when completely off screen
        if (y > groundY + birdH || x < -birdW - 50f || x > screenWidth + 50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val drawRect = RectF(x, y, x + birdW, y + birdH)
        sprite.draw(canvas, drawRect)
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) return CollisionResult.HIT
        val mercy = RectF(hitbox.left - 12f, hitbox.top - 12f, hitbox.right + 12f, hitbox.bottom + 12f)
        if (RectF.intersects(player.hitbox, mercy)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
