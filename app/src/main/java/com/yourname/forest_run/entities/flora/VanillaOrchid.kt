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
 * Vanilla Orchid — Phase 27: rendered as two-segment sprite (low vine + overhead branch).
 * Two independent hitboxes with a safe gap between them.
 * Uses two separate draws of the same sprite: bottom half and top half scaled.
 */
class VanillaOrchid(
    context: Context,
    startX: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val readability = ReadabilityProfile.entityForGround(EntityType.VANILLA_ORCHID, groundY)
    private val floraHeight = readability.heightPx
    private val floraWidth  = SpriteSizing.widthForHeight(sprite, floraHeight, minWidth = readability.minWidthPx)

    // Two distinct hitboxes
    private val bottomHitbox = RectF()
    private val topHitbox    = RectF()

    private val bottomRect   = RectF()
    private val topRect      = RectF()
    private val safeGapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(44, 248, 234, 196)
        style = Paint.Style.FILL
    }
    private val safeGapStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(132, 255, 244, 190)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val hazardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(34, 228, 160, 194)
        style = Paint.Style.FILL
    }
    private val blossomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(92, 255, 245, 226)
        style = Paint.Style.FILL
    }

    init {
        x = startX
        y = groundY - floraHeight
        swayComponent = SwayComponent(speed = 1.2f, intensity = 8f)
        bottomHitbox.set(x + floraWidth * 0.18f, groundY - floraHeight * 0.26f, x + floraWidth * 0.56f, groundY)
        topHitbox.set(x + floraWidth * 0.34f, y, x + floraWidth - floraWidth * 0.12f, groundY - floraHeight * 0.58f)
        hitbox.set(x, y, x + floraWidth, groundY)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x, y)
        bottomHitbox.offsetTo(x + floraWidth * 0.18f + sway, groundY - floraHeight * 0.26f)
        topHitbox.offsetTo(x + floraWidth * 0.34f + sway * 0.5f, y)
        sprite.update(deltaTime)
        if (x < -floraWidth - 20f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f
        val safeInset = readability.stagingPaddingPx
        val safeLeft = bottomHitbox.left + safeInset
        val safeRight = topHitbox.right - safeInset
        val safeTop = topHitbox.bottom
        val safeBottom = bottomHitbox.top
        canvas.drawRoundRect(bottomHitbox, 14f, 14f, hazardPaint)
        canvas.drawRoundRect(topHitbox, 14f, 14f, hazardPaint)
        if (safeBottom > safeTop) {
            canvas.drawRoundRect(safeLeft, safeTop, safeRight, safeBottom, 18f, 18f, safeGapPaint)
            canvas.drawRoundRect(safeLeft, safeTop, safeRight, safeBottom, 18f, 18f, safeGapStrokePaint)
        }
        canvas.drawCircle(x + floraWidth * 0.74f, y + floraHeight * 0.16f, floraWidth * 0.09f, blossomPaint)

        // Bottom vine segment
        bottomRect.set(x, groundY - floraHeight * 0.30f, x + floraWidth * 0.62f, groundY)
        canvas.save()
        canvas.rotate(sway * 2f, x + floraWidth * 0.35f, groundY)
        sprite.draw(canvas, bottomRect)
        canvas.restore()

        // Top branch + flower
        topRect.set(x + floraWidth * 0.18f, y, x + floraWidth, groundY - floraHeight * 0.50f)
        canvas.save()
        canvas.rotate(sway * 0.8f, x + floraWidth * 0.62f, y)
        sprite.draw(canvas, topRect)
        canvas.restore()
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        val encounters = PersistentMemoryManager.getEncounterCount(context, EntityType.VANILLA_ORCHID)
        val repeatHits = PersistentMemoryManager.getHitCount(context, EntityType.VANILLA_ORCHID)
        gameState.addBonus(points = 150, seeds = 1)
        ParticleManager.emit(FxPreset.LILY_NIGHT_GLOW, x + floraWidth * 0.68f, y + floraHeight * 0.16f)
        ParticleManager.emit(FxPreset.POLLEN_BURST, x + floraWidth * 0.34f, groundY - floraHeight * 0.18f)
        DialogueBubbleManager.spawn(
            FloraEncounterFlavor.orchidPass(encounters, repeatHits),
            x + floraWidth * 0.55f,
            y - 14f,
            Color.rgb(255, 246, 252),
            Color.rgb(170, 120, 160)
        )
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, bottomHitbox) ||
            RectF.intersects(player.hitbox, topHitbox)) return CollisionResult.HIT
        val mercyPad = readability.mercyPaddingPx
        val bm = RectF(bottomHitbox.left - mercyPad, bottomHitbox.top - mercyPad, bottomHitbox.right + mercyPad, bottomHitbox.bottom + mercyPad)
        val tm = RectF(topHitbox.left - mercyPad, topHitbox.top - mercyPad, topHitbox.right + mercyPad, topHitbox.bottom + mercyPad)
        if (RectF.intersects(player.hitbox, bm) || RectF.intersects(player.hitbox, tm)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
