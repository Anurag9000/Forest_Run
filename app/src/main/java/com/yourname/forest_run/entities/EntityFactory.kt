package com.yourname.forest_run.entities

import android.content.Context

/**
 * Instantiates the correct Entity subclass based on the requested EntityType.
 */
object EntityFactory {

    /**
     * Creates and returns a new Entity of the specified type.
     * In Phase 8-11, these missing classes will be implemented.
     */
    fun create(context: Context, type: EntityType, startX: Float, screenHeight: Float): Entity {
        // Phase 7 currently lacks the actual subclasses.
        // As we implement Phases 8 through 11, we will replace this dummy
        // anonymous class generation with proper instantiations.
        return object : Entity(context) {
            init {
                x = startX
                y = screenHeight * 0.82f - 50f // Default dummy placement above ground
                hitbox.set(x, y, x + 50f, y + 50f)
            }

            override fun update(deltaTime: Float, scrollSpeed: Float) {
                // Default: scroll left
                x -= scrollSpeed * deltaTime
                hitbox.offsetTo(x, y)
                if (x < -100f) isActive = false
            }

            override fun draw(canvas: android.graphics.Canvas) {
                // Empty placeholder draw
            }

            override fun onCollision(player: Player, gameState: com.yourname.forest_run.engine.GameStateManager): CollisionResult {
                return CollisionResult.NONE
            }
        }
    }
}
