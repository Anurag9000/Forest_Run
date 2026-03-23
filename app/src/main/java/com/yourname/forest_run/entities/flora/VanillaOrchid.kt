package com.yourname.forest_run.entities.flora

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.FloraEncounterFlavor
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.PersistentMemoryManager
import com.yourname.forest_run.engine.ReadabilityProfile
import com.yourname.forest_run.engine.SpriteSizing
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.engine.SwayComponent
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.systems.FxPreset
import com.yourname.forest_run.systems.ParticleManager
import com.yourname.forest_run.ui.DialogueBubbleManager

/**
 * Vanilla Orchid — Phase 27: rendered as two-segment sprite (low vine + overhead branch).
 * Two independent hitboxes with a safe gap between them.
 * Uses two separate draws of the same sprite: bottom half and top half scaled.
 */
class VanillaOrchid(
    context: Context,
    startX: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val readability = ReadabilityProfile.entityForGround(EntityType.VANILLA_ORCHID, groundY)
    private val floraHeight = readability.heightPx
    private val floraWidth  = SpriteSizing.widthForHeight(sprite, floraHeight, minWidth = readability.minWidthPx)

    // Two distinct hitboxes
    private val bottomHitbox = RectF()
    private val topHitbox    = RectF()
    private val threadRect   = RectF()

    private val bottomRect   = RectF()
    private val topRect      = RectF()
    private val safeGapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(56, 250, 240, 204)
        style = Paint.Style.FILL
    }
    private val safeGapStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(172, 255, 246, 196)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val hazardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(34, 228, 160, 194)
        style = Paint.Style.FILL
    }
    private val blossomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(92, 255, 245, 226)
        style = Paint.Style.FILL
    }
    private val threadMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(214, 255, 250, 216)
        style = Paint.Style.FILL
    }
    private val threadSpinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(168, 255, 246, 196)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    init {
        x = startX
        y = groundY - floraHeight
        swayComponent = SwayComponent(speed = 1.2f, intensity = 8f)
        updateCollisionGeometry(sway = 0f)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        updateCollisionGeometry(sway)
        sprite.update(deltaTime)
        if (x < -floraWidth - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f
        canvas.drawRoundRect(bottomHitbox, 14f, 14f, hazardPaint)
        canvas.drawRoundRect(topHitbox, 14f, 14f, hazardPaint)
        if (!threadRect.isEmpty) {
            canvas.drawRoundRect(threadRect, 18f, 18f, safeGapPaint)
            canvas.drawRoundRect(threadRect, 18f, 18f, safeGapStrokePaint)
            val centerX = threadRect.centerX()
            canvas.drawLine(centerX, threadRect.top + 8f, centerX, threadRect.bottom - 8f, threadSpinePaint)
            repeat(3) { index ->
                val t = index / 2f
                val dotY = threadRect.top + (threadRect.height() * t)
                val dotRadius = floraWidth * (0.030f + index * 0.004f)
                canvas.drawCircle(centerX, dotY, dotRadius, threadMarkerPaint)
            }
            canvas.drawCircle(threadRect.centerX(), threadRect.top - floraWidth * 0.018f, floraWidth * 0.050f, threadMarkerPaint)
            canvas.drawCircle(threadRect.centerX(), threadRect.bottom + floraWidth * 0.018f, floraWidth * 0.050f, threadMarkerPaint)
        }
        canvas.drawCircle(x + floraWidth * 0.74f, y + floraHeight * 0.16f, floraWidth * 0.09f, blossomPaint)

        // Bottom vine segment
        bottomRect.set(x, groundY - floraHeight * 0.30f, x + floraWidth * 0.64f, groundY)
        canvas.save()
        canvas.rotate(sway * 2f, x + floraWidth * 0.35f, groundY)
        sprite.draw(canvas, bottomRect)
        canvas.restore()

        // Top branch + flower
        topRect.set(x + floraWidth * 0.22f, y, x + floraWidth, groundY - floraHeight * 0.56f)
        canvas.save()
        canvas.rotate(sway * 0.8f, x + floraWidth * 0.62f, y)
        sprite.draw(canvas, topRect)
        canvas.restore()
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        val encounters = PersistentMemoryManager.getEncounterCount(context, EntityType.VANILLA_ORCHID)
        val repeatHits = PersistentMemoryManager.getHitCount(context, EntityType.VANILLA_ORCHID)
        gameState.addBonus(points = 150, seeds = 1)
        ParticleManager.emit(FxPreset.LILY_NIGHT_GLOW, x + floraWidth * 0.68f, y + floraHeight * 0.16f)
        ParticleManager.emit(FxPreset.POLLEN_BURST, x + floraWidth * 0.34f, groundY - floraHeight * 0.18f)
        DialogueBubbleManager.spawn(
            FloraEncounterFlavor.orchidPass(encounters, repeatHits),
            x + floraWidth * 0.55f,
            y - 14f,
            Color.rgb(255, 246, 252),
            Color.rgb(170, 120, 160)
        )
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, bottomHitbox) ||
            RectF.intersects(player.hitbox, topHitbox)) return CollisionResult.HIT
        if (!threadRect.isEmpty &&
            threadRect.contains(player.hitbox.left, player.hitbox.top, player.hitbox.right, player.hitbox.bottom)
        ) {
            return CollisionResult.NONE
        }
        val mercyPad = readability.mercyPaddingPx
        val bm = RectF(
            bottomHitbox.left - mercyPad,
            bottomHitbox.top - mercyPad * 0.45f,
            bottomHitbox.right + mercyPad * 0.50f,
            bottomHitbox.bottom + mercyPad
        )
        val tm = RectF(
            topHitbox.left - mercyPad * 0.50f,
            topHitbox.top - mercyPad,
            topHitbox.right + mercyPad,
            topHitbox.bottom + mercyPad * 0.45f
        )
        if (RectF.intersects(player.hitbox, bm) || RectF.intersects(player.hitbox, tm)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }

    private fun updateCollisionGeometry(sway: Float) {
        hitbox.set(x, y, x + floraWidth, groundY)
        bottomHitbox.set(
            x + floraWidth * 0.14f + sway * 0.75f,
            groundY - floraHeight * 0.22f,
            x + floraWidth * 0.58f + sway * 0.75f,
            groundY
        )
        topHitbox.set(
            x + floraWidth * 0.38f + sway * 0.35f,
            y,
            x + floraWidth * 0.88f + sway * 0.35f,
            groundY - floraHeight * 0.60f
        )

        val overlapLeft = maxOf(bottomHitbox.left, topHitbox.left)
        val overlapRight = minOf(bottomHitbox.right, topHitbox.right)
        val horizontalInset = readability.stagingPaddingPx * 0.22f
        val verticalInset = readability.stagingPaddingPx * 0.45f
        val safeTop = topHitbox.bottom + verticalInset
        val safeBottom = bottomHitbox.top - verticalInset
        if (overlapRight - overlapLeft > horizontalInset * 2f && safeBottom > safeTop) {
            threadRect.set(
                overlapLeft + horizontalInset,
                safeTop,
                overlapRight - horizontalInset,
                safeBottom
            )
        } else {
            threadRect.setEmpty()
        }
    }
}
