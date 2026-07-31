package com.anurag9000.forestrun.entities.animals

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.PersistentMemoryManager
import com.anurag9000.forestrun.engine.ReadabilityProfile
import com.anurag9000.forestrun.engine.RelationshipEncounterTuning
import com.anurag9000.forestrun.engine.RelationshipArcSystem
import com.anurag9000.forestrun.engine.SpriteSizing
import com.anurag9000.forestrun.engine.SpriteSheet
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.PlayerState
import com.anurag9000.forestrun.systems.FxPreset
import com.anurag9000.forestrun.systems.ParticleManager
import com.anurag9000.forestrun.ui.DialogueBubbleManager

/**
 * Fox (Phase 11)
 *
 * Detection zone = 3× body width ahead (ahead means to the left, where the player is).
 * If the player jumps while within detection range AND the fox hasn't jumped yet:
 *   Fox mirrors the jump — leaps up and over, landing back on the ground.
 *   Dialogue: "Heh." from fox.
 *
 * After fox lands from its mirror-jump, if player passed cleanly: "Next time..."
 * At 5 mercy hearts: Fox just sits and doesn't jump. Spare.
 */
class Fox(
    context: Context,
    startX: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val readability = ReadabilityProfile.entityForGround(EntityType.FOX, groundY)
    private val relationshipTuning: RelationshipEncounterTuning =
        RelationshipArcSystem.encounterTuning(context, EntityType.FOX)
    private val warmBond = RelationshipArcSystem.isWarmBond(context, EntityType.FOX)
    private val repeatMemoryCharm =
        RelationshipArcSystem.featuredRepeatFriend(context) == EntityType.FOX ||
            PersistentMemoryManager.getPassCount(context, EntityType.FOX) >= 4
    private val foxH = readability.heightPx
    private val foxW = SpriteSizing.widthForHeight(sprite, foxH, minWidth = readability.minWidthPx)
    private val insetX = foxW * readability.hitInsetXRatio
    private val insetY = foxH * readability.hitInsetYRatio
    private val detectionRect = RectF()
    private val detectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(44, 255, 206, 132)
        style = Paint.Style.FILL
    }
    private val detectionStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(118, 255, 184, 96)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val trailAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(44, 255, 220, 164)
        style = Paint.Style.FILL
    }
    private val trailAuraStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(104, 255, 198, 128)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private enum class FoxState { WALKING, JUMPING, LANDING, SPARED }
    private var foxState = FoxState.WALKING

    private val walkSpeed = 200f
    private val baseDetectionRange get() =
        foxW * readability.detectionRangeBodies * relationshipTuning.detectionMultiplier
    private val visibleDetectionRange get() = baseDetectionRange * 1.8f
    private val mirrorDetectionRange get() = baseDetectionRange * 2.6f
    private var hasJumped = false

    // Fox jump physics (mirrors player jump mini version)
    private var foxVelY = 0f
    private val foxJumpForce = -700f
    private val foxGravity   = 2200f

    private var spared = false
    private var passRewarded = false

    init {
        x = startX
        y = groundY - foxH
        velocityX = -walkSpeed
        hitbox.set(x + insetX, y + insetY, x + foxW - insetX, y + foxH)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        sprite.update(deltaTime)

        when (foxState) {
            FoxState.WALKING -> {
                x -= (walkSpeed + scrollSpeed * 0.05f) * deltaTime
            }
            FoxState.JUMPING -> {
                x -= walkSpeed * 0.6f * deltaTime  // Fox still drifts forward during jump
                y += foxVelY * deltaTime
                foxVelY += foxGravity * deltaTime
                if (y >= groundY - foxH) {
                    y = groundY - foxH
                    foxVelY = 0f
                    foxState = FoxState.LANDING
                    DialogueBubbleManager.spawn(
                        RelationshipArcSystem.encounterCueLine(context, EntityType.FOX, RelationshipArcSystem.EncounterCue.FOX_LANDING),
                        x + foxW * 0.55f,
                        y - 18f,
                        Color.rgb(255, 236, 214),
                        Color.rgb(190, 110, 55)
                    )
                    if (warmBond) {
                        ParticleManager.emit(FxPreset.SEED_COLLECT, x + foxW * 0.55f, y + foxH * 0.28f)
                    }
                    if (repeatMemoryCharm) {
                        ParticleManager.emit(FxPreset.MERCY_STARS, x + foxW * 0.55f, y + foxH * 0.20f)
                    }
                }
            }
            FoxState.LANDING -> {
                x -= walkSpeed * deltaTime
            }
            FoxState.SPARED -> {
                // Fox sits — doesn't move, just scrolls with the world then despawns
                x -= scrollSpeed * deltaTime
            }
        }

        val pad = readability.stagingPaddingPx
        detectionRect.set(x - visibleDetectionRange, y - pad, x + foxW + pad * 0.7f, y + foxH + pad * 0.5f)
        hitbox.offsetTo(x + insetX, y + insetY)
        if (x < -foxW - 50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        if (!spared && foxState == FoxState.WALKING) {
            if (repeatMemoryCharm) {
                canvas.drawRoundRect(
                    detectionRect.left - 18f,
                    detectionRect.top - 10f,
                    detectionRect.right + 10f,
                    detectionRect.bottom + 8f,
                    24f,
                    24f,
                    trailAuraPaint
                )
                canvas.drawRoundRect(
                    detectionRect.left - 18f,
                    detectionRect.top - 10f,
                    detectionRect.right + 10f,
                    detectionRect.bottom + 8f,
                    24f,
                    24f,
                    trailAuraStrokePaint
                )
            }
            canvas.drawRoundRect(detectionRect, 20f, 20f, detectionPaint)
            canvas.drawRoundRect(detectionRect, 20f, 20f, detectionStrokePaint)
        }
        val drawRect = RectF(x, y, x + foxW, y + foxH)
        sprite.draw(canvas, drawRect)
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        // Check for Spare  threshold
        if (!spared && gameState.mercyHearts >= 5) {
            spared   = true
            foxState = FoxState.SPARED
            gameState.addBonus(
                points = 120 + relationshipTuning.passBonusPoints,
                seeds = 2 + relationshipTuning.passBonusSeeds
            )
            PersistentMemoryManager.recordSpare(context, EntityType.FOX)
            gameState.recordSpare()
            DialogueBubbleManager.spawn(
                RelationshipArcSystem.lineFor(context, EntityType.FOX, RelationshipArcSystem.Event.SPARE),
                x + foxW * 0.55f,
                y - 16f,
                Color.rgb(255, 240, 220),
                Color.rgb(190, 110, 55)
            )
            return
        }

        if (!passRewarded && hasJumped && foxState != FoxState.SPARED) {
            passRewarded = true
            gameState.addBonus(
                points = 150 + relationshipTuning.passBonusPoints + if (repeatMemoryCharm) 18 else 0,
                seeds = 1 + relationshipTuning.passBonusSeeds + if (repeatMemoryCharm) 1 else 0
            )
            if (warmBond || repeatMemoryCharm) {
                ParticleManager.emit(FxPreset.MERCY_STARS, x + foxW * 0.55f, y + foxH * 0.4f)
            }
            DialogueBubbleManager.spawn(
                RelationshipArcSystem.lineFor(context, EntityType.FOX, RelationshipArcSystem.Event.PASS),
                x + foxW * 0.55f,
                y - 16f,
                Color.rgb(255, 238, 220),
                Color.rgb(190, 110, 55)
            )
        }
    }

    private fun tryMirrorJump(player: Player) {
        if (hasJumped || spared || foxState != FoxState.WALKING) return

        // A nearby airborne answer should still read as "you matched the trick"
        // on a real phone, even if the player is already on the falling half
        // of the arc by the time the fox checks the lane.
        val playerAnsweredTheLine = player.state in listOf(
            PlayerState.JUMPING,
            PlayerState.JUMP_START,
            PlayerState.APEX,
            PlayerState.FALLING
        )
        val inRange = (player.x + Player.BASE_WIDTH) > (x - mirrorDetectionRange) &&
            player.x < (x + foxW + Player.BASE_WIDTH * 0.35f)

        if (playerAnsweredTheLine && inRange) {
            hasJumped = true
            foxState  = FoxState.JUMPING
            foxVelY   = foxJumpForce
            DialogueBubbleManager.spawn(
                text = RelationshipArcSystem.lineFor(context, EntityType.FOX, RelationshipArcSystem.Event.THREAT),
                anchorX = x + foxW * 0.55f,
                anchorY = y - 16f,
                fillColor = Color.rgb(255, 238, 220),
                borderColor = Color.rgb(190, 110, 55)
            )
        }
    }

    override fun updatePlayerInteraction(player: Player, gameState: GameStateManager) {
        tryMirrorJump(player)
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) return CollisionResult.STUMBLE

        val mercyPad = readability.mercyPaddingPx + relationshipTuning.mercyPaddingBonusPx
        return if (intersectsExpanded(player.hitbox, hitbox, mercyPad)) {
            CollisionResult.MERCY_MISS
        } else {
            CollisionResult.NONE
        }
    }
}
