package com.yourname.forest_run.entities.animals

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.PersistentMemoryManager
import com.yourname.forest_run.engine.RelationshipArcSystem
import com.yourname.forest_run.engine.SpriteSizing
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.entities.PlayerState
import com.yourname.forest_run.ui.DialogueBubbleManager

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

    private val foxH = 100f
    private val foxW = SpriteSizing.widthForHeight(sprite, foxH, minWidth = 82f)
    private val insetX = foxW * 0.12f
    private val insetY = foxH * 0.09f
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

    private enum class FoxState { WALKING, JUMPING, LANDING, SPARED }
    private var foxState = FoxState.WALKING

    private val walkSpeed = 200f
    private val detectionRange get() = foxW * 3f
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
                    DialogueBubbleManager.spawn("Next time...", x + foxW * 0.55f, y - 18f, Color.rgb(255, 236, 214), Color.rgb(190, 110, 55))
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

        detectionRect.set(x - detectionRange, y - 12f, x + foxW + 8f, y + foxH + 6f)
        hitbox.offsetTo(x + insetX, y + insetY)
        if (x < -foxW - 50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        if (!spared && foxState == FoxState.WALKING) {
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
            gameState.addBonus(points = 120, seeds = 2)
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
            gameState.addBonus(points = 150, seeds = 1)
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

        val playerIsJumping = player.state in listOf(
            PlayerState.JUMPING, PlayerState.JUMP_START, PlayerState.APEX
        )
        val inRange = (player.x + Player.BASE_WIDTH) > (x - detectionRange) &&
            player.x < (x + foxW)

        if (playerIsJumping && inRange) {
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

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        // Trigger mirror jump check during every frame while player is nearby
        tryMirrorJump(player)

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
