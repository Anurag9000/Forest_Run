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
 * Cherry Blossom — Phase 27: mid-height branch hitbox, sprite rendered with gentle sway.
 */
class CherryBlossom(
    context: Context,
    startX: Float,
    private val screenHeight: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val readability = ReadabilityProfile.entity(EntityType.CHERRY_BLOSSOM, screenHeight)
    private val treeHeight       = readability.heightPx
    private val treeWidth        = SpriteSizing.widthForHeight(sprite, treeHeight, minWidth = readability.minWidthPx)
    private val trunkWidth       = treeWidth * 0.16f
    private val branchHeightLow  = groundY - treeHeight * 0.26f
    private val branchHeightHigh = groundY - treeHeight * 0.58f
    private val trunkTop         = groundY - treeHeight * 0.34f
    private val branchHitbox    = RectF()
    private val stormVeilRect   = RectF()
    private val drawRect        = RectF()
    private val gustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(46, 255, 222, 236)
        style = Paint.Style.FILL
    }
    private val gustStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(112, 238, 164, 194)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val gustTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(88, 255, 206, 224)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val gustCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(74, 255, 234, 242)
        style = Paint.Style.FILL
    }
    private var gustPulse = 0f
    private var currentSway = 0f

    init {
        x = startX
        y = groundY - treeHeight
        swayComponent = SwayComponent(speed = 0.6f, intensity = 12f)
        updateGeometry()
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        gustPulse += deltaTime * 2.7f
        currentSway = swayComponent?.getOffset(deltaTime) ?: 0f
        updateGeometry()
        sprite.update(deltaTime)
        if (x < -treeWidth - 50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = currentSway
        val pad = readability.stagingPaddingPx
        val pulse = 0.64f + 0.36f * kotlin.math.sin(gustPulse)
        gustPaint.alpha = (36f + 24f * pulse).toInt().coerceIn(0, 255)
        gustStrokePaint.alpha = (90f + 42f * pulse).toInt().coerceIn(0, 255)
        gustTrailPaint.alpha = (72f + 40f * pulse).toInt().coerceIn(0, 255)
        gustCorePaint.alpha = (52f + 34f * pulse).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(stormVeilRect, 28f, 28f, gustPaint)
        canvas.drawRoundRect(stormVeilRect, 28f, 28f, gustStrokePaint)
        canvas.drawRoundRect(branchHitbox, 22f, 22f, gustCorePaint)
        repeat(4) { index ->
            val yOffset = stormVeilRect.top + stormVeilRect.height() * (0.16f + index * 0.19f)
            canvas.drawLine(
                stormVeilRect.left - pad * (1.0f + index * 0.18f),
                yOffset,
                stormVeilRect.right + pad * (0.8f + index * 0.12f),
                yOffset - treeHeight * (0.04f + index * 0.012f),
                gustTrailPaint
            )
        }
        repeat(3) { index ->
            val pressureY = branchHitbox.top + branchHitbox.height() * ((index + 1f) / 4f)
            canvas.drawLine(branchHitbox.left + pad * 0.2f, pressureY, branchHitbox.right - pad * 0.2f, pressureY, gustStrokePaint)
        }
        drawRect.set(x, groundY - treeHeight, x + treeWidth, groundY)
        canvas.save()
        canvas.rotate(sway * 0.6f, x + treeWidth / 2f, groundY)
        sprite.draw(canvas, drawRect)
        canvas.restore()
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        val encounters = PersistentMemoryManager.getEncounterCount(context, EntityType.CHERRY_BLOSSOM)
        val repeatHits = PersistentMemoryManager.getHitCount(context, EntityType.CHERRY_BLOSSOM)
        gameState.addBonus(points = 150, seeds = 1)
        ParticleManager.emit(FxPreset.PETAL_DRIFT, x + treeWidth * 0.24f, branchHeightHigh)
        ParticleManager.emit(FxPreset.PETAL_DRIFT, x + treeWidth * 0.78f, branchHeightHigh + 18f)
        ParticleManager.emit(FxPreset.PETAL_DRIFT, x + treeWidth * 0.52f, branchHeightHigh - 10f)
        ParticleManager.emit(FxPreset.POLLEN_BURST, x + treeWidth * 0.5f, branchHeightLow)
        ParticleManager.emit(FxPreset.SEED_COLLECT, x + treeWidth * 0.5f, branchHeightLow + treeHeight * 0.04f)
        DialogueBubbleManager.spawn(
            TreeEncounterFlavor.cherryPass(encounters, repeatHits),
            x + treeWidth * 0.5f,
            y - 14f,
            Color.rgb(255, 238, 244),
            Color.rgb(190, 120, 150)
        )
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox) ||
            RectF.intersects(player.hitbox, branchHitbox)) return CollisionResult.HIT
        val mercyPad = readability.mercyPaddingPx
        if (
            intersectsExpanded(
                player.hitbox,
                stormVeilRect,
                horizontalPadding = mercyPad * 0.25f,
                verticalPadding = mercyPad * 0.45f
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
        branchHitbox.set(
            x + treeWidth * 0.14f,
            branchHeightHigh + treeHeight * 0.04f,
            x + treeWidth * 0.86f,
            branchHeightLow - treeHeight * 0.04f
        )
        stormVeilRect.set(
            x + treeWidth * 0.06f,
            branchHeightHigh - readability.stagingPaddingPx * 0.25f,
            x + treeWidth * 0.94f,
            branchHeightLow + readability.stagingPaddingPx * 0.25f
        )
    }
}
