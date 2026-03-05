package com.yourname.forest_run.entities

import android.content.Context
import com.yourname.forest_run.entities.flora.*

/**
 * Instantiates the correct Entity subclass based on the requested EntityType.
 */
object EntityFactory {

    /**
     * Creates and returns a new Entity of the specified type.
     * In Phase 8-11, these missing classes will be implemented.
     */
    fun create(context: Context, type: EntityType, startX: Float, screenHeight: Float): Entity {
        val groundY = screenHeight * 0.82f // Same as default ground in Player

        return when (type) {
            EntityType.CACTUS         -> Cactus(context, startX, groundY)
            EntityType.LILY_OF_VALLEY -> LilyOfValley(context, startX, groundY)
            EntityType.HYACINTH       -> Hyacinth(context, startX, groundY)
            EntityType.EUCALYPTUS     -> Eucalyptus(context, startX, groundY)
            EntityType.VANILLA_ORCHID -> VanillaOrchid(context, startX, groundY)
            
            // Phase 9-11: Implement the rest
            else -> {
                object : Entity(context) {
                    init {
                        x = startX
                        y = groundY - 50f // Default dummy placement above ground
                        hitbox.set(x, y, x + 50f, y + 50f)
                    }

                    override fun update(deltaTime: Float, scrollSpeed: Float) {
                        x -= scrollSpeed * deltaTime
                        hitbox.offsetTo(x, y)
                        if (x < -100f) isActive = false
                    }

                    override fun draw(canvas: android.graphics.Canvas) {}

                    override fun onCollision(player: Player, gameState: com.yourname.forest_run.engine.GameStateManager) = CollisionResult.NONE
                }
            }
        }
    }
}
