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
 * Lily of the Valley — Phase 27: sprite rendered with sway rotation.
 */
class LilyOfValley(
    context: Context,
    startX: Float,
    groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val readability = ReadabilityProfile.entityForGround(EntityType.LILY_OF_VALLEY, groundY)
    private val floraHeight = readability.heightPx
    private val floraWidth  = SpriteSizing.widthForHeight(sprite, floraHeight, minWidth = readability.minWidthPx)
    private val hitInsetX   = floraWidth * readability.hitInsetXRatio
    private val hitTopY     = floraHeight * 0.45f
    private val drawRect    = RectF()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 214, 255, 236)
        style = Paint.Style.FILL
    }
    private val coreGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(130, 246, 255, 248)
        style = Paint.Style.FILL
    }
    private val lureStemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 206, 255, 238)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val seedTrapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(52, 248, 244, 188)
        style = Paint.Style.FILL
    }
    private val seedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 255, 244, 176)
        style = Paint.Style.FILL
    }
    private var glowPulse = 0f
    private var trapPulse = 0f

    init {
        x = startX
        y = groundY - floraHeight
        swayComponent = SwayComponent(speed = 1.5f, intensity = 5f)
        hitbox.set(x + hitInsetX, y + hitTopY, x + floraWidth - hitInsetX, y + floraHeight)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        glowPulse += deltaTime * 3.2f
        trapPulse += deltaTime * 2.4f
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x + hitInsetX + sway, y + hitTopY)
        sprite.update(deltaTime)
        if (x < -floraWidth - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f
        val pulse = 0.65f + 0.35f * kotlin.math.sin(glowPulse)
        val blossomX = x + floraWidth * 0.52f
        val blossomY = y + floraHeight * 0.28f
        val trapGlow = 0.7f + 0.3f * kotlin.math.sin(trapPulse)
        glowPaint.alpha = (60f + 45f * pulse).toInt().coerceIn(0, 255)
        coreGlowPaint.alpha = (105f + 65f * pulse).toInt().coerceIn(0, 255)
        lureStemPaint.alpha = (90f + 40f * pulse).toInt().coerceIn(0, 255)
        seedTrapPaint.alpha = (42f + 28f * trapGlow).toInt().coerceIn(0, 255)
        canvas.drawLine(blossomX, blossomY, x + floraWidth * 0.5f, y + floraHeight * 0.82f, lureStemPaint)
        canvas.drawRoundRect(
            x + floraWidth * 0.14f,
            y + floraHeight * 0.48f,
            x + floraWidth * 0.88f,
            y + floraHeight * 0.92f,
            16f,
            16f,
            seedTrapPaint
        )
        repeat(3) { index ->
            val step = index / 2f
            val seedY = blossomY + floraHeight * (0.16f + step * 0.14f)
            val seedRadius = floraWidth * (0.038f + 0.006f * index)
            seedPaint.alpha = (160f + 30f * pulse - index * 18f).toInt().coerceIn(0, 255)
            canvas.drawCircle(blossomX + sway * 0.2f, seedY, seedRadius, seedPaint)
        }
        canvas.drawCircle(blossomX, blossomY, floraWidth * (0.34f + 0.08f * pulse), glowPaint)
        canvas.drawCircle(blossomX, blossomY, floraWidth * 0.18f, coreGlowPaint)
        drawRect.set(x, y, x + floraWidth, y + floraHeight)
        canvas.save()
        canvas.rotate(sway * 2f, x + floraWidth / 2f, y + floraHeight)
        sprite.draw(canvas, drawRect)
        canvas.restore()
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        val encounters = PersistentMemoryManager.getEncounterCount(context, EntityType.LILY_OF_VALLEY)
        val repeatHits = PersistentMemoryManager.getHitCount(context, EntityType.LILY_OF_VALLEY)
        gameState.addBonus(points = 100, seeds = 1)
        ParticleManager.emit(FxPreset.LILY_NIGHT_GLOW, x + floraWidth * 0.5f, y + floraHeight * 0.25f)
        ParticleManager.emit(FxPreset.SEED_COLLECT, x + floraWidth * 0.5f, y + floraHeight * 0.58f)
        DialogueBubbleManager.spawn(
            FloraEncounterFlavor.lilyPass(encounters, repeatHits),
            x + floraWidth * 0.5f,
            y - 10f,
            Color.rgb(242, 255, 252),
            Color.rgb(110, 170, 150)
        )
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) return CollisionResult.HIT
        val mercyPad = readability.mercyPaddingPx
        val mercy = RectF(hitbox.left - mercyPad, hitbox.top - mercyPad, hitbox.right + mercyPad, hitbox.bottom + mercyPad)
        if (RectF.intersects(player.hitbox, mercy)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
