package com.anurag9000.forestrun

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.anurag9000.forestrun.engine.EncounterScenario
import com.anurag9000.forestrun.engine.FramePerformanceReport
import com.anurag9000.forestrun.engine.FramePerformanceTelemetry
import com.anurag9000.forestrun.engine.GameView
import com.anurag9000.forestrun.engine.RunMode
import com.anurag9000.forestrun.engine.RuntimeWorkloadTelemetry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Physical-device profiling harness.
 *
 * These tests are deliberately [LargeTest] and excluded from ordinary emulator
 * CI. They collect evidence; they do not declare universal hardware thresholds.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class HardwarePerformanceProfileTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext.applicationContext

    @Before
    fun setUp() {
        InstrumentationStateReset.clear(targetContext)
    }

    @Test
    fun profileOpeningReadabilityOnHardware() {
        profileScenario(EncounterScenario.OPENING_READABILITY, measurementMs = 20_000L)
    }

    @Test
    fun profileBloomShowcaseOnHardware() {
        profileScenario(EncounterScenario.BLOOM_SHOWCASE, measurementMs = 20_000L)
    }

    private fun profileScenario(scenario: EncounterScenario, measurementMs: Long) {
        FramePerformanceTelemetry.beginSession(windowSize = 1_800)
        val launchIntent = Intent(targetContext, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_DEBUG_SCENARIO, scenario.name)
            putExtra(MainActivity.EXTRA_RUN_MODE, RunMode.PERFORMANCE_PROFILE.name)
            putExtra(MainActivity.EXTRA_DEBUG_AUTOSTART, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        var refreshRateHz = 0f
        val startedAtMs = SystemClock.elapsedRealtime()
        ActivityScenario.launch<MainActivity>(launchIntent).use { activityScenario ->
            val gameView = requireGameView(activityScenario)
            activityScenario.onActivity { activity ->
                @Suppress("DEPRECATION")
                refreshRateHz = activity.windowManager.defaultDisplay.refreshRate
            }

            waitForCondition("render thread enters deterministic ${scenario.name}") {
                gameView.debugFrameCounter >= MIN_WARMUP_FRAMES
            }

            // Startup, shader/cache warmup, and scenario initialization happen
            // before the measured sleep. The 1,800-frame ring retains the latest
            // sustained-play window rather than only launch frames.
            SystemClock.sleep(WARMUP_MS)
            val measurementStartedAtMs = SystemClock.elapsedRealtime()
            SystemClock.sleep(measurementMs)
            val measuredDurationMs = SystemClock.elapsedRealtime() - measurementStartedAtMs

            val snapshot = FramePerformanceTelemetry.snapshot()
            assertTrue("profiling session recorded frames", snapshot.totalFrames > 0L)
            assertTrue("profiling window contains sustained samples", snapshot.sampledFrames >= 300)
            assertTrue("processing percentiles are ordered", snapshot.p99ProcessingNs >= snapshot.p50ProcessingNs)
            assertTrue("maximum processing time covers p99", snapshot.maximumProcessingNs >= snapshot.p99ProcessingNs)
            assertTrue("heap values are coherent", snapshot.maxHeapBytes >= snapshot.usedHeapBytes)

            val report = FramePerformanceReport(
                scenario = scenario.name,
                durationMs = measuredDurationMs,
                manufacturer = Build.MANUFACTURER.orEmpty(),
                model = Build.MODEL.orEmpty(),
                apiLevel = Build.VERSION.SDK_INT,
                refreshRateHz = refreshRateHz.coerceAtLeast(0f),
                snapshot = snapshot,
                workload = RuntimeWorkloadTelemetry.snapshot()
            )
            val output = writeReport(report)
            val status = Bundle().apply {
                putString("forest_run_profile", output.absolutePath)
                putLong("forest_run_profile_total_elapsed_ms", SystemClock.elapsedRealtime() - startedAtMs)
            }
            instrumentation.sendStatus(0, status)
        }
    }

    private fun writeReport(report: FramePerformanceReport): File {
        val root = requireNotNull(targetContext.getExternalFilesDir(null)) {
            "External app files directory is unavailable"
        }
        val directory = File(root, "performance-profiles").apply {
            check(mkdirs() || isDirectory) { "Could not create $absolutePath" }
        }
        val safeScenario = report.scenario.lowercase().replace(Regex("[^a-z0-9_-]+"), "_")
        return File(directory, "${safeScenario}_${System.currentTimeMillis()}.json").apply {
            writeText(report.toJson(), Charsets.UTF_8)
            check(isFile && length() > 0L) { "Performance report was not written" }
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
        timeoutMs: Long = 10_000L,
        condition: () -> Boolean
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(50L)
        }
        throw AssertionError("Timed out waiting for $label")
    }

    companion object {
        private const val MIN_WARMUP_FRAMES = 60L
        private const val WARMUP_MS = 5_000L
    }
}
