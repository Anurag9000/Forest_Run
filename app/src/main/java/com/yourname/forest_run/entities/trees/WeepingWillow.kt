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
    private var curtainPulse = 0f

    init {
        x = startX
        y = groundY - treeHeight
        swayComponent = SwayComponent(speed = 0.5f, intensity = 20f)
        hitbox.set(x + treeWidth / 2f - trunkWidth / 2f, trunkTop,
                   x + treeWidth / 2f + trunkWidth / 2f, groundY)
        curtainHitbox.set(x + treeWidth * 0.08f, curtainTop, x + treeWidth * 0.92f, curtainBottom)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        curtainPulse += deltaTime * 2.2f
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x + treeWidth / 2f - trunkWidth / 2f, trunkTop)
        curtainHitbox.set(x + treeWidth * 0.08f + sway, curtainTop, x + treeWidth * 0.92f + sway, curtainBottom)
        sprite.update(deltaTime)
        if (x < -treeWidth - 50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f
        val pulse = 0.62f + 0.38f * kotlin.math.sin(curtainPulse)
        curtainPaint.alpha = (42f + 24f * pulse).toInt().coerceIn(0, 255)
        curtainStrokePaint.alpha = (84f + 50f * pulse).toInt().coerceIn(0, 255)
        shadowZonePaint.alpha = (18f + 24f * pulse).toInt().coerceIn(0, 255)
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
        repeat(5) { index ->
            val strandX = curtainHitbox.left + curtainHitbox.width() * ((index + 1f) / 6f)
            val strandDrift = sway * (0.3f + index * 0.08f)
            canvas.drawLine(
                strandX,
                curtainTop,
                strandX + strandDrift,
                curtainBottom,
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
        val mercyPad = readability.mercyPaddingPx
        val cm = RectF(curtainHitbox.left - mercyPad, curtainHitbox.top, curtainHitbox.right + mercyPad, curtainHitbox.bottom + mercyPad)
        val tm = RectF(hitbox.left - mercyPad, hitbox.top - mercyPad, hitbox.right + mercyPad, hitbox.bottom + mercyPad)
        if (RectF.intersects(player.hitbox, cm) || RectF.intersects(player.hitbox, tm)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
