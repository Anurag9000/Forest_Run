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

    private val readability = ReadabilityProfile.entityForGround(EntityType.TIT, groundY)
    private val birdH = readability.heightPx
    private val birdW = SpriteSizing.widthForHeight(sprite, birdH, minWidth = readability.minWidthPx)
    private val spacing = 72f
    private val baseLine = groundY * 0.45f // horizontal flight altitude
    private val waveAmplitude = 104f
    private val waveFrequency = 2.7f
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(82, 182, 226, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val troughPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(58, 164, 230, 255)
        style = Paint.Style.FILL
    }
    private val troughStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(126, 214, 244, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val crestPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(88, 232, 246, 255)
        style = Paint.Style.FILL
    }
    private val beatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(138, 238, 252, 255)
        style = Paint.Style.FILL
    }

    private var time = 0f
    private val birdCount = count.coerceIn(3, 5)
    private var countInPrompted = false
    private var throughPrompted = false
    private var keptBeat = false
    private val troughGuideRect = RectF()
    private val troughApproachRect = RectF()

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
        val guideWidth = birdCount * spacing + birdW * 0.4f
        val guideLeft = x - readability.stagingPaddingPx
        val guideTop = baseLine + waveY + birdH * 0.78f
        val guideBottom = guideTop + birdH * 0.42f
        troughGuideRect.set(guideLeft, guideTop, guideLeft + guideWidth, guideBottom)

        if (x < -(birdCount * spacing) - 50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRoundRect(troughGuideRect, 18f, 18f, troughPaint)
        canvas.drawRoundRect(troughGuideRect, 18f, 18f, troughStrokePaint)
        val beatDotSpacing = troughGuideRect.width() / (birdCount + 1)
        for (index in 1..birdCount) {
            val dotX = troughGuideRect.left + beatDotSpacing * index
            val dotY = if (index % 2 == 0) troughGuideRect.top + 10f else troughGuideRect.bottom - 10f
            canvas.drawCircle(dotX, dotY, birdW * 0.10f, beatPaint)
        }
        for (i in 0 until birdCount - 1) {
            val first = birdRects[i]
            val second = birdRects[i + 1]
            canvas.drawLine(first.centerX(), first.centerY(), second.centerX(), second.centerY(), wavePaint)
        }
        for (rect in birdRects) {
            canvas.drawCircle(rect.centerX(), rect.centerY(), birdW * 0.18f, crestPaint)
            sprite.draw(canvas, rect)
        }
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        gameState.addBonus(
            points = if (keptBeat) 142 + (birdCount - 3) * 12 else 120 + (birdCount - 3) * 10,
            seeds = if (keptBeat) 1 else 0
        )
        DialogueBubbleManager.spawn(
            text = BirdEncounterFlavor.titPass(birdCount, keptBeat),
            anchorX = x + birdCount * spacing * 0.45f,
            anchorY = troughGuideRect.top - 18f,
            fillColor = Color.rgb(230, 244, 255),
            borderColor = Color.rgb(88, 138, 196)
        )
    }

    override fun updatePlayerInteraction(player: Player, gameState: GameStateManager) {
        val approachLeft = birdRects.first().left - readability.stagingPaddingPx * 6f
        val approachRight = birdRects.last().right + readability.stagingPaddingPx
        if (!countInPrompted && player.hitbox.right >= approachLeft && player.hitbox.left <= approachRight) {
            countInPrompted = true
            DialogueBubbleManager.spawn(
                BirdEncounterFlavor.titCountIn(birdCount),
                x + birdCount * spacing * 0.45f,
                baseLine - waveAmplitude - 18f,
                Color.rgb(232, 246, 255),
                Color.rgb(88, 138, 196)
            )
        }

        troughApproachRect.set(
            troughGuideRect.left - readability.stagingPaddingPx * 1.6f,
            troughGuideRect.top - readability.stagingPaddingPx,
            troughGuideRect.right,
            troughGuideRect.bottom + readability.stagingPaddingPx
        )
        if (!throughPrompted && RectF.intersects(player.hitbox, troughApproachRect)) {
            throughPrompted = true
            DialogueBubbleManager.spawn(
                BirdEncounterFlavor.titThroughPrompt(birdCount),
                troughGuideRect.centerX(),
                troughGuideRect.top - 16f,
                Color.rgb(232, 246, 255),
                Color.rgb(88, 138, 196)
            )
        }
        if (RectF.intersects(player.hitbox, troughGuideRect)) keptBeat = true
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        for (rect in birdRects) {
            if (RectF.intersects(player.hitbox, rect)) return CollisionResult.HIT
            val mercyPad = readability.mercyPaddingPx
            val mercy = RectF(
                rect.left - mercyPad,
                rect.top - mercyPad,
                rect.right + mercyPad,
                rect.bottom + mercyPad
            )
            if (RectF.intersects(player.hitbox, mercy)) return CollisionResult.MERCY_MISS
        }
        return CollisionResult.NONE
    }
}
