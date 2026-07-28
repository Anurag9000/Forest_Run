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
class CherryBlossomTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `cherry blossom keeps the gust band narrower than the storm veil`() {
        val cherry = CherryBlossom(
            context = context,
            startX = 640f,
            screenHeight = 1080f,
            groundY = 885.6f,
            sprite = spriteManager.cherryBlossomSprite.copy()
        )
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)

        val branchHitbox = rectField(cherry, "branchHitbox")
        val stormVeilRect = rectField(cherry, "stormVeilRect")
        assertEquals(true, stormVeilRect.width() > branchHitbox.width())

        player.hitbox.set(
            branchHitbox.left + 8f,
            branchHitbox.top + 8f,
            branchHitbox.right - 8f,
            branchHitbox.bottom - 8f
        )
        assertEquals(CollisionResult.HIT, cherry.onCollision(player, gameState))

        player.hitbox.set(
            stormVeilRect.left + 6f,
            stormVeilRect.top + 6f,
            stormVeilRect.right - 6f,
            branchHitbox.top - 2f
        )
        assertEquals(CollisionResult.MERCY_MISS, cherry.onCollision(player, gameState))
    }

    private fun rectField(cherry: CherryBlossom, name: String): RectF {
        val field = CherryBlossom::class.java.getDeclaredField(name)
        field.isAccessible = true
        return RectF(field.get(cherry) as RectF)
    }
}
