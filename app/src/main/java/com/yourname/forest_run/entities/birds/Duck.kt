package com.yourname.forest_run.entities.birds

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.BirdEncounterFlavor
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.ReadabilityProfile
import com.yourname.forest_run.engine.SpriteSizing
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.entities.PlayerState
import com.yourname.forest_run.ui.DialogueBubbleManager

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
    private val duckLaneRect = RectF()
    private val cuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 244, 226, 108)
        style = Paint.Style.FILL
    }
    private val cueStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(118, 255, 236, 134)
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private var cuePulse = 0f
    private var warned = false
    private var stayedLow = false

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
        duckLaneRect.set(x - pad, y + birdH * 0.18f, x + birdW + pad, y + birdH * 0.86f)
        sprite.update(deltaTime)
        if (x < -birdW - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val pulse = 0.55f + 0.45f * kotlin.math.sin(cuePulse)
        cuePaint.alpha = (26f + 34f * pulse).toInt().coerceIn(0, 255)
        cueStrokePaint.alpha = (86f + 64f * pulse).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(duckLaneRect, 16f, 16f, cuePaint)
        canvas.drawRoundRect(duckLaneRect, 16f, 16f, cueStrokePaint)
        val drawRect = RectF(x, y, x + birdW, y + birdH)
        sprite.draw(canvas, drawRect)
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        gameState.addBonus(
            points = if (stayedLow) 125 else 105,
            seeds = if (stayedLow) 1 else 0
        )
        DialogueBubbleManager.spawn(
            text = BirdEncounterFlavor.duckPass(stayedLow),
            anchorX = x + birdW * 0.5f,
            anchorY = y - 16f,
            fillColor = Color.rgb(255, 250, 220),
            borderColor = Color.rgb(184, 146, 62)
        )
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        val approach = RectF(
            hitbox.left - readability.stagingPaddingPx * 5f,
            hitbox.top - readability.stagingPaddingPx,
            hitbox.right + readability.stagingPaddingPx,
            hitbox.bottom + readability.stagingPaddingPx
        )
        if (!warned && RectF.intersects(player.hitbox, approach)) {
            warned = true
            DialogueBubbleManager.spawn(
                BirdEncounterFlavor.duckWarning(),
                x + birdW * 0.5f,
                y - 18f,
                Color.rgb(255, 249, 224),
                Color.rgb(184, 146, 62)
            )
        }
        if (player.state == PlayerState.DUCKING && player.hitbox.right >= hitbox.left && player.hitbox.left <= hitbox.right) {
            stayedLow = true
        }
        if (RectF.intersects(player.hitbox, hitbox)) return CollisionResult.HIT
        val mercyPad = readability.mercyPaddingPx
        val mercy = RectF(hitbox.left - mercyPad, hitbox.top - mercyPad, hitbox.right + mercyPad, hitbox.bottom + mercyPad)
        if (RectF.intersects(player.hitbox, mercy)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
