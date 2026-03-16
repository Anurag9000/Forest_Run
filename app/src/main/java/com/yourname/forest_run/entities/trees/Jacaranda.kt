package com.yourname.forest_run.entities.trees

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.PersistentMemoryManager
import com.yourname.forest_run.engine.ReadabilityProfile
import com.yourname.forest_run.engine.SpriteSizing
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.engine.SwayComponent
import com.yourname.forest_run.engine.TreeEncounterFlavor
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.systems.FxPreset
import com.yourname.forest_run.systems.ParticleManager
import com.yourname.forest_run.ui.DialogueBubbleManager

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
    private var canopyPulse = 0f

    init {
        x = startX
        y = groundY - treeHeight
        swayComponent = SwayComponent(speed = 0.8f, intensity = 15f)
        hitbox.set(x + treeWidth / 2f - trunkWidth / 2f, trunkTop,
                   x + treeWidth / 2f + trunkWidth / 2f, groundY)
        branchHitbox.set(x + treeWidth * 0.08f, branchTop, x + treeWidth * 0.92f, branchBottom)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        canopyPulse += deltaTime * 2.6f
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x + treeWidth / 2f - trunkWidth / 2f, trunkTop)
        branchHitbox.set(x + treeWidth * 0.08f + sway, branchTop, x + treeWidth * 0.92f + sway, branchBottom)
        sprite.update(deltaTime)
        if (x < -treeWidth - 50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f
        val pulse = 0.66f + 0.34f * kotlin.math.sin(canopyPulse)
        canopyHaloPaint.alpha = (24f + 26f * pulse).toInt().coerceIn(0, 255)
        petalCurtainPaint.alpha = (42f + 24f * pulse).toInt().coerceIn(0, 255)
        petalStrokePaint.alpha = (88f + 44f * pulse).toInt().coerceIn(0, 255)
        canvas.drawOval(
            branchHitbox.left - readability.stagingPaddingPx * 1.4f,
            branchTop - treeHeight * 0.1f,
            branchHitbox.right + readability.stagingPaddingPx * 1.4f,
            branchBottom + treeHeight * 0.08f,
            canopyHaloPaint
        )
        canvas.drawRoundRect(branchHitbox, 28f, 28f, petalCurtainPaint)
        canvas.drawRoundRect(branchHitbox, 28f, 28f, petalStrokePaint)
        repeat(4) { index ->
            val driftX = branchHitbox.left + branchHitbox.width() * (0.18f + index * 0.2f)
            val driftY = branchTop + branchHitbox.height() * (0.12f + index * 0.16f)
            canvas.drawCircle(driftX + sway * 0.15f, driftY, treeWidth * (0.018f + index * 0.006f), petalStrokePaint)
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
        val mercyPad = readability.mercyPaddingPx
        val bm = RectF(branchHitbox.left - mercyPad, branchHitbox.top, branchHitbox.right + mercyPad, branchHitbox.bottom + mercyPad)
        val tm = RectF(hitbox.left - mercyPad, hitbox.top - mercyPad, hitbox.right + mercyPad, hitbox.bottom + mercyPad)
        if (RectF.intersects(player.hitbox, bm) || RectF.intersects(player.hitbox, tm)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
