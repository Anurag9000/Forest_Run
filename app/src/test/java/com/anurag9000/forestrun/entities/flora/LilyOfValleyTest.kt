package com.anurag9000.forestrun.entities.flora

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
class LilyOfValleyTest {
    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `lily distinguishes hit mercy and clear separation`() {
        val lily = lily()
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)
        val hazard = RectF(lily.hitbox)

        player.hitbox.set(hazard)
        assertEquals(CollisionResult.HIT, lily.onCollision(player, gameState))

        player.hitbox.set(
            hazard.left,
            hazard.top - 2f,
            hazard.right,
            hazard.top - 0.5f
        )
        assertEquals(CollisionResult.MERCY_MISS, lily.onCollision(player, gameState))

        player.hitbox.set(
            hazard.right + 200f,
            hazard.bottom + 200f,
            hazard.right + 240f,
            hazard.bottom + 240f
        )
        assertEquals(CollisionResult.NONE, lily.onCollision(player, gameState))
    }

    @Test
    fun `lily hitbox follows scroll movement without changing size`() {
        val lily = lily()
        val before = RectF(lily.hitbox)

        lily.update(deltaTime = 0.25f, scrollSpeed = 320f)

        assertEquals(before.left - 80f, lily.hitbox.left, 0.001f)
        assertEquals(before.top, lily.hitbox.top, 0.001f)
        assertEquals(before.width(), lily.hitbox.width(), 0.001f)
        assertEquals(before.height(), lily.hitbox.height(), 0.001f)
        assertTrue(lily.isActive)
    }

    private fun lily() = LilyOfValley(
        context = context,
        startX = 520f,
        groundY = 885.6f,
        sprite = spriteManager.lilySprite.copy()
    )
}
