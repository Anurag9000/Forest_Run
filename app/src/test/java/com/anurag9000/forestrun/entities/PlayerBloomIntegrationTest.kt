package com.anurag9000.forestrun.entities

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.systems.ParticleManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerBloomIntegrationTest {
    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        ParticleManager.clear()
        spriteManager = SpriteManager(context)
    }

    @After
    fun tearDown() {
        ParticleManager.clear()
    }

    @Test
    fun `activating Bloom in ascent preserves velocity and completes landing`() {
        val player = Player(1_920, 1_080, spriteManager)
        player.onJumpPressed()
        player.update(deltaTime = 0.06f)

        assertEquals(PlayerState.JUMPING, player.state)
        assertTrue(player.velocityY < 0f)
        val stateBeforeBloom = player.state
        val yBeforeBloom = player.y
        val velocityBeforeBloom = player.velocityY

        player.activateBloom()

        assertTrue(player.isInvincible)
        assertEquals(stateBeforeBloom, player.state)
        assertEquals(yBeforeBloom, player.y, 0.0001f)
        assertEquals(velocityBeforeBloom, player.velocityY, 0.0001f)

        player.update(deltaTime = 0.05f)

        assertTrue("Player should continue rising after Bloom activation", player.y < yBeforeBloom)
        assertTrue(
            "Gravity should continue changing vertical velocity during Bloom",
            player.velocityY > velocityBeforeBloom
        )
        assertTrue(player.isInvincible)

        var frames = 0
        while (player.state != PlayerState.LANDING && player.state != PlayerState.RUNNING && frames < 600) {
            player.update(deltaTime = 1f / 120f)
            frames++
        }

        assertTrue("Airborne Bloom player should eventually land", frames < 600)
        assertTrue(player.state == PlayerState.LANDING || player.state == PlayerState.RUNNING)
        assertEquals(player.groundY - Player.BASE_HEIGHT, player.y, 0.01f)
        assertEquals(0f, player.velocityY, 0.01f)
        assertTrue("Bloom remains orthogonal to locomotion through landing", player.isInvincible)

        val landedState = player.state
        player.deactivateBloom()

        assertFalse(player.isInvincible)
        assertEquals(landedState, player.state)
    }

    @Test
    fun `activating Bloom while falling preserves downward motion`() {
        val player = Player(1_920, 1_080, spriteManager)
        player.onJumpPressed()

        var frames = 0
        while (player.state != PlayerState.FALLING && frames < 600) {
            player.update(deltaTime = 1f / 120f)
            frames++
        }
        assertEquals(PlayerState.FALLING, player.state)

        val yBeforeBloom = player.y
        val velocityBeforeBloom = player.velocityY
        assertTrue(velocityBeforeBloom > 0f)

        player.activateBloom()
        player.update(deltaTime = 0.03f)

        assertTrue(player.isInvincible)
        assertEquals(PlayerState.FALLING, player.state)
        assertTrue(player.y > yBeforeBloom)
        assertTrue(player.velocityY > velocityBeforeBloom)
    }
}
