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
import com.anurag9000.forestrun.entities.PlayerState
import com.anurag9000.forestrun.ui.DialogueBubbleManager
import kotlin.math.sin

/**
 * Duck (Phase 10)
 * Flies at head/waist height. Player must duck under it.
 */
class Duck(
    context: Context,
    startX: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val readability = ReadabilityProfile.entityForGround(EntityType.DUCK, groundY)
    private val birdH = readability.heightPx
    private val birdW = SpriteSizing.widthForHeight(sprite, birdH, minWidth = readability.minWidthPx)
    // Duck flies at ~60% screen height above ground — roughly head height
    private val flyY = groundY - groundY * 0.30f
    private val insetX = birdW * readability.hitInsetXRatio
    private val insetY = birdH * readability.hitInsetYRatio
    private val quackCallRect = RectF()
    private val duckLaneRect = RectF()
    private val quackApproachRect = RectF()
    private val laneApproachRect = RectF()
    private val quackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(36, 255, 247, 188)
        style = Paint.Style.FILL
    }
    private val quackStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(114, 255, 239, 154)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val lanePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(54, 244, 226, 108)
        style = Paint.Style.FILL
    }
    private val laneStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(126, 255, 236, 134)
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private var cuePulse = 0f
    private var quackCalled = false
    private var lanePrompted = false
    private var stayedLow = false
    private var answeredQuack = false

    init {
        x = startX
        y = flyY - birdH
        hitbox.set(x + insetX, y + insetY, x + birdW - insetX, y + birdH - insetY)
    }

    // Phase 20: play quack SFX 0.5s before entering screen here

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        cuePulse += deltaTime * 5.2f
        hitbox.offsetTo(x + insetX, y + insetY)
        val pad = readability.stagingPaddingPx
        quackCallRect.set(
            x - pad * 4.4f,
            y - birdH * 0.34f,
            x + birdW * 0.48f,
            y + birdH * 0.30f
        )
        duckLaneRect.set(
            x - pad * 0.9f,
            y + birdH * 0.92f,
            x + birdW + pad * 1.8f,
            y + birdH * 1.54f
        )
        sprite.update(deltaTime)
        if (x < -birdW - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val pulse = 0.55f + 0.45f * sin(cuePulse)
        quackPaint.alpha = (18f + 28f * pulse).toInt().coerceIn(0, 255)
        quackStrokePaint.alpha = (74f + 48f * pulse).toInt().coerceIn(0, 255)
        lanePaint.alpha = (40f + 40f * pulse).toInt().coerceIn(0, 255)
        laneStrokePaint.alpha = (92f + 72f * pulse).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(quackCallRect, 18f, 18f, quackPaint)
        canvas.drawRoundRect(quackCallRect, 18f, 18f, quackStrokePaint)
        canvas.drawRoundRect(duckLaneRect, 16f, 16f, lanePaint)
        canvas.drawRoundRect(duckLaneRect, 16f, 16f, laneStrokePaint)
        val laneCenterY = duckLaneRect.centerY()
        val markerSpacing = birdW * 0.22f
        for (index in 0..2) {
            val markerX = duckLaneRect.left + birdW * 0.22f + index * markerSpacing
            canvas.drawLine(
                markerX,
                laneCenterY - 10f,
                markerX + 12f,
                laneCenterY,
                laneStrokePaint
            )
            canvas.drawLine(
                markerX + 12f,
                laneCenterY,
                markerX,
                laneCenterY + 10f,
                laneStrokePaint
            )
        }
        val drawRect = RectF(x, y, x + birdW, y + birdH)
        sprite.draw(canvas, drawRect)
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        gameState.addBonus(
            points = if (answeredQuack) 145 else if (stayedLow) 120 else 108,
            seeds = if (answeredQuack) 1 else 0
        )
        DialogueBubbleManager.spawn(
            text = BirdEncounterFlavor.duckPass(answeredQuack),
            anchorX = x + birdW * 0.5f,
            anchorY = y - 16f,
            fillColor = Color.rgb(255, 250, 220),
            borderColor = Color.rgb(184, 146, 62)
        )
    }

    override fun updatePlayerInteraction(player: Player, gameState: GameStateManager) {
        quackApproachRect.set(
            hitbox.left - readability.stagingPaddingPx * 7f,
            hitbox.top - readability.stagingPaddingPx * 2.2f,
            hitbox.right,
            hitbox.bottom + readability.stagingPaddingPx
        )
        if (!quackCalled && RectF.intersects(player.hitbox, quackApproachRect)) {
            quackCalled = true
            DialogueBubbleManager.spawn(
                BirdEncounterFlavor.duckCall(),
                quackCallRect.centerX(),
                quackCallRect.top - 12f,
                Color.rgb(255, 249, 224),
                Color.rgb(184, 146, 62)
            )
        }

        laneApproachRect.set(
            duckLaneRect.left - readability.stagingPaddingPx * 2f,
            duckLaneRect.top - readability.stagingPaddingPx,
            duckLaneRect.right,
            duckLaneRect.bottom + readability.stagingPaddingPx
        )
        if (!lanePrompted && RectF.intersects(player.hitbox, laneApproachRect)) {
            lanePrompted = true
            DialogueBubbleManager.spawn(
                BirdEncounterFlavor.duckAnswerPrompt(),
                duckLaneRect.centerX(),
                duckLaneRect.top - 10f,
                Color.rgb(255, 250, 226),
                Color.rgb(184, 146, 62)
            )
        }
        if (player.state == PlayerState.DUCKING && RectF.intersects(player.hitbox, duckLaneRect)) {
            stayedLow = true
            if (quackCalled) answeredQuack = true
        }
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) return CollisionResult.HIT
        val mercyPad = readability.mercyPaddingPx
        return if (intersectsExpanded(player.hitbox, hitbox, mercyPad)) {
            CollisionResult.MERCY_MISS
        } else {
            CollisionResult.NONE
        }
    }
}
