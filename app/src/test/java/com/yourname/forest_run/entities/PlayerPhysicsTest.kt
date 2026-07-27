
package com.yourname.forest_run.entities

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.engine.SpriteManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerPhysicsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `quick tap always leaves the ground`() {
        val player = Player(1920, 1080, SpriteManager(context))
        val groundTop = player.y
        player.onJumpPressed()
        player.onJumpReleased(0.02f)
        repeat(4) { player.update(1f / 60f) }
        assertTrue(player.y < groundTop)
        assertTrue(player.velocityY < 0f)
    }

    @Test
    fun `bloom activation preserves airborne physics`() {
        val player = Player(1920, 1080, SpriteManager(context))
        player.onJumpPressed()
        player.onJumpHeld(0.20f)
        player.update(1f / 60f)
        val before = player.y
        player.activateBloom()
        player.update(1f / 60f)
        assertTrue(player.y < before)
        assertTrue(player.isInvincible)
    }
}
