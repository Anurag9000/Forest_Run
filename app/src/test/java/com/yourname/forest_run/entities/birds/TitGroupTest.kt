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
class TitGroupTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `tit group tracks the trough guide as a separate rhythm reward lane`() {
        val titGroup = TitGroup(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            sprite = spriteManager.titSprite.copy(),
            count = 5
        )
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)

        titGroup.update(deltaTime = 0f, scrollSpeed = 0f)

        val troughGuideRect = rectField(titGroup, "troughGuideRect")
        val birdRects = rectArrayField(titGroup, "birdRects")

        player.hitbox.set(
            troughGuideRect.left + 10f,
            troughGuideRect.top + 4f,
            troughGuideRect.left + 54f,
            troughGuideRect.bottom - 4f
        )
        assertEquals(CollisionResult.NONE, titGroup.onCollision(player, gameState))
        assertTrue(booleanField(titGroup, "keptBeat"))

        player.hitbox.set(
            birdRects[2].left + 4f,
            birdRects[2].top + 4f,
            birdRects[2].right - 4f,
            birdRects[2].bottom - 4f
        )
        assertEquals(CollisionResult.HIT, titGroup.onCollision(player, gameState))
    }

    private fun rectField(titGroup: TitGroup, name: String): RectF {
        val field = TitGroup::class.java.getDeclaredField(name)
        field.isAccessible = true
        return RectF(field.get(titGroup) as RectF)
    }

    private fun rectArrayField(titGroup: TitGroup, name: String): Array<RectF> {
        val field = TitGroup::class.java.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val value = field.get(titGroup) as Array<RectF>
        return Array(value.size) { index -> RectF(value[index]) }
    }

    private fun booleanField(titGroup: TitGroup, name: String): Boolean {
        val field = TitGroup::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getBoolean(titGroup)
    }
}
