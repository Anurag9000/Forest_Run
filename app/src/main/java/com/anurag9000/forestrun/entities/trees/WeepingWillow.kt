package com.anurag9000.forestrun.entities.trees

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.PersistentMemoryManager
import com.anurag9000.forestrun.engine.ReadabilityProfile
import com.anurag9000.forestrun.engine.SpriteSizing
import com.anurag9000.forestrun.engine.SpriteSheet
import com.anurag9000.forestrun.engine.SwayComponent
import com.anurag9000.forestrun.engine.TreeEncounterFlavor
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.systems.FxPreset
import com.anurag9000.forestrun.systems.ParticleManager
import com.anurag9000.forestrun.ui.DialogueBubbleManager

/**
 * Weeping Willow — Phase 27: sprite rendered at 2× height, sway applied via canvas rotation.
 * Player must duck under the curtain of leaves (lower curtainHitbox).
 * Trunk hitbox remains full-height.
 */
class WeepingWillow(
    context: Context,
    startX: Float,
    private val screenHeight: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val readability = ReadabilityProfile.entity(EntityType.WEEPING_WILLOW, screenHeight)
    private val treeHeight    = readability.heightPx
    private val treeWidth     = SpriteSizing.widthForHeight(sprite, treeHeight, minWidth = readability.minWidthPx)
    private val trunkWidth    = treeWidth * 0.18f
    private val trunkTop      = groundY - treeHeight * 0.42f
    private val curtainTop    = groundY - treeHeight * 0.78f
    private val curtainBottom = groundY - treeHeight * 0.16f

    private val curtainHitbox = RectF()
    private val canopyRect    = RectF()
    private val duckLaneRect  = RectF()
    private val drawRect      = RectF()
    private val curtainPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(48, 40, 92, 54)
        style = Paint.Style.FILL
    }
    private val curtainStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(124, 96, 148, 106)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val shadowZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(28, 18, 34, 28)
        style = Paint.Style.FILL
    }
    private val canopyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(52, 32, 78, 46)
        style = Paint.Style.FILL
    }
    private val canopyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(118, 86, 134, 94)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val duckLanePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(64, 214, 236, 180)
        style = Paint.Style.FILL
    }
    private val duckLaneBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(186, 244, 248, 210)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private var curtainPulse = 0f
    private var currentSway = 0f

    init {
        x = startX
        y = groundY - treeHeight
        swayComponent = SwayComponent(speed = 0.5f, intensity = 20f)
        updateGeometry()
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        curtainPulse += deltaTime * 2.2f
        currentSway = swayComponent?.getOffset(deltaTime) ?: 0f
        updateGeometry()
        sprite.update(deltaTime)
        if (x < -treeWidth - 50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = currentSway
        val pulse = 0.62f + 0.38f * kotlin.math.sin(curtainPulse)
        curtainPaint.alpha = (42f + 24f * pulse).toInt().coerceIn(0, 255)
        curtainStrokePaint.alpha = (84f + 50f * pulse).toInt().coerceIn(0, 255)
        shadowZonePaint.alpha = (18f + 24f * pulse).toInt().coerceIn(0, 255)
        canopyPaint.alpha = (38f + 24f * pulse).toInt().coerceIn(0, 255)
        canopyBorderPaint.alpha = (74f + 34f * pulse).toInt().coerceIn(0, 255)
        duckLanePaint.alpha = (48f + 28f * pulse).toInt().coerceIn(0, 255)
        duckLaneBorderPaint.alpha = (146f + 40f * pulse).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(canopyRect, 44f, 44f, canopyPaint)
        canvas.drawRoundRect(canopyRect, 44f, 44f, canopyBorderPaint)
        canvas.drawRoundRect(
            curtainHitbox.left - readability.stagingPaddingPx,
            curtainHitbox.top - readability.stagingPaddingPx * 0.35f,
            curtainHitbox.right + readability.stagingPaddingPx,
            groundY,
            28f,
            28f,
            shadowZonePaint
        )
        canvas.drawRoundRect(curtainHitbox, 24f, 24f, curtainPaint)
        canvas.drawRoundRect(curtainHitbox, 24f, 24f, curtainStrokePaint)
        canvas.drawRoundRect(duckLaneRect, 22f, 22f, duckLanePaint)
        canvas.drawRoundRect(duckLaneRect, 22f, 22f, duckLaneBorderPaint)
        repeat(3) { index ->
            val laneMarkerX = duckLaneRect.left + duckLaneRect.width() * ((index + 1f) / 4f)
            canvas.drawCircle(laneMarkerX, duckLaneRect.centerY(), treeWidth * 0.018f, duckLaneBorderPaint)
        }
        repeat(7) { index ->
            val strandX = curtainHitbox.left + curtainHitbox.width() * ((index + 1f) / 8f)
            val strandDrift = sway * (0.28f + index * 0.07f)
            canvas.drawLine(
                strandX,
                curtainTop,
                strandX + strandDrift,
                curtainBottom - treeHeight * 0.02f + (index % 2) * treeHeight * 0.015f,
                curtainStrokePaint
            )
        }
        drawRect.set(x, groundY - treeHeight, x + treeWidth, groundY)
        canvas.save()
        canvas.rotate(sway * 0.5f, x + treeWidth / 2f, groundY)
        sprite.draw(canvas, drawRect)
        canvas.restore()
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        val encounters = PersistentMemoryManager.getEncounterCount(context, EntityType.WEEPING_WILLOW)
        val repeatHits = PersistentMemoryManager.getHitCount(context, EntityType.WEEPING_WILLOW)
        gameState.addBonus(points = 145, seeds = 1)
        ParticleManager.emit(FxPreset.PETAL_DRIFT, x + treeWidth * 0.34f, curtainTop + treeHeight * 0.1f)
        ParticleManager.emit(FxPreset.SEED_COLLECT, x + treeWidth * 0.52f, curtainBottom - treeHeight * 0.12f)
        DialogueBubbleManager.spawn(
            TreeEncounterFlavor.willowPass(encounters, repeatHits),
            x + treeWidth * 0.5f,
            y + treeHeight * 0.08f,
            Color.rgb(226, 245, 226),
            Color.rgb(82, 122, 86)
        )
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox) ||
            RectF.intersects(player.hitbox, curtainHitbox)) return CollisionResult.HIT
        if (!duckLaneRect.isEmpty &&
            duckLaneRect.contains(player.hitbox.left, player.hitbox.top, player.hitbox.right, player.hitbox.bottom)
        ) {
            return CollisionResult.NONE
        }
        val mercyPad = readability.mercyPaddingPx
        if (
            intersectsExpanded(
                player.hitbox,
                curtainHitbox,
                leftPadding = mercyPad,
                topPadding = 0f,
                rightPadding = mercyPad,
                bottomPadding = mercyPad * 0.35f
            ) || intersectsExpanded(player.hitbox, hitbox, mercyPad)
        ) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }

    private fun updateGeometry() {
        hitbox.set(
            x + treeWidth / 2f - trunkWidth / 2f,
            trunkTop,
            x + treeWidth / 2f + trunkWidth / 2f,
            groundY
        )
        curtainHitbox.set(
            x + treeWidth * 0.06f,
            curtainTop,
            x + treeWidth * 0.94f,
            curtainBottom
        )
        canopyRect.set(
            x - treeWidth * 0.08f,
            y + treeHeight * 0.06f,
            x + treeWidth * 1.02f,
            curtainBottom - treeHeight * 0.02f
        )
        val laneInset = readability.stagingPaddingPx * 0.55f
        duckLaneRect.set(
            maxOf(curtainHitbox.left + laneInset, hitbox.right + readability.stagingPaddingPx * 0.35f),
            curtainBottom + readability.stagingPaddingPx * 0.18f,
            curtainHitbox.right - laneInset,
            groundY - readability.stagingPaddingPx * 0.28f
        )
    }
}
