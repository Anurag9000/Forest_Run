package com.yourname.forest_run.entities.trees

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.engine.SwayComponent
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.ui.DialogueBubbleManager
import kotlin.random.Random

/**
 * Bamboo — Phase 27: sprite strips rendered for each stalk; gap logic preserved.
 * 5 narrow stalks, each rendered as a narrow slice of the sprite.
 */
class Bamboo(
    context: Context,
    startX: Float,
    private val screenHeight: Float,
    private val groundY: Float,
    private val sprite: SpriteSheet
) : Entity(context) {

    private val stalkCount        = 5
    private val stalkWidth        = 22f
    private val gapBetweenStalks  = 40f
    private val totalWidth        = stalkCount * stalkWidth + (stalkCount - 1) * gapBetweenStalks

    private val topHitboxes       = Array(stalkCount) { RectF() }
    private val bottomHitboxes    = Array(stalkCount) { RectF() }
    private val topDrawRects      = Array(stalkCount) { RectF() }
    private val bottomDrawRects   = Array(stalkCount) { RectF() }
    private val gapGuidePaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(44, 212, 255, 210)
        style = Paint.Style.FILL
    }

    init {
        x = startX
        y = 0f
        swayComponent = SwayComponent(speed = 3.0f, intensity = 4f)
        val gapHeight  = Player.BASE_HEIGHT * 1.5f
        val gapYCenter = Random.nextFloat() * (groundY - gapHeight * 2f) + gapHeight

        for (i in 0 until stalkCount) {
            val stalkX = x + i * (stalkWidth + gapBetweenStalks)
            topHitboxes[i].set(stalkX, 0f, stalkX + stalkWidth, gapYCenter - gapHeight / 2f)
            bottomHitboxes[i].set(stalkX, gapYCenter + gapHeight / 2f, stalkX + stalkWidth, groundY)
        }
        hitbox.set(x, 0f, x + totalWidth, groundY)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        hitbox.offsetTo(x, 0f)
        for (i in 0 until stalkCount) {
            val stalkX = x + i * (stalkWidth + gapBetweenStalks) + sway
            topHitboxes[i].left    = stalkX
            topHitboxes[i].right   = stalkX + stalkWidth
            bottomHitboxes[i].left = stalkX
            bottomHitboxes[i].right= stalkX + stalkWidth
        }
        sprite.update(deltaTime)
        if (x < -totalWidth - 50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        for (i in 0 until stalkCount - 1) {
            val left = topHitboxes[i].right
            val right = topHitboxes[i + 1].left
            if (right > left) {
                val gapTop = topHitboxes[i].bottom
                val gapBottom = bottomHitboxes[i].top
                canvas.drawRoundRect(left, gapTop, right, gapBottom, 14f, 14f, gapGuidePaint)
            }
        }
        for (i in 0 until stalkCount) {
            // Top stalk
            topDrawRects[i].set(topHitboxes[i].left, 0f, topHitboxes[i].right, topHitboxes[i].bottom)
            sprite.draw(canvas, topDrawRects[i])
            // Bottom stalk
            bottomDrawRects[i].set(bottomHitboxes[i].left, bottomHitboxes[i].top, bottomHitboxes[i].right, groundY)
            sprite.draw(canvas, bottomDrawRects[i])
        }
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        gameState.addBonus(points = 135)
        DialogueBubbleManager.spawn("Thread the grove", x + totalWidth * 0.5f, groundY * 0.16f, Color.rgb(232, 255, 236), Color.rgb(88, 148, 92))
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        var nearMiss = false
        for (i in 0 until stalkCount) {
            if (RectF.intersects(player.hitbox, topHitboxes[i]) ||
                RectF.intersects(player.hitbox, bottomHitboxes[i])) return CollisionResult.HIT
            val tm = RectF(topHitboxes[i].left - 6f, topHitboxes[i].top, topHitboxes[i].right + 6f, topHitboxes[i].bottom + 6f)
            val bm = RectF(bottomHitboxes[i].left - 6f, bottomHitboxes[i].top - 6f, bottomHitboxes[i].right + 6f, bottomHitboxes[i].bottom)
            if (RectF.intersects(player.hitbox, tm) || RectF.intersects(player.hitbox, bm)) nearMiss = true
        }
        return if (nearMiss) CollisionResult.MERCY_MISS else CollisionResult.NONE
    }
}
