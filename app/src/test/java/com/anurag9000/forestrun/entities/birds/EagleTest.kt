package com.anurag9000.forestrun.entities.birds

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.Player
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EagleTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `eagle exposes a live marked zone and rewards clearing it`() {
        val heldMarkEagle = Eagle(
            context = context,
            startX = 560f,
            screenWidth = 1920f,
            groundY = 885.6f,
            sprite = spriteManager.eagleSprite.copy()
        )
        val markMissEagle = Eagle(
            context = context,
            startX = 560f,
            screenWidth = 1920f,
            groundY = 885.6f,
            sprite = spriteManager.eagleSprite.copy()
        )
        val heldState = GameStateManager(context)
        val markMissState = GameStateManager(context)
        val player = Player(1920, 1080, spriteManager)

        val initialTarget = rectField(heldMarkEagle, "targetZoneRect")
        val initialCorridor = rectField(heldMarkEagle, "diveCorridorRect")
        assertTrue(initialTarget.width() > 0f)
        assertTrue(initialTarget.height() > 0f)
        assertTrue(initialCorridor.width() >= initialTarget.width())
        assertTrue(initialCorridor.height() >= initialTarget.height())

        // During telegraph the target follows the live player without counting
        // as a failure. Only remaining inside after lock and grace loses the
        // held-mark bonus.
        player.hitbox.set(420f, 720f, 500f, 820f)
        markMissEagle.updatePlayerInteraction(player, markMissState)
        val liveTarget = rectField(markMissEagle, "targetZoneRect")
        assertTrue(kotlin.math.abs(liveTarget.centerX() - player.hitbox.centerX()) < 1f)
        assertTrue(kotlin.math.abs(liveTarget.centerY() - player.hitbox.centerY()) < 1f)
        assertTrue(booleanField(markMissEagle, "heldMark"))

        markMissEagle.update(deltaTime = 0.5f, scrollSpeed = 0f)
        markMissEagle.update(deltaTime = 0.20f, scrollSpeed = 0f)
        player.hitbox.set(
            liveTarget.left + 6f,
            liveTarget.top + 6f,
            liveTarget.right - 6f,
            liveTarget.bottom - 6f
        )
        markMissEagle.updatePlayerInteraction(player, markMissState)
        assertTrue(!booleanField(markMissEagle, "heldMark"))

        heldMarkEagle.performUniqueAction(player, heldState)
        markMissEagle.performUniqueAction(player, markMissState)

        assertTrue(heldState.seedsThisRun > markMissState.seedsThisRun)
        assertTrue(heldState.score > markMissState.score)
    }


    @Test
    fun `all authored Eagle staging offsets survive pre entry and reach the viewport`() {
        val player = Player(1920, 1080, spriteManager)
        val state = GameStateManager(context)
        player.hitbox.set(420f, 720f, 500f, 820f)

        // EAGLE_MARK's two offsets and BIRD_SHOWCASE's later Eagle offset.
        for (offset in listOf(520f, 1_060f, 1_280f)) {
            val eagle = Eagle(
                context = context,
                startX = 1920f + offset,
                screenWidth = 1920f,
                groundY = 885.6f,
                sprite = spriteManager.eagleSprite.copy()
            )
            eagle.update(deltaTime = 0.05f, scrollSpeed = 0f)
            assertTrue("Eagle culled on the first staged frame: offset=$offset", eagle.isActive)

            eagle.updatePlayerInteraction(player, state)
            eagle.update(deltaTime = 1f, scrollSpeed = 0f)
            assertTrue("Eagle culled during authored lock-on: offset=$offset", eagle.isActive)

            repeat(55) {
                eagle.update(deltaTime = 0.04f, scrollSpeed = 0f)
                assertTrue("Eagle culled before entering: offset=$offset", eagle.isActive)
            }
            assertTrue("Eagle never reached the viewport: offset=$offset", eagle.x < 1920f)
        }
    }

    private fun rectField(eagle: Eagle, name: String): RectF {
        val field = Eagle::class.java.getDeclaredField(name)
        field.isAccessible = true
        return RectF(field.get(eagle) as RectF)
    }

    private fun booleanField(eagle: Eagle, name: String): Boolean {
        val field = Eagle::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getBoolean(eagle)
    }
}
