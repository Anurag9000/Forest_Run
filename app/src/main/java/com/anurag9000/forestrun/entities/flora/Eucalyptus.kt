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
 * Eucalyptus — Phase 27: fast-whipping sway animation via SpriteSheet.
 */
class Eucalyptus(
    context: Context,
    startX: Float,
    groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val readability = ReadabilityProfile.entityForGround(EntityType.EUCALYPTUS, groundY)
    private val floraHeight = readability.heightPx
    private val floraWidth  = SpriteSizing.widthForHeight(sprite, floraHeight, minWidth = readability.minWidthPx)
    private val hitInsetX   = floraWidth * readability.hitInsetXRatio
    private val hitTopY     = floraHeight * readability.hitInsetYRatio
    private val drawRect    = RectF()
    private val guideRect   = RectF()
    private val windGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 164, 222, 160)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val leafDriftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(82, 198, 242, 190)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val dangerBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 230, 246, 182)
        style = Paint.Style.FILL
    }
    private val dangerBandBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(184, 244, 252, 212)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val whipTipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(204, 246, 252, 214)
        style = Paint.Style.FILL
    }
    private val leanLanePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(76, 188, 234, 186)
        style = Paint.Style.FILL
    }
    private var gustPulse = 0f
    private var currentSway = 0f

    init {
        x = startX
        y = groundY - floraHeight
        swayComponent = SwayComponent(speed = 2.5f, intensity = 6f)
        hitbox.set(x + hitInsetX, y + hitTopY, x + floraWidth - hitInsetX, y + floraHeight)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        gustPulse += deltaTime * 3.5f
        currentSway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x + hitInsetX, y + hitTopY)
        val pad = readability.stagingPaddingPx
        guideRect.set(
            hitbox.left - pad * 1.2f,
            hitbox.top - pad * 1.4f,
            hitbox.right + pad * 2.2f,
            hitbox.bottom + pad * 0.4f
        )
        sprite.update(deltaTime)
        if (x < -floraWidth - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = currentSway
        val whipStartX = x + floraWidth * 0.18f
        val whipTipBaseX = x + floraWidth * 0.88f + sway * 1.6f
        val pulseBase = 0.62f + 0.38f * kotlin.math.sin(gustPulse)
        leanLanePaint.alpha = (44f + 30f * pulseBase).toInt().coerceIn(0, 255)
        dangerBandPaint.alpha = (54f + 26f * pulseBase).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(guideRect, 18f, 18f, leanLanePaint)
        canvas.drawRoundRect(hitbox, 14f, 14f, dangerBandPaint)
        canvas.drawRoundRect(hitbox, 14f, 14f, dangerBandBorderPaint)
        repeat(3) { index ->
            val phase = gustPulse + index * 0.9f
            val pulse = 0.65f + 0.35f * kotlin.math.sin(phase)
            val startY = y + floraHeight * (0.22f + index * 0.14f)
            val endY = startY - floraHeight * (0.09f + 0.03f * pulse)
            val endX = x + floraWidth * (0.84f + index * 0.025f) + sway * (1.8f + index * 0.38f)
            val paint = if (index == 0) windGuidePaint else leafDriftPaint
            paint.alpha = (88f + 72f * pulse - index * 10f).toInt().coerceIn(0, 255)
            canvas.drawLine(whipStartX, startY, endX, endY, paint)
            canvas.drawCircle(endX, endY, floraWidth * (0.040f + index * 0.012f), whipTipPaint)
        }
        canvas.drawCircle(whipTipBaseX, y + floraHeight * 0.30f, floraWidth * 0.055f, whipTipPaint)
        drawRect.set(x, y, x + floraWidth, y + floraHeight)
        canvas.save()
        canvas.rotate(sway * 3f, x + floraWidth / 2f, y + floraHeight)
        sprite.draw(canvas, drawRect)
        canvas.restore()
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        val repeatHits = PersistentMemoryManager.getHitCount(context, EntityType.EUCALYPTUS)
        gameState.addBonus(points = 120, seeds = 1)
        ParticleManager.emit(FxPreset.PETAL_DRIFT, x + floraWidth * 0.62f, y + floraHeight * 0.26f)
        DialogueBubbleManager.spawn(
            FloraEncounterFlavor.eucalyptusPass(repeatHits),
            x + floraWidth * 0.52f,
            y - 12f,
            Color.rgb(236, 255, 236),
            Color.rgb(96, 150, 108)
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
