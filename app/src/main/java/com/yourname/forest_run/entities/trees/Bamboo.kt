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
import kotlin.random.Random

/**
 * Bamboo (Phase 9)
 * 5 vertical stalks, full-screen height.
 * Creates a randomised vertical gap the player must jump through perfectly.
 */
class Bamboo(context: Context, startX: Float, private val screenHeight: Float, groundY: Float) : Entity(context) {

    private val stalkCount = 5
    private val stalkWidth = 15f
    private val gapBetweenStalks = 30f // Total width of structure = 15*5 + 30*4 = 195
    private val totalWidth = stalkCount * stalkWidth + (stalkCount - 1) * gapBetweenStalks

    private val paint = Paint().apply {
        color = Color.rgb(60, 200, 60)
        style = Paint.Style.FILL
    }
    
    // We maintain a list of safe gaps. 
    // Format: an array of Float representing the Y-coordinate center of the gap for that particular X pass.
    // Instead of doing actual gap polygons, we just have vertical hitboxes above and below the gap.
    
    private val topHitboxes = Array(stalkCount) { RectF() }
    private val bottomHitboxes = Array(stalkCount) { RectF() }

    init {
        x = startX
        y = 0f
        swayComponent = SwayComponent(speed = 3.0f, intensity = 4f) // Stiff quick jitter
        
        // Generate random gap height
        // Gap needs to be tall enough for the player to fit through, but tight.
        val gapHeight = Player.BASE_HEIGHT * 1.5f
        
        // The safe gap center can be anywhere between groundY - gapHeight/2 to 0 + gapHeight/2
        val minGapCenter = gapHeight
        val maxGapCenter = groundY - gapHeight
        
        // Random Y center for the safe zone
        val gapYCenter = Random.nextFloat() * (maxGapCenter - minGapCenter) + minGapCenter
        
        for (i in 0 until stalkCount) {
            val stalkX = x + i * (stalkWidth + gapBetweenStalks)
            
            // Top hitbox: from top of screen to top of gap
            topHitboxes[i].set(stalkX, 0f, stalkX + stalkWidth, gapYCenter - gapHeight / 2f)
            
            // Bottom hitbox: from bottom of gap to ground
            bottomHitboxes[i].set(stalkX, gapYCenter + gapHeight / 2f, stalkX + stalkWidth, groundY)
        }
        
        // Main bounds to know when it's passed
        hitbox.set(x, 0f, x + totalWidth, groundY)
    }

    override fun update(deltaTime: Float, scrollSpeed: Float) {
        x -= scrollSpeed * deltaTime
        val sway = swayComponent?.getOffset(deltaTime) ?: 0f
        
        hitbox.offsetTo(x, 0f)
        
        for (i in 0 until stalkCount) {
            val stalkX = x + i * (stalkWidth + gapBetweenStalks) + sway
            
            topHitboxes[i].left = stalkX
            topHitboxes[i].right = stalkX + stalkWidth
            
            bottomHitboxes[i].left = stalkX
            bottomHitboxes[i].right = stalkX + stalkWidth
        }
        
        if (x < -totalWidth - 50f) isActive = false
    }

    override fun draw(canvas: Canvas) {
        // Draw the top and bottom segments of the stalks
        for (i in 0 until stalkCount) {
            canvas.drawRect(topHitboxes[i], paint)
            canvas.drawRect(bottomHitboxes[i], paint)
        }
    }

    override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult {
        var nearMiss = false
        
        // Check collision against all 10 stalks segments
        for (i in 0 until stalkCount) {
            if (RectF.intersects(player.hitbox, topHitboxes[i]) || RectF.intersects(player.hitbox, bottomHitboxes[i])) {
                return CollisionResult.HIT
            }
            
            // Check mercy (near miss under 6px for bamboo specifically to make it tight)
            val topMercy = RectF(
                topHitboxes[i].left - 6f, topHitboxes[i].top,
                topHitboxes[i].right + 6f, topHitboxes[i].bottom + 6f
            )
            val bottomMercy = RectF(
                bottomHitboxes[i].left - 6f, bottomHitboxes[i].top - 6f,
                bottomHitboxes[i].right + 6f, bottomHitboxes[i].bottom
            )
            
            if (RectF.intersects(player.hitbox, topMercy) || RectF.intersects(player.hitbox, bottomMercy)) {
                nearMiss = true
            }
        }
        
        if (nearMiss) return CollisionResult.MERCY_MISS

        return CollisionResult.NONE
    }
}
