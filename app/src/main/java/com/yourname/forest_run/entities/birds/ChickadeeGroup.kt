package com.yourname.forest_run.entities.birds

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.Player
import kotlin.random.Random

/**
 * Chickadee (Phase 10)
 * Group of 2-4 birds that independently change altitude every 1 ± 0.3s.
 */
class ChickadeeGroup(
    context: Context,
    startX: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet,
    count: Int = 3
) : Entity(context) {

    private val birdW = 32f
    private val birdH = 24f
    private val spacing = 60f
    private val birdCount = count.coerceIn(2, 4)

    // Each bird's individual altitude and timer
    private val altitudes = FloatArray(birdCount) { groundY * (0.3f + Random.nextFloat() * 0.4f) }
    private val targetAltitudes = FloatArray(birdCount) { groundY * (0.3f + Random.nextFloat() * 0.4f) }
    private val altitudeTimers = FloatArray(birdCount) { Random.nextFloat() * 1.3f }
    private val altitudeIntervals = FloatArray(birdCount) { 0.7f + Random.nextFloat() * 0.6f }

    private val birdRects = Array(birdCount) { i ->
        val bx = startX + i * spacing
        RectF(bx + 3f, altitudes[i] - birdH / 2f + 3f, bx + birdW - 3f, altitudes[i] + birdH / 2f - 3f)
    }

    init {
        x = startX
        y = groundY * 0.4f
        hitbox.set(x, y, x + birdCount * spacing, y + birdH)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        sprite.update(deltaTime)

        for (i in 0 until birdCount) {
            altitudeTimers[i] -= deltaTime
            if (altitudeTimers[i] <= 0f) {
                targetAltitudes[i] = groundY * (0.2f + Random.nextFloat() * 0.5f)
                altitudeTimers[i] = altitudeIntervals[i]
            }
            // Smoothly lerp to target altitude
            altitudes[i] += (targetAltitudes[i] - altitudes[i]) * (deltaTime * 6f)

            val bx = x + i * spacing
            birdRects[i].offsetTo(bx + 3f, altitudes[i] - birdH / 2f + 3f)
        }

        hitbox.offsetTo(x, altitudes.min() - birdH)
        if (x < -(birdCount * spacing) - 50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        for (rect in birdRects) {
            sprite.draw(canvas, rect)
        }
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        for (rect in birdRects) {
            if (RectF.intersects(player.hitbox, rect)) return CollisionResult.HIT
            val mercy = RectF(rect.left - 12f, rect.top - 12f, rect.right + 12f, rect.bottom + 12f)
            if (RectF.intersects(player.hitbox, mercy)) return CollisionResult.MERCY_MISS
        }
        return CollisionResult.NONE
    }
}
