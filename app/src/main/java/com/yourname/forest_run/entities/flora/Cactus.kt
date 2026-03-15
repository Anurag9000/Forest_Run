package com.yourname.forest_run.entities.flora

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
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.ui.DialogueBubbleManager

/**
 * Cactus — Phase 27: rendered via SpriteSheet. Sprite loaded from assets/sprites/plants/cactus_4frames.png.
 * Falls back to BitmapHelper placeholder automatically if file is missing.
 */
class Cactus(
    context: Context,
    startX: Float,
    groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val readability = ReadabilityProfile.entityForGround(EntityType.CACTUS, groundY)
    private val cactusHeight = readability.heightPx
    private val cactusWidth  = SpriteSizing.widthForHeight(sprite, cactusHeight, minWidth = readability.minWidthPx)
    private val insetX       = cactusWidth * readability.hitInsetXRatio
    private val insetY       = cactusHeight * readability.hitInsetYRatio
    private val drawRect     = RectF()
    private val warningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(48, 255, 204, 154)
        style = Paint.Style.FILL
    }
    private val warningStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(124, 214, 160, 102)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    init {
        x = startX
        y = groundY - cactusHeight
        swayComponent = SwayComponent(speed = 1.0f, intensity = 4f)
        hitbox.set(x + insetX, y + insetY, x + cactusWidth - insetX, y + cactusHeight)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x + insetX + sway, y + insetY)
        sprite.update(deltaTime)
        if (x < -cactusWidth - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f
        val pad = readability.stagingPaddingPx
        canvas.drawRoundRect(x - pad, y + cactusHeight * 0.12f, x + cactusWidth + pad, y + cactusHeight + 4f, 16f, 16f, warningPaint)
        canvas.drawRoundRect(x - pad, y + cactusHeight * 0.12f, x + cactusWidth + pad, y + cactusHeight + 4f, 16f, 16f, warningStrokePaint)
        drawRect.set(x, y, x + cactusWidth, y + cactusHeight)
        canvas.save()
        canvas.rotate(sway * 1.5f, x + cactusWidth / 2f, y + cactusHeight)
        sprite.draw(canvas, drawRect)
        canvas.restore()
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        gameState.addBonus(points = 95)
        val hitCount = PersistentMemoryManager.getHitCount(context, EntityType.CACTUS)
        DialogueBubbleManager.spawn(
            text = if (hitCount >= 2) "Not this time." else "Sharp read.",
            anchorX = x + cactusWidth * 0.5f,
            anchorY = y - 14f,
            fillColor = Color.rgb(255, 244, 220),
            borderColor = Color.rgb(168, 122, 72)
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
