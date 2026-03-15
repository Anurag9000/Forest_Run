package com.yourname.forest_run.entities.animals

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.CameraSystem
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.PersistentMemoryManager
import com.yourname.forest_run.engine.RelationshipArcSystem
import com.yourname.forest_run.engine.SfxManager
import com.yourname.forest_run.engine.SpriteSizing
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.systems.FxPreset
import com.yourname.forest_run.systems.ParticleManager
import com.yourname.forest_run.ui.DialogueBubbleManager

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

    private val wolfH = 116f
    private val wolfW = SpriteSizing.widthForHeight(sprite, wolfH, minWidth = 92f)
    private val insetX = wolfW * 0.11f
    private val insetY = wolfH * 0.07f

    private enum class WolfState { WALKING, HOWLING, CHARGING, SPARED }
    private var wolfState = WolfState.WALKING

    private val walkSpeed = 150f
    private var howlTimer = 0f
    private val howlDuration = 1.0f

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
                    velocityX  = -(walkSpeed * 2f + scrollSpeed * 0.3f) // Double + partial scroll speed
                    DialogueBubbleManager.spawn(
                        text = "Here it comes.",
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
        }
        val drawRect = RectF(x, y, x + wolfW, y + wolfH)
        sprite.draw(canvas, drawRect)
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        // Called when player fully passes without collision
        if (!spared && gameState.mercyHearts >= 8) {
            spared    = true
            wolfState = WolfState.SPARED
            gameState.addBonus(points = 200, seeds = 3)
            PersistentMemoryManager.recordSpare(context, EntityType.WOLF)
            gameState.recordSpare()
            DialogueBubbleManager.spawn(
                RelationshipArcSystem.lineFor(context, EntityType.WOLF, RelationshipArcSystem.Event.SPARE),
                x + wolfW * 0.5f,
                y - 20f,
                Color.rgb(232, 236, 245),
                Color.rgb(110, 110, 140)
            )
            return
        }

        if (!passRewarded && wolfState == WolfState.CHARGING) {
            passRewarded = true
            gameState.addBonus(points = 180, seeds = 1)
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
        val mercy = RectF(hitbox.left - 12f, hitbox.top - 12f, hitbox.right + 12f, hitbox.bottom + 12f)
        if (RectF.intersects(player.hitbox, mercy)) {
            return CollisionResult.MERCY_MISS
        }
        return CollisionResult.NONE
    }
}
