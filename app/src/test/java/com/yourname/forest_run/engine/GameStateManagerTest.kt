package com.yourname.forest_run.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class GameStateManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `collecting seeds activates bloom and persists lifetime seeds`() {
        val state = GameStateManager(context)

        repeat(GameConstants.BLOOM_SEED_COUNT) {
            state.collectSeed()
        }
        state.addBonus(points = 250)
        state.save()

        assertTrue(state.isBloomActive)
        assertEquals(0, state.bloomMeter)
        assertEquals(GameConstants.BLOOM_SEED_COUNT, state.seedsThisRun)
        assertEquals(GameConstants.BLOOM_SEED_COUNT, state.lifetimeSeeds)

        val reloaded = GameStateManager(context)
        assertEquals(GameConstants.BLOOM_SEED_COUNT, reloaded.lifetimeSeeds)
        assertEquals(250, reloaded.highScore)
    }

    @Test
    fun `reset run clears transient state and milestone carryover`() {
        val state = GameStateManager(context)

        state.update(1_100f)
        assertTrue(state.consumeMilestone())

        state.resetRun()

        assertEquals(0, state.score)
        assertEquals(0, state.seedsThisRun)
        assertEquals(0, state.bloomMeter)
        assertFalse(state.isBloomActive)
        assertFalse(state.consumeMilestone())
    }
}
