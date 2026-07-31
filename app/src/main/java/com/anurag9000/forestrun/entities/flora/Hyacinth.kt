package com.anurag9000.forestrun.entities.flora

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.anurag9000.forestrun.engine.FloraEncounterFlavor
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.PersistentMemoryManager
import com.anurag9000.forestrun.engine.ReadabilityProfile
import com.anurag9000.forestrun.engine.SpriteSizing
import com.anurag9000.forestrun.engine.SpriteSheet
import com.anurag9000.forestrun.engine.SwayComponent
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.systems.FxPreset
import com.anurag9000.forestrun.systems.ParticleManager
import com.anurag9000.forestrun.ui.DialogueBubbleManager

/**
 * Hyacinth — Phase 27: sprite rendered with sway rotation.
 * Brushing collision → MERCY_MISS. Full hit → HIT.
 */
class Hyacinth(
    context: Context,
    startX: Float,
    groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val readability = ReadabilityProfile.entityForGround(EntityType.HYACINTH, groundY)
    private val floraHeight = readability.heightPx
    private val floraWidth  = SpriteSizing.widthForHeight(sprite, floraHeight, minWidth = readability.minWidthPx)
    private val hitInsetX   = floraWidth * readability.hitInsetXRatio
    private val hitTopY     = floraHeight * readability.hitInsetYRatio
    private val drawRect    = RectF()
    private val brushBox    = RectF()
    private val brushPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(64, 188, 120, 228)
        style = Paint.Style.FILL
    }
    private val rhythmPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(126, 228, 186, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private val beatLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(104, 236, 208, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val beatNodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 246, 218, 255)
        style = Paint.Style.FILL
    }
    private val hitBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(72, 246, 198, 255)
        style = Paint.Style.FILL
    }
    private val hitBandBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(184, 250, 226, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val beatGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(54, 230, 180, 255)
        style = Paint.Style.FILL
    }
    private var rhythmPulse = 0f
    private var currentSway = 0f

    init {
        x = startX
        y = groundY - floraHeight
        swayComponent = SwayComponent(speed = 1.0f, intensity = 7f)
        hitbox.set(x + hitInsetX, y + hitTopY, x + floraWidth - hitInsetX, y + floraHeight)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        rhythmPulse += deltaTime * 3f
        currentSway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x + hitInsetX, y + hitTopY)
        val pad = readability.stagingPaddingPx
        brushBox.set(hitbox.left - pad, hitbox.top - pad * 2.2f, hitbox.right + pad, hitbox.bottom + pad * 0.35f)
        sprite.update(deltaTime)
        if (x < -floraWidth - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = currentSway
        val centerX = x + floraWidth * 0.5f + sway * 0.2f
        val beatTop = y + floraHeight * 0.18f
        val beatMid = y + floraHeight * 0.38f
        val beatLow = y + floraHeight * 0.60f
        val pulseBase = 0.55f + 0.45f * kotlin.math.sin(rhythmPulse)
        brushPaint.alpha = (54f + 34f * pulseBase).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(brushBox, 12f, 12f, brushPaint)
        canvas.drawRoundRect(hitbox, 14f, 14f, hitBandPaint)
        canvas.drawRoundRect(hitbox, 14f, 14f, hitBandBorderPaint)
        canvas.drawLine(centerX, beatTop, centerX, beatLow + floraHeight * 0.06f, beatLinePaint)
        repeat(3) { index ->
            val phase = rhythmPulse + index * 0.8f
            val pulse = 0.55f + 0.45f * kotlin.math.sin(phase)
            val beatY = when (index) {
                0 -> beatTop
                1 -> beatMid
                else -> beatLow
            }
            val beatX = centerX + when (index) {
                0 -> -floraWidth * 0.08f
                1 -> floraWidth * 0.02f
                else -> floraWidth * 0.10f
            }
            rhythmPaint.alpha = (70f + 70f * pulse - index * 12f).toInt().coerceIn(0, 255)
            beatGlowPaint.alpha = (38f + 30f * pulse - index * 8f).toInt().coerceIn(0, 255)
            val radius = floraWidth * (0.14f + index * 0.08f + 0.03f * pulse)
            canvas.drawCircle(beatX, beatY, radius * 1.35f, beatGlowPaint)
            canvas.drawCircle(beatX, beatY, radius, rhythmPaint)
            canvas.drawCircle(beatX, beatY, floraWidth * (0.030f + 0.006f * index), beatNodePaint)
        }
        drawRect.set(x, y, x + floraWidth, y + floraHeight)
        canvas.save()
        canvas.rotate(sway * 1.5f, x + floraWidth / 2f, y + floraHeight)
        sprite.draw(canvas, drawRect)
        canvas.restore()
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        val encounters = PersistentMemoryManager.getEncounterCount(context, EntityType.HYACINTH)
        val repeatHits = PersistentMemoryManager.getHitCount(context, EntityType.HYACINTH)
        gameState.addBonus(points = 110)
        ParticleManager.emit(FxPreset.POLLEN_BURST, x + floraWidth * 0.5f, y + floraHeight * 0.32f)
        DialogueBubbleManager.spawn(
            FloraEncounterFlavor.hyacinthPass(encounters, repeatHits),
            x + floraWidth * 0.5f,
            y - 12f,
            Color.rgb(246, 232, 255),
            Color.rgb(150, 110, 190)
        )
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) return CollisionResult.HIT
        if (RectF.intersects(player.hitbox, brushBox)) return CollisionResult.MERCY_MISS
        val mercyPad = readability.mercyPaddingPx
        if (intersectsExpanded(player.hitbox, hitbox, mercyPad)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
