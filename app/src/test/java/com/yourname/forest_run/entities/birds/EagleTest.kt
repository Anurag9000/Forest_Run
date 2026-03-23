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
class EagleTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `eagle exposes a marked zone and rewards clearing it`() {
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

        val targetZone = rectField(heldMarkEagle, "targetZoneRect")
        val corridor = rectField(heldMarkEagle, "diveCorridorRect")
        assertTrue(targetZone.width() > 0f)
        assertTrue(targetZone.height() > 0f)
        assertTrue(corridor.width() >= targetZone.width())
        assertTrue(corridor.height() >= targetZone.height())

        val missedZone = rectField(markMissEagle, "targetZoneRect")
        player.hitbox.set(
            missedZone.left + 6f,
            missedZone.top + 6f,
            missedZone.right - 6f,
            missedZone.bottom - 6f
        )
        assertEquals(CollisionResult.NONE, markMissEagle.onCollision(player, markMissState))
        assertTrue(!booleanField(markMissEagle, "heldMark"))

        heldMarkEagle.performUniqueAction(player, heldState)
        markMissEagle.performUniqueAction(player, markMissState)

        assertTrue(heldState.seedsThisRun > markMissState.seedsThisRun)
        assertTrue(heldState.score > markMissState.score)
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
