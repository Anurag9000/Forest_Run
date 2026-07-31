package com.anurag9000.forestrun.entities.animals

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.anurag9000.forestrun.engine.AnimalEncounterFlavor
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.PersistentMemoryManager
import com.anurag9000.forestrun.engine.ReadabilityProfile
import com.anurag9000.forestrun.engine.SpriteSizing
import com.anurag9000.forestrun.engine.SpriteSheet
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.systems.FxPreset
import com.anurag9000.forestrun.systems.ParticleManager
import com.anurag9000.forestrun.ui.DialogueBubbleManager
import kotlin.math.sin

/**
 * Hedgehog (Phase 11)
 *
 * Fast, tiny, very low to the ground. Hard to dodge at speed.
 * Collision → NOT game over. Instead:
 *   - Apply 50% speed debuff for 3 seconds.
 *   - Play curl animation (set sprite to last frame and hold).
 *   - Dialogue "Eep!" on near-miss.
 */
class Hedgehog(
    context: Context,
    startX: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val readability = ReadabilityProfile.entityForGround(EntityType.HEDGEHOG, groundY)
    private val hogH  = readability.heightPx
    private val hogW  = SpriteSizing.widthForHeight(sprite, hogH, minWidth = readability.minWidthPx)
    private val insetX = hogW * readability.hitInsetXRatio
    private val insetY = hogH * readability.hitInsetYRatio
    private val warningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(56, 255, 220, 164)
        style = Paint.Style.FILL
    }
    private val warningStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(118, 214, 160, 88)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val hopLanePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(58, 180, 218, 255)
        style = Paint.Style.FILL
    }
    private val hopLaneStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 214, 234, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val repeatHits = PersistentMemoryManager.getHitCount(context, EntityType.HEDGEHOG)
    private val warningLeadDurationSec = readability.telegraphDurationSec.coerceIn(0.16f, 0.22f)

    private var hasHit = false  // Only apply debuff once per instance
    private var warned = false
    private var armed = false
    private var warningLeadTimer = 0f
    private var pulse = 0f
    private val warningRect = RectF()
    private val mercyRect = RectF()

    init {
        x = startX
        y = groundY - hogH
        hitbox.set(x + insetX, y + insetY, x + hogW - insetX, y + hogH)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= (scrollSpeed * 1.15f) * deltaTime  // Slightly faster than scroll speed (sneaky!)
        pulse += deltaTime * 5.5f
        if (warned && !armed) {
            warningLeadTimer = (warningLeadTimer - deltaTime).coerceAtLeast(0f)
            if (warningLeadTimer <= 0f) {
                armed = true
            }
        }
        hitbox.offsetTo(x + insetX, y + insetY)
        sprite.update(deltaTime)
        if (x < -hogW - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val pulseValue = 0.55f + 0.45f * sin(pulse)
        warningPaint.alpha = (38f + 34f * pulseValue).toInt().coerceIn(0, 255)
        warningStrokePaint.alpha = (96f + 44f * pulseValue).toInt().coerceIn(0, 255)
        canvas.drawOval(x - 8f, y + hogH * 0.15f, x + hogW + 8f, y + hogH + 4f, warningPaint)
        canvas.drawOval(x - 8f, y + hogH * 0.15f, x + hogW + 8f, y + hogH + 4f, warningStrokePaint)
        if (warned && !hasHit) {
            hopLanePaint.alpha = if (armed) 42 else 76
            hopLaneStrokePaint.alpha = if (armed) 120 else 188
            canvas.drawRoundRect(
                x - 14f,
                y - hogH * 0.24f,
                x + hogW + 14f,
                y + hogH * 0.18f,
                24f,
                24f,
                hopLanePaint
            )
            canvas.drawRoundRect(
                x - 14f,
                y - hogH * 0.24f,
                x + hogW + 14f,
                y + hogH * 0.18f,
                24f,
                24f,
                hopLaneStrokePaint
            )
        }
        val drawRect = RectF(x, y, x + hogW, y + hogH)
        sprite.draw(canvas, drawRect)
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        val clearedRead = warned
        gameState.addBonus(
            points = if (clearedRead) 135 else 95,
            seeds = if (clearedRead) 1 else 0
        )
        DialogueBubbleManager.spawn(
            AnimalEncounterFlavor.hedgehogPass(repeatHits, clearedRead),
            x + hogW * 0.5f,
            y - 14f,
            Color.rgb(255, 246, 220),
            Color.rgb(160, 120, 70)
        )
        if (repeatHits >= 1 || clearedRead) {
            ParticleManager.emit(FxPreset.MERCY_STARS, x + hogW * 0.5f, y + hogH * 0.45f)
        }
        if (clearedRead) {
            ParticleManager.emit(FxPreset.SEED_COLLECT, x + hogW * 0.5f, y + hogH * 0.22f)
        }
    }

    override fun updatePlayerInteraction(player: Player, gameState: GameStateManager) {
        warningRect.set(
            hitbox.left - readability.stagingPaddingPx * 5f,
            hitbox.top - readability.stagingPaddingPx,
            hitbox.right + readability.stagingPaddingPx,
            hitbox.bottom + readability.stagingPaddingPx
        )
        if (!warned && RectF.intersects(player.hitbox, warningRect)) {
            warned = true
            armed = false
            warningLeadTimer = warningLeadDurationSec
            DialogueBubbleManager.spawn(
                AnimalEncounterFlavor.hedgehogWarning(repeatHits),
                x + hogW * 0.5f,
                y - 14f,
                Color.rgb(255, 246, 220),
                Color.rgb(160, 120, 70)
            )
        }
    }

    override fun onOutcomeSelected(
        result: CollisionResult,
        player: Player,
        gameState: GameStateManager
    ) {
        when (result) {
            CollisionResult.STUMBLE -> if (!hasHit) {
                hasHit = true
                gameState.applySpeedDebuff(0.5f, 3000)
                sprite.isLooping = false
                sprite.setFrame(sprite.frameCount - 1)
                ParticleManager.emit(
                    FxPreset.MERCY_STARS,
                    player.x + Player.BASE_WIDTH * 0.5f,
                    player.y + Player.BASE_HEIGHT * 0.5f
                )
                DialogueBubbleManager.spawn(
                    AnimalEncounterFlavor.hedgehogHit(repeatHits),
                    player.x + Player.BASE_WIDTH * 0.5f,
                    player.y - 20f,
                    Color.rgb(255, 242, 220),
                    Color.rgb(160, 120, 70)
                )
            }

            CollisionResult.MERCY_MISS -> {
                val line = if (warned && !armed && RectF.intersects(player.hitbox, hitbox)) {
                    "Hop now."
                } else {
                    "Eep!"
                }
                DialogueBubbleManager.spawn(
                    line,
                    x + hogW * 0.5f,
                    y - 14f,
                    Color.rgb(255, 246, 220),
                    Color.rgb(160, 120, 70)
                )
            }

            else -> Unit
        }
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) {
            return if (warned && !armed) {
                CollisionResult.MERCY_MISS
            } else {
                CollisionResult.STUMBLE
            }
        }

        val mercyPad = readability.mercyPaddingPx
        mercyRect.set(
            hitbox.left - mercyPad,
            hitbox.top - mercyPad,
            hitbox.right + mercyPad,
            hitbox.bottom + mercyPad
        )
        return if (RectF.intersects(player.hitbox, mercyRect)) {
            CollisionResult.MERCY_MISS
        } else {
            CollisionResult.NONE
        }
    }
}
