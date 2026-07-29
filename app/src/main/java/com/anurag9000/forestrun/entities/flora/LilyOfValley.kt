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
    private val hitTopY     = floraHeight * 0.58f
    private val drawRect    = RectF()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(92, 214, 255, 236)
        style = Paint.Style.FILL
    }
    private val coreGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(156, 246, 255, 248)
        style = Paint.Style.FILL
    }
    private val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(52, 198, 244, 232)
        style = Paint.Style.FILL
    }
    private val lureColumnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(86, 224, 255, 244)
        style = Paint.Style.FILL
    }
    private val lureStemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 206, 255, 238)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val seedTrapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(78, 248, 244, 188)
        style = Paint.Style.FILL
    }
    private val trapBandBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(176, 252, 242, 208)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val trapBandGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(58, 254, 240, 174)
        style = Paint.Style.FILL
    }
    private val lureBeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(198, 255, 246, 186)
        style = Paint.Style.FILL
    }
    private val seedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 255, 244, 176)
        style = Paint.Style.FILL
    }
    private var glowPulse = 0f
    private var trapPulse = 0f
    private var currentSway = 0f

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
        currentSway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x + hitInsetX, y + hitTopY)
        sprite.update(deltaTime)
        if (x < -floraWidth - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = currentSway
        val pulse = 0.65f + 0.35f * kotlin.math.sin(glowPulse)
        val blossomX = x + floraWidth * 0.52f
        val blossomY = y + floraHeight * 0.28f
        val trapGlow = 0.7f + 0.3f * kotlin.math.sin(trapPulse)
        val trapTop = y + floraHeight * 0.56f
        val trapBottom = y + floraHeight * 0.94f
        val trapLeft = x + floraWidth * 0.12f
        val trapRight = x + floraWidth * 0.90f
        glowPaint.alpha = (76f + 52f * pulse).toInt().coerceIn(0, 255)
        coreGlowPaint.alpha = (128f + 72f * pulse).toInt().coerceIn(0, 255)
        outerGlowPaint.alpha = (38f + 28f * pulse).toInt().coerceIn(0, 255)
        lureStemPaint.alpha = (104f + 46f * pulse).toInt().coerceIn(0, 255)
        lureColumnPaint.alpha = (54f + 32f * pulse).toInt().coerceIn(0, 255)
        seedTrapPaint.alpha = (62f + 34f * trapGlow).toInt().coerceIn(0, 255)
        trapBandGlowPaint.alpha = (44f + 34f * trapGlow).toInt().coerceIn(0, 255)
        lureBeadPaint.alpha = (164f + 44f * pulse).toInt().coerceIn(0, 255)

        canvas.drawCircle(blossomX, blossomY, floraWidth * (0.48f + 0.10f * pulse), outerGlowPaint)
        canvas.drawOval(
            blossomX - floraWidth * 0.14f,
            blossomY + floraHeight * 0.02f,
            blossomX + floraWidth * 0.14f,
            trapTop - floraHeight * 0.02f,
            lureColumnPaint
        )
        canvas.drawLine(blossomX, blossomY, x + floraWidth * 0.5f, trapBottom, lureStemPaint)
        repeat(4) { index ->
            val beadT = index / 3f
            val seedY = blossomY + floraHeight * (0.12f + beadT * 0.34f)
            val seedRadius = floraWidth * (0.034f + 0.005f * (3 - index))
            canvas.drawCircle(blossomX + sway * 0.18f, seedY, seedRadius, lureBeadPaint)
        }
        canvas.drawRoundRect(
            trapLeft,
            trapTop,
            trapRight,
            trapBottom,
            16f,
            16f,
            seedTrapPaint
        )
        canvas.drawRoundRect(
            trapLeft - 4f,
            trapTop + floraHeight * 0.02f,
            trapRight + 4f,
            trapBottom + 2f,
            18f,
            18f,
            trapBandGlowPaint
        )
        canvas.drawRoundRect(
            trapLeft,
            trapTop,
            trapRight,
            trapBottom,
            16f,
            16f,
            trapBandBorderPaint
        )
        repeat(3) { index ->
            val step = index / 2f
            val seedY = trapTop + floraHeight * (0.06f + step * 0.10f)
            val seedRadius = floraWidth * (0.040f + 0.005f * index)
            seedPaint.alpha = (160f + 30f * pulse - index * 18f).toInt().coerceIn(0, 255)
            canvas.drawCircle(blossomX + sway * 0.18f, seedY, seedRadius, seedPaint)
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
        if (intersectsExpanded(player.hitbox, hitbox, mercyPad)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
