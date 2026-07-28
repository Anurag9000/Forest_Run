package com.anurag9000.forestrun

import android.content.Context
import android.content.Intent
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anurag9000.forestrun.engine.EncounterDirector
import com.anurag9000.forestrun.engine.EncounterScenario
import com.anurag9000.forestrun.engine.FeedbackSettings
import com.anurag9000.forestrun.engine.GameView
import com.anurag9000.forestrun.engine.SafeContentTransform
import com.anurag9000.forestrun.engine.SaveManager
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LifecycleSettingsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext.applicationContext

    @Before
    fun setUp() {
        InstrumentationStateReset.clear(targetContext)
    }

    @Test
    fun pauseResumeReplacesStoppedRenderThreadAndContinuesFrames() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireReadyGameView(scenario)
            val firstThread = getPrivateField(gameView, "gameThread") as Thread
            val framesBeforePause = gameView.debugFrameCounter
            assertTrue(firstThread.isAlive)

            scenario.moveToState(Lifecycle.State.STARTED)
            waitForCondition("first render thread terminates on pause") { !firstThread.isAlive }

            scenario.moveToState(Lifecycle.State.RESUMED)
            waitForCondition("new render thread starts after resume") {
                val current = getPrivateField(gameView, "gameThread") as Thread
                current !== firstThread && current.isAlive
            }
            val resumedThread = getPrivateField(gameView, "gameThread") as Thread
            assertNotSame(firstThread, resumedThread)
            waitForCondition("frames continue after resume") {
                gameView.debugFrameCounter > framesBeforePause + 8
            }
        }
    }

    @Test
    fun repeatedSingleTaskIntentsReuseActivityAndSwitchDeterministicScenario() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireReadyGameView(scenario)
            val originalActivity = AtomicReference<MainActivity>()
            scenario.onActivity(originalActivity::set)

            launchScenarioIntent(scenario, EncounterScenario.BLOOM_SHOWCASE)
            waitForCondition("Bloom scenario is applied") {
                val director = getPrivateField(gameView, "encounterDirector") as EncounterDirector
                director.activeScenario == EncounterScenario.BLOOM_SHOWCASE
            }

            launchScenarioIntent(scenario, EncounterScenario.GHOST_READABILITY)
            waitForCondition("second scenario replaces the first") {
                val director = getPrivateField(gameView, "encounterDirector") as EncounterDirector
                director.activeScenario == EncounterScenario.GHOST_READABILITY
            }

            scenario.onActivity { activity -> assertSame(originalActivity.get(), activity) }
        }
    }

    @Test
    fun menuFeedbackControlsApplyImmediatelyAndPersistAcrossRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireReadyGameView(scenario)
            val menu = getPrivateField(gameView, "mainMenuScreen")
                ?: error("Main menu was not initialized")
            val panel = getPrivateField(menu, "feedbackSettingsPanel")
                ?: error("Feedback settings panel was not initialized")
            val layout = getPrivateField(panel, "layout")
                ?: error("Feedback settings layout was not initialized")

            tapLogicalRect(gameView, getPrivateField(layout, "reducedMotion") as RectF)
            tapLogicalRect(gameView, getPrivateField(layout, "audio") as RectF)
            tapLogicalRect(gameView, getPrivateField(layout, "haptics") as RectF)

            waitForCondition("all feedback settings change") {
                FeedbackSettings.reducedMotion &&
                    !FeedbackSettings.audioEnabled &&
                    !FeedbackSettings.hapticsEnabled
            }

            scenario.recreate()
            val recreatedView = requireReadyGameView(scenario)
            assertTrue(recreatedView.debugFrameCounter > 0)
            assertTrue(FeedbackSettings.reducedMotion)
            assertFalse(FeedbackSettings.audioEnabled)
            assertFalse(FeedbackSettings.hapticsEnabled)

            val prefs = targetContext.getSharedPreferences(
                "forest_run_feedback_settings",
                Context.MODE_PRIVATE
            )
            assertTrue(prefs.getBoolean("reduced_motion", false))
            assertFalse(prefs.getBoolean("audio_enabled", true))
            assertFalse(prefs.getBoolean("haptics_enabled", true))
        }
    }

    @Test
    fun startupRepairsWrongTypedSaveAndFeedbackValuesBeforeFirstFrame() {
        targetContext.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("high_score", "broken")
            .putInt("lifetime_seeds", -25)
            .putFloat("best_distance", Float.NaN)
            .putInt("garden_unlocked", 99)
            .commit()
        targetContext.getSharedPreferences("forest_run_feedback_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("reduced_motion", "broken")
            .putInt("audio_enabled", 3)
            .putFloat("haptics_enabled", 1f)
            .commit()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireReadyGameView(scenario)
            assertTrue(gameView.debugFrameCounter > 0)
            assertEquals(0, SaveManager.loadHighScore(targetContext))
            assertEquals(0, SaveManager.loadLifetimeSeeds(targetContext))
            assertEquals(0f, SaveManager.loadBestDistance(targetContext), 0f)
            assertEquals(9, SaveManager.loadGardenProgress(targetContext))
            assertEquals(
                1,
                targetContext.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
                    .getInt("save_schema_version", -1)
            )
            assertFalse(FeedbackSettings.reducedMotion)
            assertTrue(FeedbackSettings.audioEnabled)
            assertTrue(FeedbackSettings.hapticsEnabled)
        }
    }

    private fun launchScenarioIntent(
        scenario: ActivityScenario<MainActivity>,
        requestedScenario: EncounterScenario
    ) {
        scenario.onActivity { activity ->
            activity.startActivity(
                Intent(activity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(MainActivity.EXTRA_DEBUG_SCENARIO, requestedScenario.name)
                    putExtra(MainActivity.EXTRA_DEBUG_AUTOSTART, true)
                }
            )
        }
        instrumentation.waitForIdleSync()
    }

    private fun requireReadyGameView(scenario: ActivityScenario<MainActivity>): GameView {
        val gameView = requireGameView(scenario)
        waitForCondition("GameView lays out and renders", timeoutMs = 10_000L) {
            gameView.width > 0 && gameView.height > 0 && gameView.debugFrameCounter > 10
        }
        return gameView
    }

    private fun requireGameView(scenario: ActivityScenario<MainActivity>): GameView {
        lateinit var gameView: GameView
        scenario.onActivity { activity ->
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            gameView = content.getChildAt(0) as GameView
        }
        return gameView
    }

    private fun tapLogicalRect(gameView: GameView, rect: RectF) {
        val transform = getPrivateField(gameView, "safeContentTransform") as SafeContentTransform
        val physical = transform.toPhysical(
            (rect.left + rect.right) / 2f,
            (rect.top + rect.bottom) / 2f
        )
        tapGameView(gameView, physical.x, physical.y)
    }

    private fun tapGameView(gameView: GameView, x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 16L, MotionEvent.ACTION_UP, x, y, 0)
        instrumentation.runOnMainSync {
            gameView.dispatchTouchEvent(down)
            gameView.dispatchTouchEvent(up)
        }
        instrumentation.waitForIdleSync()
        down.recycle()
        up.recycle()
    }

    private fun waitForCondition(
        label: String,
        timeoutMs: Long = 5_000L,
        condition: () -> Boolean
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(50L)
        }
        throw AssertionError("Timed out waiting for $label")
    }

    private fun getPrivateField(target: Any, fieldName: String): Any? {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(target)
    }
}
