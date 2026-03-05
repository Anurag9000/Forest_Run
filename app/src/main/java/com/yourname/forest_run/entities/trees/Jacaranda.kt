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
 * Jacaranda (Phase 9)
 * Upper branch hitbox. Player must duck or avoid.
 * Emits purple petal particles.
 */
class Jacaranda(context: Context, startX: Float, private val screenHeight: Float, groundY: Float) : Entity(context) {

    private val treeWidth = 150f
    private val trunkWidth = 30f
    private val branchHeight = screenHeight * 0.3f

    private val trunkPaint = Paint().apply {
        color = Color.rgb(90, 60, 30)
        style = Paint.Style.FILL
    }

    private val branchPaint = Paint().apply {
        color = Color.argb(220, 150, 80, 200) // Purple leaves
        style = Paint.Style.FILL
    }
    
    // The player's upper body could hit this if they jump
    private val branchHitbox = RectF()

    init {
        x = startX
        y = 0f // Starts from top of screen
        swayComponent = SwayComponent(speed = 0.8f, intensity = 15f)
        
        hitbox.set(x + treeWidth / 2f - trunkWidth / 2f, groundY - screenHeight * 0.8f, 
                   x + treeWidth / 2f + trunkWidth / 2f, groundY)
                   
        branchHitbox.set(x, 0f, x + treeWidth, branchHeight)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        
        hitbox.offsetTo(x + treeWidth / 2f - trunkWidth / 2f, hitbox.top)
        
        // Branches sway
        branchHitbox.set(x + sway, 0f, x + treeWidth + sway, branchHeight)
        
        if (x < -treeWidth - 50f) isActive = false
        
        // Phase 14: constantly emit purple petal particles
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f

        // Draw trunk
        canvas.drawRect(hitbox, trunkPaint)

        // Draw branches
        canvas.drawOval(branchHitbox, branchPaint)
    }

    override fun performUniqueAction(player: Player, gameState: GameStateManager) {
        // Phase 14: spawn full-screen petal curtain FX
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        // Hit trunk or branches
        if (RectF.intersects(player.hitbox, hitbox) || RectF.intersects(player.hitbox, branchHitbox)) {
            return CollisionResult.HIT
        }
        
        // Mercy box
        val branchMercy = RectF(
            branchHitbox.left - 12f, branchHitbox.top,
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
