package com.yourname.forest_run.entities.birds

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.CameraSystem
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.ReadabilityProfile
import com.yourname.forest_run.engine.RelationshipEncounterTuning
import com.yourname.forest_run.engine.RelationshipArcSystem
import com.yourname.forest_run.engine.SpriteSizing
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.ui.DialogueBubbleManager
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Eagle (Phase 10)
 * Spawns off-screen top. Captures player's Y on spawn.
 * Dives diagonally toward that point at high speed.
 */
class Eagle(
    context: Context,
    startX: Float,
    private val screenWidth: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val readability = ReadabilityProfile.entityForGround(EntityType.EAGLE, groundY)
    private val relationshipTuning: RelationshipEncounterTuning =
        RelationshipArcSystem.encounterTuning(context, EntityType.EAGLE)
    private val birdH = readability.heightPx
    private val birdW = SpriteSizing.widthForHeight(sprite, birdH, minWidth = readability.minWidthPx)
    private val diveSpeed = (readability.movementSpeedPxPerSec * relationshipTuning.aggressionMultiplier).coerceAtLeast(640f)
    private val insetX = birdW * readability.hitInsetXRatio
    private val insetY = birdH * readability.hitInsetYRatio

    private var velX = 0f
    private var velY = 0f
    private var targetX = screenWidth * 0.25f
    private var targetY = groundY - 50f
    private var lockTimer = 0f
    private val lockDuration = (readability.telegraphDurationSec * relationshipTuning.telegraphMultiplier).coerceAtLeast(0.28f)
    private var isLocked = false
    private var markPrompted = false
    private var heldMark = true
    private var targetAnnounced = false
    private var markGraceTimer = 0f
    private val targetZoneRect = RectF()
    private val diveCorridorRect = RectF()
    private val markApproachRect = RectF()
    private val reticlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 255, 232, 202)
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val reticleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(96, 255, 182, 132)
        style = Paint.Style.FILL
    }
    private val corridorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 255, 224, 184)
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val corridorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(84, 255, 188, 140)
        style = Paint.Style.FILL
    }
    private val eagleGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(86, 255, 220, 160)
        style = Paint.Style.FILL
    }

    init {
        // Spawn off-screen top at a random horizontal position
        x = startX
        y = -birdH - 20f
        hitbox.set(x + insetX, y + insetY, x + birdW - insetX, y + birdH - insetY)

        // Seed a visible lane. The live player position replaces this
        // placeholder during the first interaction frame.
        updateTarget(screenWidth * 0.25f, groundY - 50f)

        // Phase 20: play screech SFX on spawn
    }

    /**
     * Call once after spawning to aim the eagle at the player's current position.
     */
    fun lockOnTarget(targetX: Float, targetY: Float) {
        updateTarget(targetX, targetY)
        announceTarget()
    }

    private fun updateTarget(targetX: Float, targetY: Float) {
        this.targetX = targetX
        this.targetY = targetY
        val dx = targetX - x
        val dy = targetY - y
        val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat().coerceAtLeast(1f)
        velX = dx / dist * diveSpeed
        velY = dy / dist * diveSpeed
        updateCueGeometry()
    }

    private fun announceTarget() {
        if (targetAnnounced) return
        targetAnnounced = true
        CameraSystem.shakeEagle()
        DialogueBubbleManager.spawn(
            RelationshipArcSystem.encounterCueLine(
                context,
                EntityType.EAGLE,
                RelationshipArcSystem.EncounterCue.EAGLE_LOCK
            ),
            targetX,
            targetY - 28f,
            Color.rgb(255, 234, 234),
            Color.rgb(180, 70, 70)
        )
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        if (!isLocked) {
            lockTimer += deltaTime
            if (lockTimer >= lockDuration) {
                isLocked = true
                markGraceTimer = 0.18f
            }
        } else {
            markGraceTimer = (markGraceTimer - deltaTime).coerceAtLeast(0f)
            x += velX * deltaTime
            y += velY * deltaTime
        }
        updateCueGeometry()
        hitbox.offsetTo(x + insetX, y + insetY)
        sprite.update(deltaTime)
        // Despawn when completely off screen (using +150f to allow time for the diagonal dive)
        if (y > groundY + birdH || x < -birdW - 50f || x > screenWidth + 150f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val corridorProgress = if (isLocked) 0.7f else 1f - (lockTimer / lockDuration).coerceIn(0f, 1f) * 0.18f
        corridorFillPaint.alpha = (84f * corridorProgress).toInt().coerceIn(36, 255)
        corridorPaint.alpha = (210f * corridorProgress).toInt().coerceIn(120, 255)
        canvas.drawRoundRect(diveCorridorRect, 18f, 18f, corridorFillPaint)
        canvas.drawRoundRect(diveCorridorRect, 18f, 18f, corridorPaint)
        if (!isLocked) {
            val radius = 22f + lockTimer * 34f
            canvas.drawCircle(targetX, targetY, radius, reticleFillPaint)
            reticlePaint.alpha = (220 * (1f - (lockTimer / lockDuration).coerceIn(0f, 1f))).toInt().coerceIn(60, 220)
            canvas.drawCircle(targetX, targetY, radius, reticlePaint)
            canvas.drawLine(targetX - radius - 8f, targetY, targetX - radius + 6f, targetY, reticlePaint)
            canvas.drawLine(targetX + radius - 6f, targetY, targetX + radius + 8f, targetY, reticlePaint)
            canvas.drawLine(targetX, targetY - radius - 8f, targetX, targetY - radius + 6f, reticlePaint)
            canvas.drawLine(targetX, targetY + radius - 6f, targetX, targetY + radius + 8f, reticlePaint)
        }
        canvas.drawRoundRect(targetZoneRect, 18f, 18f, reticleFillPaint)
        canvas.drawRoundRect(targetZoneRect, 18f, 18f, reticlePaint)
        canvas.drawLine(targetZoneRect.left + 8f, targetY, targetZoneRect.right - 8f, targetY, reticlePaint)
        canvas.drawLine(targetX, targetZoneRect.top + 8f, targetX, targetZoneRect.bottom - 8f, reticlePaint)
        canvas.drawCircle(x + birdW * 0.5f, y + birdH * 0.5f, birdW * 0.42f, eagleGlowPaint)
        val drawRect = RectF(x, y, x + birdW, y + birdH)
        sprite.draw(canvas, drawRect)
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        gameState.addBonus(
            points = 165 + relationshipTuning.passBonusPoints + if (heldMark) 18 else 0,
            seeds = 1 + relationshipTuning.passBonusSeeds + if (heldMark) 1 else 0
        )
        DialogueBubbleManager.spawn(
            text = if (heldMark) "Held the mark." else RelationshipArcSystem.lineFor(context, EntityType.EAGLE, RelationshipArcSystem.Event.PASS),
            anchorX = targetX,
            anchorY = targetY - 28f,
            fillColor = Color.rgb(255, 236, 236),
            borderColor = Color.rgb(180, 70, 70)
        )
    }

    override fun updatePlayerInteraction(player: Player, gameState: GameStateManager) {
        if (!isLocked) {
            updateTarget(player.hitbox.centerX(), player.hitbox.centerY())
            announceTarget()
        }

        markApproachRect.set(
            targetZoneRect.left - readability.stagingPaddingPx,
            targetZoneRect.top - readability.stagingPaddingPx,
            targetZoneRect.right + readability.stagingPaddingPx,
            targetZoneRect.bottom + readability.stagingPaddingPx
        )
        if (isLocked && !markPrompted && RectF.intersects(player.hitbox, markApproachRect)) {
            markPrompted = true
            DialogueBubbleManager.spawn(
                "Clear the mark.",
                targetX,
                targetY - 12f,
                Color.rgb(255, 234, 234),
                Color.rgb(180, 70, 70)
            )
        }
        if (isLocked && markGraceTimer <= 0f && RectF.intersects(player.hitbox, targetZoneRect)) {
            heldMark = false
        }
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) return CollisionResult.HIT

        val mercyPad = readability.mercyPaddingPx + relationshipTuning.mercyPaddingBonusPx
        val mercy = RectF(
            hitbox.left - mercyPad,
            hitbox.top - mercyPad,
            hitbox.right + mercyPad,
            hitbox.bottom + mercyPad
        )
        return if (RectF.intersects(player.hitbox, mercy)) {
            CollisionResult.MERCY_MISS
        } else {
            CollisionResult.NONE
        }
    }

    private fun updateCueGeometry() {
        val zoneHalfW = max(birdW * 0.96f, 48f)
        val zoneHalfH = max(birdH * 0.78f, 40f)
        targetZoneRect.set(
            targetX - zoneHalfW,
            targetY - zoneHalfH,
            targetX + zoneHalfW,
            targetY + zoneHalfH
        )
        val left = minOf(x + birdW * 0.5f, targetX) - birdW * 0.46f
        val right = maxOf(x + birdW * 0.5f, targetX) + birdW * 0.46f
        val top = minOf(y + birdH * 0.5f, targetY) - birdH * 0.44f
        val bottom = maxOf(y + birdH * 0.5f, targetY) + birdH * 0.44f
        diveCorridorRect.set(left, top, right, bottom)
    }
}
