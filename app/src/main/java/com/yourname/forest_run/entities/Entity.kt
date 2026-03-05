package com.yourname.forest_run.entities

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SwayComponent

/**
 * Base class for all obstacles, flora, birds, and animals in the game.
 *
 * Managed by the EntityManager. Must implement custom update/draw,
 * and define collision behaviour.
 */
abstract class Entity(val context: Context) {

    // -----------------------------------------------------------------------
    // Position & Physics
    // -----------------------------------------------------------------------
    var x: Float = 0f
    var y: Float = 0f
    
    var velocityX: Float = 0f
    var velocityY: Float = 0f

    /** Automatically updated in update() if bounds are set. */
    var hitbox = RectF()

    /** If false, the EntityManager will remove this object from play. */
    var isActive: Boolean = true

    /** Optional procedural wind animation. */
    var swayComponent: SwayComponent? = null

    // -----------------------------------------------------------------------
    // Core loops
    // -----------------------------------------------------------------------

    /**
     * Called every game frame while [isActive] is true.
     * @param deltaTime   Seconds since last frame
     * @param scrollSpeed The global scroll speed from GameStateManager
     */
    abstract fun update(deltaTime: Float, scrollSpeed: Float)

    /**
     * Called every game frame. Render your sprite and/or debug shapes here.
     */
    abstract fun draw(canvas: Canvas)

    // -----------------------------------------------------------------------
    // Game Logic Hooks
    // -----------------------------------------------------------------------

    /**
     * Fires when the player interacts with this entity, or when it passes
     * specific milestones. Overridden by subclasses (e.g., Cat waves, Tree drops leaves).
     */
    open fun performUniqueAction(player: Player, gameState: GameStateManager) {
        // Default: do nothing
    }

    /**
     * Determines the outcome of a collision check between the player and this entity.
     * @return [CollisionResult.HIT], [CollisionResult.MERCY_MISS], or [CollisionResult.NONE].
     */
    abstract fun onCollision(player: Player, gameState: GameStateManager): CollisionResult
}
