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
class CactusTest {
    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `cactus distinguishes hit mercy and clear separation`() {
        val cactus = cactus()
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)
        val hazard = RectF(cactus.hitbox)

        player.hitbox.set(hazard)
        assertEquals(CollisionResult.HIT, cactus.onCollision(player, gameState))

        player.hitbox.set(
            hazard.left,
            hazard.top - 2f,
            hazard.right,
            hazard.top - 0.5f
        )
        assertEquals(CollisionResult.MERCY_MISS, cactus.onCollision(player, gameState))

        player.hitbox.set(
            hazard.right + 200f,
            hazard.bottom + 200f,
            hazard.right + 240f,
            hazard.bottom + 240f
        )
        assertEquals(CollisionResult.NONE, cactus.onCollision(player, gameState))
    }

    @Test
    fun `cactus hitbox follows scroll movement without changing size`() {
        val cactus = cactus()
        val before = RectF(cactus.hitbox)

        cactus.update(deltaTime = 0.5f, scrollSpeed = 240f)

        assertEquals(before.left - 120f, cactus.hitbox.left, 0.001f)
        assertEquals(before.top, cactus.hitbox.top, 0.001f)
        assertEquals(before.width(), cactus.hitbox.width(), 0.001f)
        assertEquals(before.height(), cactus.hitbox.height(), 0.001f)
        assertTrue(cactus.isActive)
    }

    private fun cactus() = Cactus(
        context = context,
        startX = 520f,
        groundY = 885.6f,
        sprite = spriteManager.cactusSprite.copy()
    )
}
