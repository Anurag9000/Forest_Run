package com.yourname.forest_run.entities.birds

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.CameraSystem
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SpriteSizing
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.ui.DialogueBubbleManager
import kotlin.math.sqrt

/**
 * Eagle (Phase 10)
 * Spawns off-screen top. Captures player's Y on spawn.
 * Dives diagonally toward that point at high speed.
 */
class Eagle(
    context: Context,
    startX: Float,
    private val screenWidth: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val birdH = 96f
    private val birdW = SpriteSizing.widthForHeight(sprite, birdH, minWidth = 70f)
    private val diveSpeed = 700f
    private val insetX = birdW * 0.10f
    private val insetY = birdH * 0.10f

    private var velX = 0f
    private var velY = 0f
    private var targetX = screenWidth * 0.25f
    private var targetY = groundY - 50f
    private var lockTimer = 0f
    private val lockDuration = 0.35f
    private var isLocked = false
    private val reticlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 90, 90)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    init {
        // Spawn off-screen top at a random horizontal position
        x = startX
        y = -birdH - 20f
        hitbox.set(x + insetX, y + insetY, x + birdW - insetX, y + birdH - insetY)

        // Auto-lock onto the player's typical horizontal running line
        lockOnTarget(screenWidth * 0.25f, groundY - 50f)

        // Phase 20: play screech SFX on spawn
    }

    /**
     * Call once after spawning to aim the eagle at the player's current position.
     */
    fun lockOnTarget(targetX: Float, targetY: Float) {
        this.targetX = targetX
        this.targetY = targetY
        val dx = targetX - x
        val dy = targetY - y
        val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat().coerceAtLeast(1f)
        velX = dx / dist * diveSpeed
        velY = dy / dist * diveSpeed
        CameraSystem.shakeEagle()  // Phase 15: lock-on tremor
        DialogueBubbleManager.spawn("Marked.", targetX, targetY - 28f, Color.rgb(255, 234, 234), Color.rgb(180, 70, 70))
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        if (!isLocked) {
            lockTimer += deltaTime
            if (lockTimer >= lockDuration) {
                isLocked = true
            }
        } else {
            x += velX * deltaTime
            y += velY * deltaTime
        }
        hitbox.offsetTo(x + insetX, y + insetY)
        sprite.update(deltaTime)
        // Despawn when completely off screen (using +150f to allow time for the diagonal dive)
        if (y > groundY + birdH || x < -birdW - 50f || x > screenWidth + 150f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        if (!isLocked) {
            val radius = 22f + lockTimer * 34f
            reticlePaint.alpha = (220 * (1f - (lockTimer / lockDuration).coerceIn(0f, 1f))).toInt().coerceIn(60, 220)
            canvas.drawCircle(targetX, targetY, radius, reticlePaint)
            canvas.drawLine(targetX - radius - 8f, targetY, targetX - radius + 6f, targetY, reticlePaint)
            canvas.drawLine(targetX + radius - 6f, targetY, targetX + radius + 8f, targetY, reticlePaint)
            canvas.drawLine(targetX, targetY - radius - 8f, targetX, targetY - radius + 6f, reticlePaint)
            canvas.drawLine(targetX, targetY + radius - 6f, targetX, targetY + radius + 8f, reticlePaint)
        }
        val drawRect = RectF(x, y, x + birdW, y + birdH)
        sprite.draw(canvas, drawRect)
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        if (RectF.intersects(player.hitbox, hitbox)) return CollisionResult.HIT
        val mercy = RectF(hitbox.left - 12f, hitbox.top - 12f, hitbox.right + 12f, hitbox.bottom + 12f)
        if (RectF.intersects(player.hitbox, mercy)) return CollisionResult.MERCY_MISS
        return CollisionResult.NONE
    }
}
