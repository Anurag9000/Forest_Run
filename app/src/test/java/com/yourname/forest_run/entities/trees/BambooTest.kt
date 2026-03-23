package com.yourname.forest_run.entities.trees

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
class BambooTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `bamboo keeps a featured seam wider than the surrounding gaps`() {
        val bamboo = Bamboo(
            context = context,
            startX = 560f,
            screenHeight = 1080f,
            groundY = 885.6f,
            sprite = spriteManager.bambooSprite.copy()
        )
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)

        val gapRects = rectArrayField(bamboo, "gapRects")
        assertTrue(gapRects[1].width() > gapRects[0].width())
        assertTrue(gapRects[1].width() > gapRects[2].width())

        player.hitbox.set(
            gapRects[1].left + 4f,
            gapRects[1].top + 8f,
            gapRects[1].right - 4f,
            gapRects[1].bottom - 8f
        )
        assertEquals(CollisionResult.NONE, bamboo.onCollision(player, gameState))

        val topHitboxes = rectArrayField(bamboo, "topHitboxes")
        player.hitbox.set(
            topHitboxes[1].left + 2f,
            topHitboxes[1].bottom - 10f,
            topHitboxes[1].right - 2f,
            topHitboxes[1].bottom - 2f
        )
        assertEquals(CollisionResult.HIT, bamboo.onCollision(player, gameState))
    }

    private fun rectArrayField(bamboo: Bamboo, name: String): Array<RectF> {
        val field = Bamboo::class.java.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val value = field.get(bamboo) as Array<RectF>
        return Array(value.size) { index -> RectF(value[index]) }
    }
}
