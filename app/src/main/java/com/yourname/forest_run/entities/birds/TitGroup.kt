package com.yourname.forest_run.entities.birds

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.Player
import kotlin.math.sin

/**
 * Tit (Phase 10)
 * Spawns as a group of 3-5. All birds share the same vertical sine wave.
 * Player jumps through the trough.
 */
class TitGroup(
    context: Context,
    startX: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet,
    count: Int = 4
) : Entity(context) {

    private val birdW = 36f
    private val birdH = 28f
    private val spacing = 55f
    private val baseLine = groundY * 0.45f // horizontal flight altitude
    private val waveAmplitude = 80f
    private val waveFrequency = 2.5f

    private var time = 0f
    private val birdCount = count.coerceIn(3, 5)

    // Individual rects for collision — all birds share the same wave
    private val birdRects = Array(birdCount) { i ->
        val bx = startX + i * spacing
        RectF(bx + 3f, baseLine - birdH / 2f + 3f, bx + birdW - 3f, baseLine + birdH / 2f - 3f)
    }

    init {
        x = startX
        y = baseLine
        // Main hitbox covers full group width
        hitbox.set(x, baseLine - birdH, x + birdCount * spacing, baseLine + birdH)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        time += deltaTime
        sprite.update(deltaTime)

        val waveY = sin((time * waveFrequency).toDouble()).toFloat() * waveAmplitude

        for (i in 0 until birdCount) {
            val bx = x + i * spacing
            birdRects[i].offsetTo(bx + 3f, baseLine + waveY - birdH / 2f + 3f)
        }
        hitbox.offsetTo(x, baseLine + waveY - birdH)

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
