package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GardenSanctuaryAtmosphereIntegrationTest {
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
    fun `planner publishes exact unmodified reckless baseline`() {
        SaveManager.saveForestMoodState(
            context,
            ForestMoodState(
                currentMood = ForestMood.RECKLESS,
                moodStreak = 0,
                totalRuns = 1,
                recklessRuns = 1
            )
        )

        val state = GardenSanctuaryPlanner.build(context, summary = null)

        assertEquals(1, state.fireflyCount)
        assertEquals(5, state.petalCount)
        assertEquals(0, state.bloomPatchCount)
        assertEquals(0, state.mistBandCount)
        assertEquals(0, state.lanternGlowCount)
        assertEquals(36, state.groundGlowAlpha)
        assertEquals(22, state.canopyShadeAlpha)
    }
}
