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
    private var gustPulse = 0f

    init {
        x = startX
        y = groundY - floraHeight
        swayComponent = SwayComponent(speed = 2.5f, intensity = 6f)
        hitbox.set(x + hitInsetX, y + hitTopY, x + floraWidth - hitInsetX, y + floraHeight)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        gustPulse += deltaTime * 3.5f
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x + hitInsetX + sway, y + hitTopY)
        sprite.update(deltaTime)
        if (x < -floraWidth - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f
        repeat(3) { index ->
            val phase = gustPulse + index * 0.9f
            val pulse = 0.65f + 0.35f * kotlin.math.sin(phase)
            val startY = y + floraHeight * (0.22f + index * 0.14f)
            val endY = startY - floraHeight * (0.09f + 0.03f * pulse)
            val endX = x + floraWidth * (0.82f + index * 0.03f) + sway * (1.6f + index * 0.35f)
            val paint = if (index == 0) windGuidePaint else leafDriftPaint
            paint.alpha = (88f + 72f * pulse - index * 10f).toInt().coerceIn(0, 255)
            canvas.drawLine(x + floraWidth * 0.18f, startY, endX, endY, paint)
        }
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
