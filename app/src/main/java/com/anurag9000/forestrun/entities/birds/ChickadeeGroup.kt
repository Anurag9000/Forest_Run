package com.anurag9000.forestrun.entities.birds

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.anurag9000.forestrun.engine.BirdEncounterFlavor
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.ReadabilityProfile
import com.anurag9000.forestrun.engine.SpriteSizing
import com.anurag9000.forestrun.engine.SpriteSheet
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.ui.DialogueBubbleManager
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
    private val leadBirdIndex = birdCount / 2
    private val flutterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(64, 255, 232, 188)
        style = Paint.Style.FILL
    }
    private val flutterTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(96, 255, 242, 214)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val leadGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 255, 242, 190)
        style = Paint.Style.FILL
    }
    private val pocketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(54, 255, 236, 196)
        style = Paint.Style.FILL
    }
    private val pocketStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(124, 255, 244, 214)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    // Each bird's individual altitude and timer
    private val altitudes = FloatArray(birdCount) { groundY * (0.3f + Random.nextFloat() * 0.4f) }
    private val targetAltitudes = FloatArray(birdCount) { groundY * (0.3f + Random.nextFloat() * 0.4f) }
    private val altitudeTimers = FloatArray(birdCount) { Random.nextFloat() * 1.3f }
    private val altitudeIntervals = FloatArray(birdCount) { 0.7f + Random.nextFloat() * 0.6f }
    private var warned = false
    private var pocketPrompted = false
    private var readPocket = false
    private val flutterPocketRect = RectF()
    private val pocketApproachRect = RectF()

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
        updateFlutterPocket()
        if (x < -(birdCount * spacing) - 50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRoundRect(flutterPocketRect, 16f, 16f, pocketPaint)
        canvas.drawRoundRect(flutterPocketRect, 16f, 16f, pocketStrokePaint)
        for (i in birdRects.indices) {
            val rect = birdRects[i]
            canvas.drawLine(rect.centerX(), rect.centerY(), rect.centerX(), targetAltitudes[i], flutterTrailPaint)
            if (i == leadBirdIndex) {
                canvas.drawCircle(rect.centerX(), rect.centerY(), birdW * 0.42f, leadGlowPaint)
            }
            canvas.drawCircle(rect.centerX(), rect.centerY(), birdW * 0.28f, flutterPaint)
            sprite.draw(canvas, rect)
        }
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        gameState.addBonus(
            points = if (readPocket) 144 + flutterSpread().toInt().coerceAtMost(40) / 10 else 130 + flutterSpread().toInt().coerceAtMost(40) / 10,
            seeds = if (readPocket) 1 else 0
        )
        DialogueBubbleManager.spawn(
            text = BirdEncounterFlavor.chickadeePass(flutterSpread(), readPocket),
            anchorX = x + birdCount * spacing * 0.42f,
            anchorY = altitudes.min() - 24f,
            fillColor = Color.rgb(255, 246, 224),
            borderColor = Color.rgb(170, 128, 84)
        )
    }

    override fun updatePlayerInteraction(player: Player, gameState: GameStateManager) {
        val approachLeft = birdRects.first().left - readability.stagingPaddingPx * 6f
        val approachRight = birdRects.last().right + readability.stagingPaddingPx
        if (!warned && player.hitbox.right >= approachLeft && player.hitbox.left <= approachRight) {
            warned = true
            DialogueBubbleManager.spawn(
                BirdEncounterFlavor.chickadeeWarning(flutterSpread()),
                x + birdCount * spacing * 0.42f,
                altitudes.min() - 24f,
                Color.rgb(255, 246, 224),
                Color.rgb(170, 128, 84)
            )
        }

        pocketApproachRect.set(
            flutterPocketRect.left - readability.stagingPaddingPx,
            flutterPocketRect.top - readability.stagingPaddingPx,
            flutterPocketRect.right + readability.stagingPaddingPx,
            flutterPocketRect.bottom + readability.stagingPaddingPx
        )
        if (!pocketPrompted && RectF.intersects(player.hitbox, pocketApproachRect)) {
            pocketPrompted = true
            DialogueBubbleManager.spawn(
                BirdEncounterFlavor.chickadeePocketPrompt(),
                flutterPocketRect.centerX(),
                flutterPocketRect.top - 14f,
                Color.rgb(255, 246, 224),
                Color.rgb(170, 128, 84)
            )
        }
        if (RectF.intersects(player.hitbox, flutterPocketRect)) readPocket = true
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        for (rect in birdRects) {
            if (RectF.intersects(player.hitbox, rect)) return CollisionResult.HIT
            val mercyPad = readability.mercyPaddingPx
            if (intersectsExpanded(player.hitbox, rect, mercyPad)) return CollisionResult.MERCY_MISS
        }
        return CollisionResult.NONE
    }

    private fun flutterSpread(): Float = altitudes.max() - altitudes.min()

    private fun updateFlutterPocket() {
        val leadRect = birdRects[leadBirdIndex]
        val upperBound = birdRects.minOf { it.bottom } + 4f
        val lowerBound = birdRects.maxOf { it.top } - 4f
        val fallbackCenter = leadRect.centerY() + birdH * 0.72f
        val pocketTop = if (lowerBound > upperBound + birdH * 0.18f) {
            upperBound
        } else {
            fallbackCenter - birdH * 0.20f
        }
        val pocketBottom = if (lowerBound > upperBound + birdH * 0.18f) {
            lowerBound
        } else {
            fallbackCenter + birdH * 0.20f
        }
        flutterPocketRect.set(
            leadRect.centerX() - birdW * 0.95f,
            pocketTop,
            leadRect.centerX() + birdW * 0.95f,
            pocketBottom
        )
    }
}
