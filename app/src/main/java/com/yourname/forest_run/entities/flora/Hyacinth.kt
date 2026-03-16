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
        color = Color.argb(52, 188, 120, 228)
        style = Paint.Style.FILL
    }
    private val rhythmPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(126, 228, 186, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private var rhythmPulse = 0f

    init {
        x = startX
        y = groundY - floraHeight
        swayComponent = SwayComponent(speed = 1.0f, intensity = 7f)
        hitbox.set(x + hitInsetX, y + hitTopY, x + floraWidth - hitInsetX, y + floraHeight)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        rhythmPulse += deltaTime * 3f
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x + hitInsetX + sway, y + hitTopY)
        val pad = readability.stagingPaddingPx
        brushBox.set(hitbox.left - pad, hitbox.top - pad * 1.4f, hitbox.right + pad, hitbox.bottom)
        sprite.update(deltaTime)
        if (x < -floraWidth - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f
        canvas.drawRoundRect(brushBox, 12f, 12f, brushPaint)
        repeat(3) { index ->
            val phase = rhythmPulse + index * 0.8f
            val pulse = 0.55f + 0.45f * kotlin.math.sin(phase)
            rhythmPaint.alpha = (70f + 70f * pulse - index * 12f).toInt().coerceIn(0, 255)
            val radius = floraWidth * (0.16f + index * 0.11f + 0.03f * pulse)
            canvas.drawCircle(
                x + floraWidth * 0.5f + sway * 0.2f,
                y + floraHeight * (0.22f + index * 0.16f),
                radius,
                rhythmPaint
            )
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
        val mercy = RectF(hitbox.left - mercyPad, hitbox.top - mercyPad, hitbox.right + mercyPad, hitbox.bottom + mercyPad)
        if (RectF.intersects(player.hitbox, mercy)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
