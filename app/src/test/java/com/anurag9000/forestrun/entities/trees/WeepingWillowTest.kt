package com.anurag9000.forestrun.entities.trees

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.Player
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WeepingWillowTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `willow keeps an explicit duck lane below the curtain`() {
        val willow = WeepingWillow(
            context = context,
            startX = 680f,
            screenHeight = 1080f,
            groundY = 885.6f,
            sprite = spriteManager.willowSprite.copy()
        )
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)

        val curtainHitbox = rectField(willow, "curtainHitbox")
        val duckLaneRect = rectField(willow, "duckLaneRect")

        player.hitbox.set(
            duckLaneRect.left + 8f,
            duckLaneRect.top + 4f,
            duckLaneRect.right - 8f,
            duckLaneRect.bottom - 4f
        )
        assertEquals(CollisionResult.NONE, willow.onCollision(player, gameState))

        player.hitbox.set(
            curtainHitbox.left + 16f,
            curtainHitbox.top + 12f,
            curtainHitbox.right - 16f,
            curtainHitbox.bottom - 12f
        )
        assertEquals(CollisionResult.HIT, willow.onCollision(player, gameState))

        player.hitbox.set(
            duckLaneRect.left + 8f,
            curtainHitbox.bottom + 2f,
            duckLaneRect.right - 8f,
            duckLaneRect.top - 2f
        )
        assertEquals(CollisionResult.MERCY_MISS, willow.onCollision(player, gameState))
    }

    private fun rectField(willow: WeepingWillow, name: String): RectF {
        val field = WeepingWillow::class.java.getDeclaredField(name)
        field.isAccessible = true
        return RectF(field.get(willow) as RectF)
    }
}
