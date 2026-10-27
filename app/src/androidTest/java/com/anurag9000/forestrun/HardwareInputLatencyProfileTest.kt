package com.anurag9000.forestrun

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.anurag9000.forestrun.engine.EncounterScenario
import com.anurag9000.forestrun.engine.GameView
import com.anurag9000.forestrun.engine.InputLatencyReport
import com.anurag9000.forestrun.engine.InputLatencyTelemetryRegistry
import com.anurag9000.forestrun.engine.RunMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Physical-device app/render input-latency profiling harness.
 *
 * Motion events are dispatched to the app's GameView on the UI thread. The
 * report measures the app path from its touch listener through the first posted
 * response frame; it excludes Android window/input dispatch, touchscreen sensor
 * acquisition, display scanout, and panel response, so it is not touch-to-photon
 * evidence.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class HardwareInputLatencyProfileTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext.applicationContext

    @Before
    fun setUp() {
        InstrumentationStateReset.clear(targetContext)
        InputLatencyTelemetryRegistry.reset()
    }

    @Test
    fun profileJumpAndDuckAppLatencyOnHardware() {
        val launchIntent = Intent(targetContext, MainActivity::class.java).apply {
            // The one-step buddy scenario leaves gameplay live throughout the
            // measurement window; the opening-readability scenario can end
            // before a long series of injected gestures has been sampled.
            putExtra(MainActivity.EXTRA_DEBUG_SCENARIO, EncounterScenario.DOG_BUDDY.name)
            putExtra(MainActivity.EXTRA_RUN_MODE, RunMode.PERFORMANCE_PROFILE.name)
            putExtra(MainActivity.EXTRA_DEBUG_AUTOSTART, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        var refreshRateHz = 0f
        var touchX = 0f
        var touchY = 0f
        val startedAtMs = SystemClock.elapsedRealtime()
        ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
            val gameView = requireGameView(scenario)
            scenario.onActivity { activity ->
                @Suppress("DEPRECATION")
                refreshRateHz = activity.windowManager.defaultDisplay.refreshRate
                touchX = gameView.width * 0.42f
                touchY = gameView.height * 0.42f
            }
            waitForCondition("render thread warmup") {
                gameView.debugFrameCounter >= MIN_WARMUP_FRAMES
            }
            assertEquals("latency scenario must reach live gameplay", "PLAYING", privateField(gameView, "appState").toString())
            assertEquals("latency scenario run must be active", "PLAYING", privateField(gameView, "runState").toString())
            SystemClock.sleep(WARMUP_MS)
            InputLatencyTelemetryRegistry.reset()

            repeat(ACTION_PAIRS) {
                injectTap(gameView, touchX, touchY)
                SystemClock.sleep(ACTION_SETTLE_MS)
                injectDuck(gameView, touchX, touchY)
                SystemClock.sleep(ACTION_SETTLE_MS)
            }
            val snapshot = InputLatencyTelemetryRegistry.snapshot()
            val sampleDeadline = SystemClock.uptimeMillis() + ACTION_SAMPLE_TIMEOUT_MS
            var completedSnapshot = snapshot
            while (
                completedSnapshot.sampledActions < ACTION_PAIRS * 2 &&
                SystemClock.uptimeMillis() < sampleDeadline
            ) {
                SystemClock.sleep(25L)
                completedSnapshot = InputLatencyTelemetryRegistry.snapshot()
            }
            assertTrue(
                "latency profile retained all actions: sampled=${completedSnapshot.sampledActions}, " +
                    "dropped=${completedSnapshot.droppedActions}, frames=${gameView.debugFrameCounter}, " +
                    "view=${gameView.width}x${gameView.height}, " +
                    "appState=${privateField(gameView, "appState")}, " +
                    "runState=${privateField(gameView, "runState")}, " +
                    "gesture=${(privateField(gameView, "inputHandler") as? com.anurag9000.forestrun.engine.InputHandler)?.lastGestureLabel}",
                completedSnapshot.sampledActions >= ACTION_PAIRS * 2
            )
            val finalSnapshot = completedSnapshot
            assertTrue("touch-to-render p95 was measured", finalSnapshot.p95TouchToRenderNs > 0L)
            assertTrue(
                "touch-to-render percentiles are ordered",
                finalSnapshot.p99TouchToRenderNs >= finalSnapshot.p95TouchToRenderNs
            )
            val report = InputLatencyReport(
                scenario = REPORT_SCENARIO,
                durationMs = SystemClock.elapsedRealtime() - startedAtMs,
                manufacturer = Build.MANUFACTURER.orEmpty(),
                model = Build.MODEL.orEmpty(),
                apiLevel = Build.VERSION.SDK_INT,
                refreshRateHz = refreshRateHz,
                injectedActions = ACTION_PAIRS * 2,
                snapshot = finalSnapshot
            )
            val output = writeReport(report)
            instrumentation.sendStatus(
                0,
                Bundle().apply {
                    putString("forest_run_input_latency_profile", output.absolutePath)
                    putInt("forest_run_input_latency_samples", finalSnapshot.sampledActions)
                    putLong("forest_run_input_latency_p95_ns", finalSnapshot.p95TouchToRenderNs)
                }
            )
        }
    }

    private fun injectTap(gameView: GameView, x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        sendToGameView(gameView, MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0))
        SystemClock.sleep(TAP_HOLD_MS)
        sendToGameView(
            gameView,
            MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP,
                x,
                y,
                0
            )
        )
    }

    private fun injectDuck(gameView: GameView, x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        sendToGameView(gameView, MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0))
        SystemClock.sleep(SWIPE_STEP_MS)
        sendToGameView(
            gameView,
            MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_MOVE,
                x,
                y + DUCK_SWIPE_PX,
                0
            )
        )
        SystemClock.sleep(SWIPE_STEP_MS)
        sendToGameView(
            gameView,
            MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP,
                x,
                y + DUCK_SWIPE_PX,
                0
            )
        )
    }

    private fun sendToGameView(gameView: GameView, event: MotionEvent) {
        try {
            instrumentation.runOnMainSync {
                gameView.dispatchTouchEvent(event)
            }
        } finally {
            event.recycle()
        }
    }

    private fun writeReport(report: InputLatencyReport): File {
        val root = requireNotNull(targetContext.getExternalFilesDir(null)) {
            "External app files directory is unavailable"
        }
        val directory = File(root, "input-latency-profiles").apply {
            check(mkdirs() || isDirectory) { "Could not create $absolutePath" }
        }
        return File(directory, "input_latency_${System.currentTimeMillis()}.json").apply {
            writeText(report.toJson(), Charsets.UTF_8)
            check(isFile && length() > 0L) { "Input-latency report was not written" }
        }
    }

    private fun requireGameView(scenario: ActivityScenario<MainActivity>): GameView {
        lateinit var gameView: GameView
        scenario.onActivity { activity ->
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            gameView = content.getChildAt(0) as GameView
        }
        return gameView
    }

    private fun privateField(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).run {
            isAccessible = true
            get(target)
        }

    private fun waitForCondition(
        label: String,
        timeoutMs: Long = 15_000L,
        condition: () -> Boolean
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(25L)
        }
        throw AssertionError("Timed out waiting for $label")
    }

    companion object {
        private const val REPORT_SCENARIO = "INPUT_GESTURES"
        private const val MIN_WARMUP_FRAMES = 60L
        private const val WARMUP_MS = 2_000L
        private const val ACTION_PAIRS = 20
        private const val ACTION_SETTLE_MS = 180L
        private const val TAP_HOLD_MS = 20L
        private const val SWIPE_STEP_MS = 12L
        private const val DUCK_SWIPE_PX = 140f
        private const val ACTION_SAMPLE_TIMEOUT_MS = 15_000L
    }
}
