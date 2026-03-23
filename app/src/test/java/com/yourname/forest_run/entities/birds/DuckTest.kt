package com.yourname.forest_run.entities.birds

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SpriteManager
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DuckTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `duck rewards answering the staged low-lane call`() {
        val duck = Duck(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            sprite = spriteManager.duckFlying.copy()
        )
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)

        duck.update(deltaTime = 0f, scrollSpeed = 0f)

        val hitbox = RectF(duck.hitbox)
        val duckLaneRect = rectField(duck, "duckLaneRect")

        player.hitbox.set(
            hitbox.left - 72f,
            hitbox.top + 8f,
            hitbox.left - 12f,
            hitbox.bottom - 8f
        )
        assertTrue(duck.onCollision(player, gameState) != CollisionResult.HIT)
        assertTrue(booleanField(duck, "quackCalled"))

        player.onDuckPressed()
        player.hitbox.set(
            duckLaneRect.left + 6f,
            duckLaneRect.top + 4f,
            duckLaneRect.right - 18f,
            duckLaneRect.bottom - 4f
        )
        assertTrue(duck.onCollision(player, gameState) != CollisionResult.HIT)

        assertTrue(booleanField(duck, "answeredQuack"))
        assertTrue(booleanField(duck, "stayedLow"))

        player.hitbox.set(
            hitbox.left + 4f,
            hitbox.top + 4f,
            hitbox.right - 4f,
            hitbox.bottom - 4f
        )
        assertEquals(CollisionResult.HIT, duck.onCollision(player, gameState))
    }

    private fun rectField(duck: Duck, name: String): RectF {
        val field = Duck::class.java.getDeclaredField(name)
        field.isAccessible = true
        return RectF(field.get(duck) as RectF)
    }

    private fun booleanField(duck: Duck, name: String): Boolean {
        val field = Duck::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getBoolean(duck)
    }
}
