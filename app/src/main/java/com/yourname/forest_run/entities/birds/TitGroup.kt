package com.yourname.forest_run.entities.birds

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

    private val birdH = 60f
    private val birdW = SpriteSizing.widthForHeight(sprite, birdH, minWidth = 48f)
    private val spacing = 72f
    private val baseLine = groundY * 0.45f // horizontal flight altitude
    private val waveAmplitude = 104f
    private val waveFrequency = 2.7f
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(82, 182, 226, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

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
        for (i in 0 until birdCount - 1) {
            val first = birdRects[i]
            val second = birdRects[i + 1]
            canvas.drawLine(first.centerX(), first.centerY(), second.centerX(), second.centerY(), wavePaint)
        }
        for (rect in birdRects) {
            sprite.draw(canvas, rect)
        }
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        gameState.addBonus(points = 120)
        DialogueBubbleManager.spawn(
            text = "In sync",
            anchorX = x + birdCount * spacing * 0.45f,
            anchorY = baseLine - waveAmplitude - 18f,
            fillColor = Color.rgb(230, 244, 255),
            borderColor = Color.rgb(88, 138, 196)
        )
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
