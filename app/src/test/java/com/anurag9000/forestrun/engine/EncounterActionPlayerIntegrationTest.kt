package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EncounterActionPlayerIntegrationTest {
    private lateinit var context: Context
    private lateinit var sprites: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sprites = SpriteManager(context)
    }

    @Test
    fun `largest admitted Player step has 495 pixel physical apex and cannot meet 500`() {
        val player = Player(1_920, 1_080, sprites)
        val initialY = player.y
        var maximumRise = 0f
        player.onJumpPressed()
        repeat(20) {
            player.update(FrameInputAdmission.MAX_DELTA_SECONDS)
            maximumRise = maxOf(maximumRise, initialY - player.y)
        }
        val model = EncounterActionFeasibility.observe(
            leadDistancePx = 10_000f,
            approachSpeedPxPerSec = GameConstants.BASE_SCROLL_SPEED,
            requiredVerticalClearancePx = 500f,
            jumpUpwardSpeedPxPerSec = -Player.MAX_JUMP_FORCE,
            gravityPxPerSecSquared = Player.GRAVITY,
            gestureDecisionSeconds = 0.075f,
            safetyMarginSeconds = 0.08f
        )
        assertEquals(495f, maximumRise, 0.002f)
        assertEquals(maximumRise, model.maximumBallisticRisePx, 0.002f)
        assertFalse(model.jumpFeasible)
        assertTrue(maximumRise < 500f)
    }
}
