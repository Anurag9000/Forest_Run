package com.anurag9000.forestrun.entities.flora

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
class VanillaOrchidTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `orchid keeps the true thread open between low and high hazards`() {
        val orchid = VanillaOrchid(
            context = context,
            startX = 560f,
            groundY = 885.6f,
            sprite = spriteManager.orchidSprite.copy()
        )
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)

        val bottomHitbox = rectField(orchid, "bottomHitbox")
        val threadRect = rectField(orchid, "threadRect")

        val threadLeft = threadRect.left + 2f
        val threadRight = threadRect.right - 2f
        val threadTop = threadRect.top + 2f
        val threadBottom = threadRect.bottom - 2f

        player.hitbox.set(threadLeft, threadTop, threadRight, threadBottom)
        assertEquals(CollisionResult.NONE, orchid.onCollision(player, gameState))

        player.hitbox.set(threadLeft, bottomHitbox.top + 2f, threadRight, bottomHitbox.bottom - 2f)
        assertEquals(CollisionResult.HIT, orchid.onCollision(player, gameState))

        player.hitbox.set(threadLeft, bottomHitbox.top - 6f, threadRight, bottomHitbox.top - 1f)
        assertEquals(CollisionResult.MERCY_MISS, orchid.onCollision(player, gameState))
    }

    private fun rectField(orchid: VanillaOrchid, name: String): RectF {
        val field = VanillaOrchid::class.java.getDeclaredField(name)
        field.isAccessible = true
        return RectF(field.get(orchid) as RectF)
    }
}
