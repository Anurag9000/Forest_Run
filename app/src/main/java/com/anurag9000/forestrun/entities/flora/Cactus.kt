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
import com.anurag9000.forestrun.engine.SpriteSheet
import com.anurag9000.forestrun.engine.SpriteSizing
import com.anurag9000.forestrun.engine.SwayComponent
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.systems.FxPreset
import com.anurag9000.forestrun.systems.ParticleManager
import com.anurag9000.forestrun.ui.DialogueBubbleManager

class Cactus(
    context: Context,
    startX: Float,
    groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {
    private val readability = ReadabilityProfile.entityForGround(EntityType.CACTUS, groundY)
    private val cactusHeight = readability.heightPx
    private val cactusWidth = SpriteSizing.widthForHeight(
        sprite,
        cactusHeight,
        minWidth = readability.minWidthPx
    )
    private val insetX = cactusWidth * readability.hitInsetXRatio
    private val insetY = cactusHeight * readability.hitInsetYRatio
    private val drawRect = RectF()
    private val warningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(48, 255, 204, 154)
        style = Paint.Style.FILL
    }
    private val warningStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(124, 214, 160, 102)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private var warningPulse = 0f
    private var currentSway = 0f

    init {
        x = startX
        y = groundY - cactusHeight
        swayComponent = SwayComponent(speed = 1.0f, intensity = 4f)
        hitbox.set(x + insetX, y + insetY, x + cactusWidth - insetX, y + cactusHeight)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        warningPulse += deltaTime * 2.8f
        currentSway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x + insetX, y + insetY)
        sprite.update(deltaTime)
        if (x < -cactusWidth - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = currentSway
        val pad = readability.stagingPaddingPx
        val pulse = 0.68f + 0.32f * kotlin.math.sin(warningPulse)
        warningPaint.alpha = (34f + 30f * pulse).toInt().coerceIn(0, 255)
        warningStrokePaint.alpha = (92f + 54f * pulse).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(
            x - pad,
            y + cactusHeight * 0.12f,
            x + cactusWidth + pad,
            y + cactusHeight + 4f,
            16f,
            16f,
            warningPaint
        )
        canvas.drawRoundRect(
            x - pad,
            y + cactusHeight * 0.12f,
            x + cactusWidth + pad,
            y + cactusHeight + 4f,
            16f,
            16f,
            warningStrokePaint
        )
        drawRect.set(x, y, x + cactusWidth, y + cactusHeight)
        canvas.save()
        canvas.rotate(sway * 1.5f, x + cactusWidth / 2f, y + cactusHeight)
        sprite.draw(canvas, drawRect)
        canvas.restore()
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        gameState.addBonus(points = 95)
        val encounters = PersistentMemoryManager.getEncounterCount(context, EntityType.CACTUS)
        val hitCount = PersistentMemoryManager.getHitCount(context, EntityType.CACTUS)
        // EntityManager records the pass centrally after this callback.
        val cleanPasses = PersistentMemoryManager.getPassCount(context, EntityType.CACTUS) + 1
        ParticleManager.emit(
            FxPreset.SEED_COLLECT,
            x + cactusWidth * 0.5f,
            y + cactusHeight * 0.42f
        )
        DialogueBubbleManager.spawn(
            text = FloraEncounterFlavor.cactusPass(encounters, hitCount, cleanPasses),
            anchorX = x + cactusWidth * 0.5f,
            anchorY = y - 14f,
            fillColor = Color.rgb(255, 244, 220),
            borderColor = Color.rgb(168, 122, 72)
        )
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) return CollisionResult.HIT
        val mercyPad = readability.mercyPaddingPx
        val mercy = RectF(
            hitbox.left - mercyPad,
            hitbox.top - mercyPad,
            hitbox.right + mercyPad,
            hitbox.bottom + mercyPad
        )
        if (RectF.intersects(player.hitbox, mercy)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
