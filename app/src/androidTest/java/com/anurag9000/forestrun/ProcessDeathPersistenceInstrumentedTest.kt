package com.anurag9000.forestrun

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.anurag9000.forestrun.engine.GardenEconomy
import com.anurag9000.forestrun.engine.SaveManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith

/**
 * Two-stage durable-state check driven by scripts/run_connected_validation.sh.
 * The host process force-stops the target between separate instrumentation runs.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ProcessDeathPersistenceInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext.applicationContext
    private val stage: String
        get() = InstrumentationRegistry.getArguments().getString(STAGE_ARGUMENT).orEmpty()

    @get:Rule
    val cleanup = object : TestWatcher() {
        override fun failed(e: Throwable, description: Description) {
            try {
                InstrumentationStateReset.clear(context)
            } catch (cleanupFailure: Throwable) {
                e.addSuppressed(cleanupFailure)
            }
        }

        override fun succeeded(description: Description) {
            // Preserve only the prepare stage's state for the host force-stop.
            if (stage != STAGE_PREPARE) {
                InstrumentationStateReset.clear(context)
            }
        }
    }

    @Test
    fun durableGameStateSurvivesHostVerifiedProcessDeath() {
        when (stage) {
            STAGE_PREPARE -> prepareDurableState()
            STAGE_VERIFY -> verifyRestoredDurableState()
            STAGE_CLEANUP -> InstrumentationStateReset.clear(context)
            else -> throw AssertionError("Expected instrumentation stage prepare, verify, or cleanup; got '$stage'.")
        }
    }

    private fun prepareDurableState() {
        InstrumentationStateReset.clear(context)
        assertEquals(0, SaveManager.loadHighScore(context))
        assertEquals(1, SaveManager.loadGardenProgress(context))

        SaveManager.saveHighScore(context, EXPECTED_HIGH_SCORE)
        SaveManager.saveGardenProgress(context, GardenEconomy.catalogueSize)

        // SaveManager uses apply(); commit on the same preference file waits for
        // queued writes so the host can immediately terminate the app process.
        assertTrue(
            "SaveManager preference writes must reach durable storage before force-stop",
            context.getSharedPreferences(SAVE_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(FLUSH_MARKER, true)
                .commit()
        )
        assertEquals(EXPECTED_HIGH_SCORE, SaveManager.loadHighScore(context))
        assertEquals(GardenEconomy.catalogueSize, SaveManager.loadGardenProgress(context))
    }

    private fun verifyRestoredDurableState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertEquals(
                    EXPECTED_HIGH_SCORE,
                    SaveManager.loadHighScore(context)
                )
                assertEquals(
                    GardenEconomy.catalogueSize,
                    SaveManager.loadGardenProgress(context)
                )
            }
        }
        assertEquals(
            "high score must be loaded from durable game storage after a fresh instrumentation launch",
            EXPECTED_HIGH_SCORE,
            SaveManager.loadHighScore(context)
        )
        assertEquals(
            "garden progression must be loaded from durable game storage after a fresh instrumentation launch",
            GardenEconomy.catalogueSize,
            SaveManager.loadGardenProgress(context)
        )
    }

    private companion object {
        const val STAGE_ARGUMENT = "forestRunProcessDeathStage"
        const val STAGE_PREPARE = "prepare"
        const val STAGE_VERIFY = "verify"
        const val STAGE_CLEANUP = "cleanup"
        const val EXPECTED_HIGH_SCORE = 1_234_567
        const val SAVE_PREFERENCES = "forest_run_prefs"
        const val FLUSH_MARKER = "process_death_test_flush"
    }
}
