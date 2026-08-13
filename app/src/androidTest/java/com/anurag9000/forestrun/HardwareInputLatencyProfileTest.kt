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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Physical-device app/render input-latency profiling harness.
 *
 * Motion events are instrumentation-injected. The resulting report measures
 * receipt inside Forest Run through the first posted response frame; it does not
 * measure touchscreen sensor acquisition, display scanout, or panel response and
 * therefore must never be represented as touch-to-photon evidence.
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
            putExtra(MainActivity.EXTRA_DEBUG_SCENARIO, EncounterScenario.OPENING_READABILITY.name)
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
                val location = IntArray(2)
                gameView.getLocationOnScreen(location)
                touchX = location[0] + gameView.width * 0.42f
                touchY = location[1] + gameView.height * 0.42f
            }
            waitForCondition("render thread warmup") {
                gameView.debugFrameCounter >= MIN_WARMUP_FRAMES
            }
            SystemClock.sleep(WARMUP_MS)
            InputLatencyTelemetryRegistry.reset()

            repeat(ACTION_PAIRS) {
                injectTap(touchX, touchY)
                SystemClock.sleep(ACTION_SETTLE_MS)
                injectDuck(touchX, touchY)
                SystemClock.sleep(ACTION_SETTLE_MS)
            }
            waitForCondition("all injected actions reach a posted frame") {
                InputLatencyTelemetryRegistry.snapshot().sampledActions >= ACTION_PAIRS * 2
            }

            val snapshot = InputLatencyTelemetryRegistry.snapshot()
            assertTrue("latency profile retained actions", snapshot.sampledActions >= ACTION_PAIRS * 2)
            assertTrue("touch-to-render p95 was measured", snapshot.p95TouchToRenderNs > 0L)
            assertTrue(
                "touch-to-render percentiles are ordered",
                snapshot.p99TouchToRenderNs >= snapshot.p95TouchToRenderNs
            )
            val report = InputLatencyReport(
                scenario = REPORT_SCENARIO,
                durationMs = SystemClock.elapsedRealtime() - startedAtMs,
                manufacturer = Build.MANUFACTURER.orEmpty(),
                model = Build.MODEL.orEmpty(),
                apiLevel = Build.VERSION.SDK_INT,
                refreshRateHz = refreshRateHz,
                injectedActions = ACTION_PAIRS * 2,
                snapshot = snapshot
            )
            val output = writeReport(report)
            instrumentation.sendStatus(
                0,
                Bundle().apply {
                    putString("forest_run_input_latency_profile", output.absolutePath)
                    putInt("forest_run_input_latency_samples", snapshot.sampledActions)
                    putLong("forest_run_input_latency_p95_ns", snapshot.p95TouchToRenderNs)
                }
            )
        }
    }

    private fun injectTap(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        send(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0))
        SystemClock.sleep(TAP_HOLD_MS)
        send(
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

    private fun injectDuck(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        send(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0))
        SystemClock.sleep(SWIPE_STEP_MS)
        send(
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
        send(
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

    private fun send(event: MotionEvent) {
        try {
            instrumentation.sendPointerSync(event)
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
    }
}
