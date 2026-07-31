package com.anurag9000.forestrun.entities.animals

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.anurag9000.forestrun.engine.CameraSystem
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.PersistentMemoryManager
import com.anurag9000.forestrun.engine.ReadabilityProfile
import com.anurag9000.forestrun.engine.RelationshipEncounterTuning
import com.anurag9000.forestrun.engine.RelationshipArcSystem
import com.anurag9000.forestrun.engine.SfxManager
import com.anurag9000.forestrun.engine.SpriteSizing
import com.anurag9000.forestrun.engine.SpriteSheet
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.systems.FxPreset
import com.anurag9000.forestrun.systems.ParticleManager
import com.anurag9000.forestrun.ui.DialogueBubbleManager

/**
 * Wolf (Phase 11)
 *
 * State machine:
 * 1. WALKING  — spawns on screen right, moves left slowly.
 * 2. HOWLING  — triggered when wolf's x crosses 50% screen width. Plays howl SFX cue (Phase 20).
 *               Lasts HOWL_DURATION seconds. Dirt particles (Phase 14).
 * 3. CHARGING — after howl, velocityX doubles. Fast charge toward player.
 *
 * Spare: after 8 mercy hearts, wolf stops, turns, trots off screen.
 */
class Wolf(
    context: Context,
    startX: Float,
    private val groundY: Float,
    private val screenWidth: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val readability = ReadabilityProfile.entityForGround(EntityType.WOLF, groundY)
    private val relationshipTuning: RelationshipEncounterTuning =
        RelationshipArcSystem.encounterTuning(context, EntityType.WOLF)
    private val warmBond = RelationshipArcSystem.isWarmBond(context, EntityType.WOLF)
    private val sparedHistory = PersistentMemoryManager.getSparedCount(context, EntityType.WOLF)
    private val respectStandDownHistory = sparedHistory >= 2 || warmBond
    private val wolfH = readability.heightPx
    private val wolfW = SpriteSizing.widthForHeight(sprite, wolfH, minWidth = readability.minWidthPx)
    private val insetX = wolfW * readability.hitInsetXRatio
    private val insetY = wolfH * readability.hitInsetYRatio

    private enum class WolfState { WALKING, HOWLING, CHARGING, SPARED }
    private var wolfState = WolfState.WALKING

    private val walkSpeed = 150f
    private var howlTimer = 0f
    private val howlDuration = (readability.telegraphDurationSec * relationshipTuning.telegraphMultiplier).coerceAtLeast(0.8f)

    private var spared = false
    private var passRewarded = false
    private val threatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(56, 255, 118, 118)
        style = Paint.Style.FILL
    }
    private val threatStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 255, 164, 164)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val respectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(62, 194, 226, 255)
        style = Paint.Style.FILL
    }
    private val respectStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(168, 224, 240, 255)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val standDownTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 214, 224, 244)
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
    }

    init {
        x = startX
        y = groundY - wolfH
        velocityX = -walkSpeed
        hitbox.set(x + insetX, y + insetY, x + wolfW - insetX, y + wolfH)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        sprite.update(deltaTime)

        when (wolfState) {
            WolfState.WALKING -> {
                x -= walkSpeed * deltaTime
                // Trigger howl at mid-screen
                if (x < screenWidth * 0.5f && !spared) {
                    wolfState = WolfState.HOWLING
                    howlTimer = 0f
                    DialogueBubbleManager.spawn(
                        text = RelationshipArcSystem.lineFor(context, EntityType.WOLF, RelationshipArcSystem.Event.THREAT),
                        anchorX = x + wolfW * 0.5f,
                        anchorY = y - 20f,
                        fillColor = Color.rgb(245, 228, 232),
                        borderColor = Color.rgb(150, 50, 50)
                    )
                    CameraSystem.shakeWolfHowl()   // Phase 15 shake
                    SfxManager.playHowl()
                }
            }
            WolfState.HOWLING -> {
                // Stand still during howl wind-up
                howlTimer += deltaTime
                if (howlTimer >= howlDuration) {
                    wolfState  = WolfState.CHARGING
                    velocityX  = -(walkSpeed * 2f + scrollSpeed * 0.3f) * relationshipTuning.aggressionMultiplier
                    DialogueBubbleManager.spawn(
                        text = RelationshipArcSystem.encounterCueLine(
                            context,
                            EntityType.WOLF,
                            RelationshipArcSystem.EncounterCue.WOLF_CHARGE
                        ),
                        anchorX = x + wolfW * 0.5f,
                        anchorY = y - 24f,
                        fillColor = Color.rgb(255, 232, 232),
                        borderColor = Color.rgb(170, 70, 70)
                    )
                }
            }
            WolfState.CHARGING -> {
                x += velocityX * deltaTime
                // Emit dust cloud from wolf feet during charge
                ParticleManager.emit(FxPreset.WOLF_CHARGE_DUST, x + wolfW, y + wolfH)
            }
            WolfState.SPARED -> {
                // Turn and trot right slowly
                x += walkSpeed * 0.5f * deltaTime
                if (x > screenWidth + wolfW) isActive = false
            }
        }

        hitbox.offsetTo(x + insetX, y + insetY)
        if (x < -wolfW - 50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        if (wolfState == WolfState.HOWLING || wolfState == WolfState.CHARGING) {
            canvas.drawRoundRect(x - 12f, y - 8f, x + wolfW + 12f, y + wolfH + 8f, 24f, 24f, threatPaint)
            canvas.drawRoundRect(x - 12f, y - 8f, x + wolfW + 12f, y + wolfH + 8f, 24f, 24f, threatStrokePaint)
        } else if (wolfState == WolfState.SPARED) {
            val trailStartX = x - wolfW * 0.42f
            val trailY = y + wolfH * 0.76f
            canvas.drawLine(trailStartX, trailY, x + wolfW * 0.12f, trailY, standDownTrailPaint)
            val auraPad = if (respectStandDownHistory) 20f else 12f
            canvas.drawRoundRect(
                x - auraPad,
                y - 6f,
                x + wolfW + auraPad,
                y + wolfH + 6f,
                24f,
                24f,
                respectPaint
            )
            canvas.drawRoundRect(
                x - auraPad,
                y - 6f,
                x + wolfW + auraPad,
                y + wolfH + 6f,
                24f,
                24f,
                respectStrokePaint
            )
        }
        val drawRect = RectF(x, y, x + wolfW, y + wolfH)
        sprite.draw(canvas, drawRect)
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        // Called when player fully passes without collision
        if (!spared && gameState.mercyHearts >= 8) {
            spared    = true
            wolfState = WolfState.SPARED
            gameState.addBonus(
                points = 220 + relationshipTuning.passBonusPoints + if (respectStandDownHistory) 70 else 0,
                seeds = 3 + relationshipTuning.passBonusSeeds + (if (warmBond) 1 else 0) + (if (respectStandDownHistory) 1 else 0)
            )
            PersistentMemoryManager.recordSpare(context, EntityType.WOLF)
            gameState.recordSpare()
            ParticleManager.emit(FxPreset.MERCY_STARS, x + wolfW * 0.5f, y + wolfH * 0.40f)
            ParticleManager.emit(FxPreset.SEED_COLLECT, x + wolfW * 0.5f, y + wolfH * 0.20f)
            if (respectStandDownHistory) {
                ParticleManager.emit(FxPreset.MERCY_STARS, x + wolfW * 0.32f, y + wolfH * 0.58f)
                ParticleManager.emit(FxPreset.MERCY_STARS, x + wolfW * 0.68f, y + wolfH * 0.58f)
            }
            DialogueBubbleManager.spawn(
                RelationshipArcSystem.lineFor(context, EntityType.WOLF, RelationshipArcSystem.Event.SPARE),
                x + wolfW * 0.5f,
                y - 20f,
                if (respectStandDownHistory) Color.rgb(226, 238, 248) else Color.rgb(232, 236, 245),
                if (respectStandDownHistory) Color.rgb(96, 118, 144) else Color.rgb(110, 110, 140)
            )
            return
        }

        if (!passRewarded && wolfState == WolfState.CHARGING) {
            passRewarded = true
            gameState.addBonus(
                points = 180 + relationshipTuning.passBonusPoints,
                seeds = 1 + relationshipTuning.passBonusSeeds
            )
            DialogueBubbleManager.spawn(
                RelationshipArcSystem.lineFor(context, EntityType.WOLF, RelationshipArcSystem.Event.PASS),
                x + wolfW * 0.5f,
                y - 20f,
                Color.rgb(236, 240, 255),
                Color.rgb(110, 110, 140)
            )
        }
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) {
            return CollisionResult.STUMBLE
        }
        val mercyPad = readability.mercyPaddingPx + relationshipTuning.mercyPaddingBonusPx
        if (intersectsExpanded(player.hitbox, hitbox, mercyPad)) {
            return CollisionResult.MERCY_MISS
        }
        return CollisionResult.NONE
    }
}
