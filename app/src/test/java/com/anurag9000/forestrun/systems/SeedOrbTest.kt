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
