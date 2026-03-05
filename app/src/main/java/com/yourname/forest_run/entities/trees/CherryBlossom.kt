package com.yourname.forest_run.entities.trees

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SwayComponent
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Entity
import com.yourname.forest_run.entities.Player

/**
 * Cherry Blossom (Phase 9)
 * Mid-height branches, duck-under hitbox.
 * Emits pink petal drift.
 * performUniqueAction: temp increases global wind speed and spikes petal rate.
 */
class CherryBlossom(context: Context, startX: Float, private val screenHeight: Float, groundY: Float) : Entity(context) {

    private val treeWidth = 180f
    private val trunkWidth = 35f
    private val branchHeightLow = screenHeight * 0.4f
    private val branchHeightHigh = screenHeight * 0.1f // Leaves hanging down from top.

    private val trunkPaint = Paint().apply {
        color = Color.rgb(100, 70, 40)
        style = Paint.Style.FILL
    }

    private val branchPaint = Paint().apply {
        color = Color.argb(220, 255, 180, 200) // Pink leaves
        style = Paint.Style.FILL
    }
    
    // Hitbox specifically for the branches hanging mid-height, forcing a duck
    private val branchHitbox = RectF()

    init {
        x = startX
        y = 0f // Starts from top of screen
        swayComponent = SwayComponent(speed = 0.6f, intensity = 12f)
        
        hitbox.set(x + treeWidth / 2f - trunkWidth / 2f, groundY - screenHeight * 0.8f, 
                   x + treeWidth / 2f + trunkWidth / 2f, groundY)
                   
        branchHitbox.set(x, branchHeightHigh, x + treeWidth, branchHeightLow)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        
        hitbox.offsetTo(x + treeWidth / 2f - trunkWidth / 2f, hitbox.top)
        
        // Branches sway
        branchHitbox.set(x + sway, branchHeightHigh, x + treeWidth + sway, branchHeightLow)
        
        if (x < -treeWidth - 50f) isActive = false
        
        // Phase 14: constantly emit pink petal particles
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f

        // Draw trunk
        canvas.drawRect(hitbox, trunkPaint)

        // Draw branches
        canvas.drawOval(branchHitbox, branchPaint)
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        // Phase 14: temporarily increase globalWindSpeed for 3 seconds, spike petal emission rate
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        // Hit trunk or branches
        if (RectF.intersects(player.hitbox, hitbox) || RectF.intersects(player.hitbox, branchHitbox)) {
            return CollisionResult.HIT
        }
        
        // Mercy box
        val branchMercy = RectF(
            branchHitbox.left - 12f, branchHitbox.top - 12f,
            branchHitbox.right + 12f, branchHitbox.bottom + 12f
        )
        val trunkMercy = RectF(
            hitbox.left - 12f, hitbox.top - 12f,
            hitbox.right + 12f, hitbox.bottom + 12f
        )

        if (RectF.intersects(player.hitbox, branchMercy) || RectF.intersects(player.hitbox, trunkMercy)) {
            return CollisionResult.MERCY_MISS
        }

        return CollisionResult.NONE
    }
}
