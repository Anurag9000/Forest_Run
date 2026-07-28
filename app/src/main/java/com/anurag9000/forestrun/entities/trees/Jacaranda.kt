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
 * Jacaranda — Phase 27: sprite rendered with sway. Upper branch hitbox; player must duck to pass.
 */
class Jacaranda(
    context: Context,
    startX: Float,
    private val screenHeight: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val readability = ReadabilityProfile.entity(EntityType.JACARANDA, screenHeight)
    private val treeHeight   = readability.heightPx
    private val treeWidth    = SpriteSizing.widthForHeight(sprite, treeHeight, minWidth = readability.minWidthPx)
    private val trunkWidth   = treeWidth * 0.16f
    private val branchTop    = groundY - treeHeight * 0.72f
    private val branchBottom = groundY - treeHeight * 0.34f
    private val trunkTop     = groundY - treeHeight * 0.38f
    private val branchHitbox = RectF()
    private val canopyCoreRect = RectF()
    private val canopyBloomRect = RectF()
    private val undersideLaneRect = RectF()
    private val drawRect     = RectF()
    private val petalCurtainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(54, 218, 176, 255)
        style = Paint.Style.FILL
    }
    private val petalStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 190, 132, 232)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val canopyHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(42, 226, 188, 255)
        style = Paint.Style.FILL
    }
    private val canopyBloomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(34, 242, 210, 255)
        style = Paint.Style.FILL
    }
    private val undersideLanePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(58, 236, 230, 196)
        style = Paint.Style.FILL
    }
    private val undersideLaneBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(182, 248, 242, 214)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private var canopyPulse = 0f
    private var currentSway = 0f

    init {
        x = startX
        y = groundY - treeHeight
        swayComponent = SwayComponent(speed = 0.8f, intensity = 15f)
        updateGeometry()
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        canopyPulse += deltaTime * 2.6f
        currentSway = swayComponent?.getOffset(deltaTime) ?: 0f
        updateGeometry()
        sprite.update(deltaTime)
        if (x < -treeWidth - 50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = currentSway
        val pulse = 0.66f + 0.34f * kotlin.math.sin(canopyPulse)
        canopyHaloPaint.alpha = (24f + 26f * pulse).toInt().coerceIn(0, 255)
        canopyBloomPaint.alpha = (20f + 20f * pulse).toInt().coerceIn(0, 255)
        petalCurtainPaint.alpha = (42f + 24f * pulse).toInt().coerceIn(0, 255)
        petalStrokePaint.alpha = (88f + 44f * pulse).toInt().coerceIn(0, 255)
        undersideLanePaint.alpha = (44f + 24f * pulse).toInt().coerceIn(0, 255)
        undersideLaneBorderPaint.alpha = (150f + 36f * pulse).toInt().coerceIn(0, 255)
        canvas.drawOval(canopyBloomRect, canopyBloomPaint)
        canvas.drawOval(canopyCoreRect, canopyHaloPaint)
        canvas.drawRoundRect(branchHitbox, 28f, 28f, petalCurtainPaint)
        canvas.drawRoundRect(branchHitbox, 28f, 28f, petalStrokePaint)
        canvas.drawRoundRect(undersideLaneRect, 22f, 22f, undersideLanePaint)
        canvas.drawRoundRect(undersideLaneRect, 22f, 22f, undersideLaneBorderPaint)
        repeat(6) { index ->
            val driftX = branchHitbox.left + branchHitbox.width() * (0.10f + index * 0.14f)
            val startY = branchTop + branchHitbox.height() * (0.08f + (index % 3) * 0.14f)
            val endY = branchBottom + treeHeight * (0.06f + (index % 2) * 0.05f)
            canvas.drawLine(
                driftX,
                startY,
                driftX + sway * (0.10f + index * 0.03f),
                endY,
                petalStrokePaint
            )
            canvas.drawCircle(
                driftX + sway * 0.18f,
                startY + treeHeight * 0.06f,
                treeWidth * (0.014f + index * 0.003f),
                petalStrokePaint
            )
        }
        repeat(3) { index ->
            val laneMarkerX = undersideLaneRect.left + undersideLaneRect.width() * ((index + 1f) / 4f)
            canvas.drawCircle(laneMarkerX, undersideLaneRect.centerY(), treeWidth * 0.015f, undersideLaneBorderPaint)
        }
        drawRect.set(x, groundY - treeHeight, x + treeWidth, groundY)
        canvas.save()
        canvas.rotate(sway * 0.8f, x + treeWidth / 2f, groundY)
        sprite.draw(canvas, drawRect)
        canvas.restore()
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        val encounters = PersistentMemoryManager.getEncounterCount(context, EntityType.JACARANDA)
        val repeatHits = PersistentMemoryManager.getHitCount(context, EntityType.JACARANDA)
        gameState.addBonus(points = 145, seeds = 1)
        ParticleManager.emit(FxPreset.PETAL_DRIFT, x + treeWidth * 0.30f, branchTop)
        ParticleManager.emit(FxPreset.PETAL_DRIFT, x + treeWidth * 0.72f, branchTop + 12f)
        ParticleManager.emit(FxPreset.PETAL_DRIFT, x + treeWidth * 0.50f, branchTop - 12f)
        ParticleManager.emit(FxPreset.SEED_COLLECT, x + treeWidth * 0.5f, branchBottom + treeHeight * 0.04f)
        DialogueBubbleManager.spawn(
            TreeEncounterFlavor.jacarandaPass(encounters, repeatHits),
            x + treeWidth * 0.5f,
            y - 16f,
            Color.rgb(244, 234, 255),
            Color.rgb(130, 100, 170)
        )
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox) ||
            RectF.intersects(player.hitbox, branchHitbox)) return CollisionResult.HIT
        if (!undersideLaneRect.isEmpty &&
            undersideLaneRect.contains(player.hitbox.left, player.hitbox.top, player.hitbox.right, player.hitbox.bottom)
        ) {
            return CollisionResult.NONE
        }
        val mercyPad = readability.mercyPaddingPx
        val bm = RectF(
            branchHitbox.left - mercyPad,
            branchHitbox.top,
            branchHitbox.right + mercyPad,
            branchHitbox.bottom + mercyPad * 0.40f
        )
        val tm = RectF(hitbox.left - mercyPad, hitbox.top - mercyPad, hitbox.right + mercyPad, hitbox.bottom + mercyPad)
        if (RectF.intersects(player.hitbox, bm) || RectF.intersects(player.hitbox, tm)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }

    private fun updateGeometry() {
        hitbox.set(
            x + treeWidth / 2f - trunkWidth / 2f,
            trunkTop,
            x + treeWidth / 2f + trunkWidth / 2f,
            groundY
        )
        branchHitbox.set(
            x + treeWidth * 0.06f,
            branchTop,
            x + treeWidth * 0.94f,
            branchBottom
        )
        canopyCoreRect.set(
            branchHitbox.left - readability.stagingPaddingPx * 1.8f,
            branchTop - treeHeight * 0.14f,
            branchHitbox.right + readability.stagingPaddingPx * 1.8f,
            branchBottom + treeHeight * 0.12f
        )
        canopyBloomRect.set(
            branchHitbox.left - readability.stagingPaddingPx * 2.4f,
            branchTop - treeHeight * 0.20f,
            branchHitbox.right + readability.stagingPaddingPx * 2.4f,
            branchBottom + treeHeight * 0.18f
        )
        val laneInset = readability.stagingPaddingPx * 0.55f
        undersideLaneRect.set(
            maxOf(branchHitbox.left + laneInset, hitbox.right + readability.stagingPaddingPx * 0.30f),
            branchBottom + readability.stagingPaddingPx * 0.24f,
            branchHitbox.right - laneInset,
            groundY - readability.stagingPaddingPx * 0.24f
        )
    }
}
