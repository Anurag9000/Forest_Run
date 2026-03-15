package com.yourname.forest_run.entities.birds

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.ReadabilityProfile
import com.yourname.forest_run.engine.RelationshipArcSystem
import com.yourname.forest_run.engine.SpriteSizing
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.ui.DialogueBubbleManager

/**
 * Owl (Phase 10) — Night only.
 * Perches high-mid. Dives toward player if player jumps while owl is on screen.
 * If player doesn't jump, owl stays put and is passable underneath.
 */
class Owl(
    context: Context,
    startX: Float,
    private val groundY: Float,
    private val idleSprite: SpriteSheet,
    private val actionSprite: SpriteSheet
) : Entity(context) {

    private val readability = ReadabilityProfile.entityForGround(EntityType.OWL, groundY)
    private val birdH = readability.heightPx
    private val birdW = SpriteSizing.widthForHeight(actionSprite, birdH, minWidth = readability.minWidthPx)
    private val perchY = groundY * 0.2f // High-up perch
    private val insetX = birdW * readability.hitInsetXRatio
    private val insetY = birdH * readability.hitInsetYRatio

    private enum class OwlState { SLEEPING, ALERT, DIVING }
    private var owlState = OwlState.SLEEPING
    private var currentSprite = idleSprite

    private var velX = 0f
    private var velY = 0f
    private var alertTimer = 0f
    private var pendingTargetX = 0f
    private var pendingTargetY = 0f
    private var hasWarned = false
    private val alertPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 205, 120)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val alertFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(42, 255, 184, 82)
        style = Paint.Style.FILL
    }
    private val eyeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(132, 255, 184, 72)
        style = Paint.Style.FILL
    }

    init {
        x = startX
        y = perchY
        hitbox.set(x + insetX, y + insetY, x + birdW - insetX, y + birdH - insetY)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        currentSprite.update(deltaTime)

        when (owlState) {
            OwlState.SLEEPING -> {
                x -= scrollSpeed * deltaTime
                // Amber eye glow is handled by particle manager in Phase 14
            }
            OwlState.ALERT -> {
                x -= scrollSpeed * deltaTime
                alertTimer += deltaTime
                if (alertTimer >= readability.telegraphDurationSec) {
                    triggerDive(pendingTargetX, pendingTargetY)
                }
            }
            OwlState.DIVING -> {
                x += velX * deltaTime
                y += velY * deltaTime
            }
        }

        hitbox.offsetTo(x + insetX, y + insetY)
        if (x < -birdW - 50f || y > groundY + birdH) isActive = false
    }

    /**
     * Call from the EntityManager or Player physics when the player jumps while the owl is visible.
     * Triggers the dive toward the player's current position.
     */
    fun triggerDive(targetX: Float, targetY: Float) {
        if (owlState == OwlState.SLEEPING || owlState == OwlState.ALERT) {
            owlState = OwlState.DIVING
            currentSprite = actionSprite
            currentSprite.reset()
            val diveSpeed = readability.movementSpeedPxPerSec.coerceAtLeast(560f)
            val dx = targetX - x
            val dy = targetY - y
            val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat().coerceAtLeast(1f)
            velX = dx / dist * diveSpeed
            velY = dy / dist * diveSpeed
        }
    }

    override fun draw(canvas: Canvas) {
        val drawRect = RectF(x, y, x + birdW, y + birdH)
        if (owlState == OwlState.SLEEPING || owlState == OwlState.ALERT) {
            canvas.drawCircle(drawRect.centerX(), drawRect.centerY(), birdW * 0.48f, eyeGlowPaint)
        }
        if (owlState == OwlState.ALERT) {
            val radius = birdW * (0.48f + alertTimer / readability.telegraphDurationSec.coerceAtLeast(0.1f) * 0.14f)
            canvas.drawCircle(drawRect.centerX(), drawRect.centerY(), radius, alertFillPaint)
            alertPaint.alpha = (180 * (1f - (alertTimer / readability.telegraphDurationSec.coerceAtLeast(0.1f)).coerceIn(0f, 1f))).toInt().coerceIn(40, 180)
            canvas.drawCircle(drawRect.centerX(), drawRect.centerY(), radius, alertPaint)
        }
        currentSprite.draw(canvas, drawRect)
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        gameState.addBonus(points = 150)
        DialogueBubbleManager.spawn(
            text = RelationshipArcSystem.lineFor(context, EntityType.OWL, RelationshipArcSystem.Event.PASS),
            anchorX = x + birdW * 0.5f,
            anchorY = y - 18f,
            fillColor = Color.rgb(255, 244, 220),
            borderColor = Color.rgb(176, 126, 70)
        )
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        // If player jumps, owl should begin its dive (checked in EntityManager/GameView)
        if (player.state in listOf(
                com.yourname.forest_run.entities.PlayerState.JUMPING,
                com.yourname.forest_run.entities.PlayerState.APEX,
                com.yourname.forest_run.entities.PlayerState.JUMP_START
            ) && owlState == OwlState.SLEEPING
        ) {
            owlState = OwlState.ALERT
            alertTimer = 0f
            pendingTargetX = player.x
            pendingTargetY = player.y
            if (!hasWarned) {
                DialogueBubbleManager.spawn(
                    RelationshipArcSystem.lineFor(context, EntityType.OWL, RelationshipArcSystem.Event.THREAT),
                    x + birdW * 0.5f,
                    y - 14f,
                    Color.rgb(255, 242, 220),
                    Color.rgb(170, 120, 60)
                )
                hasWarned = true
            }
        }

        if (owlState == OwlState.DIVING && RectF.intersects(player.hitbox, hitbox)) {
            return CollisionResult.HIT
        }

        val mercyPad = readability.mercyPaddingPx
        val mercy = RectF(hitbox.left - mercyPad, hitbox.top - mercyPad, hitbox.right + mercyPad, hitbox.bottom + mercyPad)
        if (owlState == OwlState.DIVING && RectF.intersects(player.hitbox, mercy)) {
            return CollisionResult.MERCY_MISS
        }
        return CollisionResult.NONE
    }
}
