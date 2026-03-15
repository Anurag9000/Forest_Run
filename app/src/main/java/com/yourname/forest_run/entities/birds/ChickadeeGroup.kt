package com.yourname.forest_run.entities.birds

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.ReadabilityProfile
import com.yourname.forest_run.engine.SpriteSizing
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.ui.DialogueBubbleManager
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

    private val readability = ReadabilityProfile.entityForGround(EntityType.CHICKADEE, groundY)
    private val birdH = readability.heightPx
    private val birdW = SpriteSizing.widthForHeight(sprite, birdH, minWidth = readability.minWidthPx)
    private val spacing = 74f
    private val birdCount = count.coerceIn(2, 4)
    private val flutterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(64, 255, 232, 188)
        style = Paint.Style.FILL
    }

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
            canvas.drawCircle(rect.centerX(), rect.centerY(), birdW * 0.28f, flutterPaint)
            sprite.draw(canvas, rect)
        }
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        gameState.addBonus(points = 130)
        DialogueBubbleManager.spawn(
            text = "Flutter!",
            anchorX = x + birdCount * spacing * 0.42f,
            anchorY = altitudes.min() - 24f,
            fillColor = Color.rgb(255, 246, 224),
            borderColor = Color.rgb(170, 128, 84)
        )
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        for (rect in birdRects) {
            if (RectF.intersects(player.hitbox, rect)) return CollisionResult.HIT
            val mercyPad = readability.mercyPaddingPx
            val mercy = RectF(rect.left - mercyPad, rect.top - mercyPad, rect.right + mercyPad, rect.bottom + mercyPad)
            if (RectF.intersects(player.hitbox, mercy)) return CollisionResult.MERCY_MISS
        }
        return CollisionResult.NONE
    }
}
