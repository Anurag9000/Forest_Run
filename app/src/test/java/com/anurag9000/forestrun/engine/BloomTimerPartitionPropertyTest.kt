package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BloomTimerPartitionPropertyTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `active Bloom clock is invariant to equivalent positive frame partitions`() {
        val total = 4.75f
        val singleFrame = GameStateManager(context).also {
            it.debugActivateBloom()
            it.update(total)
        }
        val partitioned = GameStateManager(context).also {
            it.debugActivateBloom()
            listOf(1f, 0.5f, 2f, 1.25f).forEach(it::update)
        }

        assertTrue(singleFrame.isBloomActive)
        assertTrue(partitioned.isBloomActive)
        assertEquals(singleFrame.bloomSecondsRemaining, partitioned.bloomSecondsRemaining, 0.0001f)
        assertEquals(singleFrame.bloomTimeFractionRemaining, partitioned.bloomTimeFractionRemaining, 0.0001f)
    }

    @Test
    fun `Bloom expires at the exact authoritative duration for single and partitioned frames`() {
        val singleFrame = GameStateManager(context).also {
            it.debugActivateBloom()
            it.update(GameConstants.BLOOM_DURATION_S)
        }
        val partitioned = GameStateManager(context).also {
            it.debugActivateBloom()
            listOf(1f, 2f, 3f).forEach(it::update)
        }

        assertFalse(singleFrame.isBloomActive)
        assertFalse(partitioned.isBloomActive)
        assertEquals(0f, singleFrame.bloomTimeFractionRemaining, 0f)
        assertEquals(0f, partitioned.bloomTimeFractionRemaining, 0f)
    }

    @Test
    fun `Bloom remains active immediately below its duration boundary`() {
        val state = GameStateManager(context)
        state.debugActivateBloom()

        state.update(Math.nextDown(GameConstants.BLOOM_DURATION_S))

        assertTrue(state.isBloomActive)
        assertTrue(state.bloomSecondsRemaining > 0f)
        assertTrue(state.bloomTimeFractionRemaining > 0f)
    }

    @Test
    fun `invalid frame deltas never consume an active Bloom window`() {
        val state = GameStateManager(context)
        state.debugActivateBloom()
        val initialRemaining = state.bloomSecondsRemaining

        listOf(
            0f,
            -1f,
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        ).forEach(state::update)

        assertTrue(state.isBloomActive)
        assertEquals(initialRemaining, state.bloomSecondsRemaining, 0f)
        assertEquals(1f, state.bloomTimeFractionRemaining, 0f)
    }

    @Test
    fun `first seed after Bloom expiration starts a fresh meter instead of retriggering`() {
        val state = GameStateManager(context)
        state.debugActivateBloom()
        state.update(GameConstants.BLOOM_DURATION_S)
        assertFalse(state.isBloomActive)

        state.collectSeed()

        assertFalse(state.isBloomActive)
        assertEquals(1, state.bloomMeter)
        assertEquals(1, state.seedsThisRun)
    }
}
