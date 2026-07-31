package com.anurag9000.forestrun.entities.trees

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        val willow = willow()
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
            curtainHitbox.bottom + 0.5f,
            duckLaneRect.right - 8f,
            duckLaneRect.top - 0.5f
        )
        assertTrue(player.hitbox.top < player.hitbox.bottom)
        assertEquals(CollisionResult.MERCY_MISS, willow.onCollision(player, gameState))
    }

    @Test
    fun `willow encounter bounds enclose trunk and curtain without making duck lane solid`() {
        val willow = willow()
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)
        val trunk = rectField(willow, "trunkHitbox")
        val curtain = rectField(willow, "curtainHitbox")
        val lane = rectField(willow, "duckLaneRect")

        assertEncloses(willow.hitbox, trunk)
        assertEncloses(willow.hitbox, curtain)
        assertTrue(willow.hitbox.right > trunk.right)

        player.hitbox.set(
            lane.left + 8f,
            lane.top + 4f,
            lane.right - 8f,
            lane.bottom - 4f
        )
        assertTrue(RectF.intersects(player.hitbox, willow.hitbox))
        assertEquals(CollisionResult.NONE, willow.onCollision(player, gameState))
    }

    private fun willow() = WeepingWillow(
        context = context,
        startX = 680f,
        screenHeight = 1080f,
        groundY = 885.6f,
        sprite = spriteManager.willowSprite.copy()
    )

    private fun assertEncloses(outer: RectF, inner: RectF) {
        assertTrue(outer.left <= inner.left)
        assertTrue(outer.top <= inner.top)
        assertTrue(outer.right >= inner.right)
        assertTrue(outer.bottom >= inner.bottom)
    }

    private fun rectField(willow: WeepingWillow, name: String): RectF {
        val field = WeepingWillow::class.java.getDeclaredField(name)
        field.isAccessible = true
        return RectF(field.get(willow) as RectF)
    }
}
