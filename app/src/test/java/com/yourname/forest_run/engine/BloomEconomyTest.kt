package com.yourname.forest_run.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BloomEconomyTest {
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
    fun `seeds earned during Bloom do not restart its timer`() {
        val state = GameStateManager(context)
        repeat(GameConstants.BLOOM_SEED_COUNT) { state.collectSeed() }
        state.update(GameConstants.BLOOM_DURATION_S / 2f)
        val remainingBeforeConversions = state.bloomSecondsRemaining

        repeat(GameConstants.BLOOM_SEED_COUNT) { state.recordBloomConversion() }

        assertTrue(state.isBloomActive)
        assertEquals(0, state.bloomMeter)
        assertEquals(remainingBeforeConversions, state.bloomSecondsRemaining, 0.0001f)
    }

    @Test
    fun `garden spending cannot be refunded by stale game state`() {
        SaveManager.saveLifetimeSeeds(context, 20)
        val state = GameStateManager(context)
        assertEquals(20, state.lifetimeSeeds)

        // Simulate a Garden purchase while the long-lived state object exists.
        SaveManager.saveLifetimeSeeds(context, 5)

        state.collectSeed()
        state.save()

        assertEquals(6, state.lifetimeSeeds)
        assertEquals(6, SaveManager.loadLifetimeSeeds(context))
    }

    @Test
    fun `reset run refreshes externally changed seed balance`() {
        SaveManager.saveLifetimeSeeds(context, 30)
        val state = GameStateManager(context)
        SaveManager.saveLifetimeSeeds(context, 11)

        state.resetRun()

        assertEquals(11, state.lifetimeSeeds)
    }
}
