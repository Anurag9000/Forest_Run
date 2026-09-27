package com.anurag9000.forestrun.systems

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.GameStateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SeedOrbTest {

    private lateinit var context: Context
    private lateinit var gameState: GameStateManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        gameState = GameStateManager(context)
    }


    @Test
    fun `max speed bounded frame cannot tunnel a narrow jumping player through an Orb`() {
        val orb = SeedOrb(530f, 200f)
        val narrowJumpHitbox = RectF(460f, 180f, 500f, 220f)
        // Neither sampled endpoint overlaps the 52 px pickup core.
        assertFalse(orb.checkCollection(narrowJumpHitbox))
        assertTrue(orb.update(0.05f, 2_000f, gameState))
        assertTrue(orb.centreX + SeedOrb.RADIUS < narrowJumpHitbox.left)
        assertTrue(orb.checkCollection(narrowJumpHitbox))
        assertTrue(orb.isCollected)
        assertFalse(orb.isActive)
        assertFalse(orb.checkCollection(narrowJumpHitbox))
    }


    @Test
    fun `moving player cannot collect an Orb when their time windows differ`() {
        val orb = SeedOrb(520f, 200f)
        val playerBefore = RectF(460f, 300f, 480f, 320f)
        val playerAfter = RectF(460f, 224f, 480f, 244f)
        assertTrue(orb.update(0.05f, 2_000f, gameState))
        // An endpoint-only Player sweep sees the final 224px top and
        // falsely intersects the Orb while it crosses X earlier.
        assertFalse(orb.checkCollection(playerAfter, playerBefore))
        assertFalse(orb.isCollected)
        assertTrue(orb.isActive)
    }

    @Test
    fun `moving player collects a real simultaneous midframe Orb overlap`() {
        val orb = SeedOrb(520f, 200f)
        val playerBefore = RectF(460f, 200f, 480f, 220f)
        val playerAfter = RectF(460f, 300f, 480f, 320f)
        assertTrue(orb.update(0.05f, 2_000f, gameState))
        // End-frame rectangles miss, yet the falling Player and Orb share
        // an actual collision interval shortly after Orb enters the X lane.
        assertFalse(orb.checkCollection(playerAfter))
        assertTrue(orb.checkCollection(playerAfter, playerBefore))
        assertTrue(orb.isCollected)
        assertFalse(orb.isActive)
        assertFalse(orb.checkCollection(playerAfter, playerBefore))
    }

    @Test
    fun `malformed historical player box fails closed without granting pickup`() {
        val orb = SeedOrb(520f, 200f)
        val current = RectF(460f, 190f, 480f, 220f)
        assertTrue(orb.update(0.05f, 2_000f, gameState))
        assertFalse(
            orb.checkCollection(current, RectF(Float.NaN, 0f, 500f, 220f))
        )
        assertTrue(orb.isActive)
    }

    @Test
    fun `horizontal sweep never collects an Orb outside the vertical lane`() {
        val orb = SeedOrb(530f, 200f)
        val unrelatedVerticalLane = RectF(460f, 350f, 500f, 390f)
        assertTrue(orb.update(0.05f, 2_000f, gameState))
        assertFalse(orb.checkCollection(unrelatedVerticalLane))
        assertTrue(orb.isActive)
        assertFalse(orb.isCollected)
    }

    @Test
    fun `swept collision does not use diagonal union rectangle as a pickup`() {
        val orb = SeedOrb(530f, 220f)
        val diagonalMiss = RectF(472f, 252f, 488f, 262f)
        assertTrue(orb.update(0.05f, 2_000f, gameState))
        // X and Y swept projections each overlap, but at different times.
        assertFalse(orb.checkCollection(diagonalMiss))
        assertTrue(orb.isActive)
    }

    @Test
    fun `sweep does not resurrect a lifetime expired Orb`() {
        val orb = SeedOrb(530f, 200f)
        val narrowJumpHitbox = RectF(460f, 180f, 500f, 220f)
        assertFalse(orb.update(SeedOrb.LIFETIME_S, 2_000f, gameState))
        assertFalse(orb.checkCollection(narrowJumpHitbox))
        assertFalse(orb.isCollected)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `orb rejects non finite spawn coordinates`() {
        SeedOrb(Float.NaN, 100f)
    }

    @Test
    fun `collection is terminal and can only be claimed once`() {
        val orb = SeedOrb(100f, 200f)
        val player = RectF(80f, 180f, 120f, 220f)

        assertTrue(orb.checkCollection(player))
        assertTrue(orb.isCollected)
        assertFalse(orb.isActive)
        assertFalse(orb.checkCollection(player))
    }

    @Test
    fun `invalid motion input is a no op`() {
        val orb = SeedOrb(100f, 200f)

        orb.update(Float.NaN, 100f, gameState)
        orb.update(-1f, 100f, gameState)
        orb.update(1f, Float.POSITIVE_INFINITY, gameState)
        orb.update(1f, -100f, gameState)

        assertEquals(100f, orb.centreX, 0f)
        assertEquals(200f, orb.centreY, 0f)
        assertTrue(orb.isActive)
    }

    @Test
    fun `orb expires at its bounded lifetime`() {
        val orb = SeedOrb(100f, 200f)

        assertFalse(orb.update(SeedOrb.LIFETIME_S, 0f, gameState))
        assertFalse(orb.isActive)
        assertFalse(orb.checkCollection(RectF(80f, 180f, 120f, 220f)))
    }
}
