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
 * Weeping Willow (Phase 9)
 * Full-height tree. Player must duck under the curtain of leaves.
 */
class WeepingWillow(context: Context, startX: Float, private val screenHeight: Float, groundY: Float) : Entity(context) {

    private val treeWidth = 200f
    private val trunkWidth = 40f
    private val curtainHeight = screenHeight * 0.45f

    private val trunkPaint = Paint().apply {
        color = Color.rgb(80, 50, 20)
        style = Paint.Style.FILL
    }

    private val leafPaint = Paint().apply {
        color = Color.argb(200, 30, 100, 50)
        style = Paint.Style.FILL
    }
    
    // The player must duck under this
    private val curtainHitbox = RectF()

    init {
        x = startX
        y = 0f // Starts from top of screen
        swayComponent = SwayComponent(speed = 0.5f, intensity = 20f)
        
        hitbox.set(x + treeWidth / 2f - trunkWidth / 2f, groundY - screenHeight * 0.8f, 
                   x + treeWidth / 2f + trunkWidth / 2f, groundY)
                   
        curtainHitbox.set(x, 0f, x + treeWidth, curtainHeight)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        
        hitbox.offsetTo(x + treeWidth / 2f - trunkWidth / 2f, hitbox.top)
        // Curtain sways
        curtainHitbox.set(x + sway, 0f, x + treeWidth + sway, curtainHeight)
        
        if (x < -treeWidth - 50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        val sway = swayComponent?.getOffset(0f) ?: 0f

        // Draw trunk
        canvas.drawRect(hitbox, trunkPaint)

        // Draw curtain
        canvas.drawRect(curtainHitbox, leafPaint)
        
        // Phase 9: "Shadow zone" — subtle darkening when player is under the tree.
        // We'll calculate this in GameView or just draw a dark semi-transparent rectangle below the curtain
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        // Hit trunk or curtain
        if (RectF.intersects(player.hitbox, hitbox) || RectF.intersects(player.hitbox, curtainHitbox)) {
            return CollisionResult.HIT
        }
        
        // Mercy box for curtain (ducked just low enough)
        val curtainMercy = RectF(
            curtainHitbox.left - 12f, curtainHitbox.top, // top doesn't matter, it's at 0
            curtainHitbox.right + 12f, curtainHitbox.bottom + 12f
        )
        val trunkMercy = RectF(
            hitbox.left - 12f, hitbox.top - 12f,
            hitbox.right + 12f, hitbox.bottom + 12f
        )

        if (RectF.intersects(player.hitbox, curtainMercy) || RectF.intersects(player.hitbox, trunkMercy)) {
            return CollisionResult.MERCY_MISS
        }

        return CollisionResult.NONE
    }
}
