package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameStateOpeningInputBoundaryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `non finite and negative holds cannot complete onboarding`() {
        val state = GameStateManager(context)
        state.recordJumpInput()

        listOf(
            -1f,
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        ).forEach(state::recordJumpHold)

        assertFalse(holdChip(state).isComplete)
    }

    @Test
    fun `authored hold threshold still completes onboarding`() {
        val state = GameStateManager(context)
        state.recordJumpInput()

        state.recordJumpHold(OpeningReadabilityGuide.HOLD_DISCOVERY_THRESHOLD_SEC)

        assertTrue(holdChip(state).isComplete)
    }

    private fun holdChip(state: GameStateManager): OpeningGuidanceChip =
        requireNotNull(state.openingGuidanceCue)
            .chips
            .single { it.label == "Hold" }
}
