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
import kotlin.math.min
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

    @Test
    fun profileAllEntityFamiliesOnHardware() {
        listOf(
            EncounterScenario.FLORA_SHOWCASE,
            EncounterScenario.TREE_SHOWCASE,
            EncounterScenario.BIRD_SHOWCASE,
            EncounterScenario.ANIMAL_SHOWCASE
        ).forEach { scenario ->
            profileScenario(scenario, measurementMs = 15_000L)
        }
    }

    @Test
    fun profileGhostReadabilityOnHardware() {
        profileScenario(EncounterScenario.GHOST_READABILITY, measurementMs = 20_000L)
    }

    private fun profileScenario(scenario: EncounterScenario, measurementMs: Long) {
        InstrumentationStateReset.clear(targetContext)
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
            SystemClock.sleep(WARMUP_MS)

            // The GameThread retains the monitor object captured at construction.
            // Stop its producer, clear startup/cache samples in place, restart the
            // requested scenario, and resume with the same monitor reference.
            activityScenario.onActivity {
                gameView.pause()
                FramePerformanceTelemetry.resetStoppedSession()
                gameView.applyDebugLaunchIntent(launchIntent)
                gameView.resume()
            }

            val cycleMs = scenarioReplayIntervalMs(scenario)
            val measurementStartedAtMs = SystemClock.elapsedRealtime()
            val measurementEndsAtMs = measurementStartedAtMs + measurementMs
            var nextReplayAtMs = measurementStartedAtMs + cycleMs
            var replayCount = 1
            while (SystemClock.elapsedRealtime() < measurementEndsAtMs) {
                val now = SystemClock.elapsedRealtime()
                if (now >= nextReplayAtMs) {
                    activityScenario.onActivity {
                        gameView.applyDebugLaunchIntent(launchIntent)
                    }
                    replayCount++
                    nextReplayAtMs += cycleMs
                }
                val remainingToEnd = measurementEndsAtMs - SystemClock.elapsedRealtime()
                val remainingToReplay = nextReplayAtMs - SystemClock.elapsedRealtime()
                val sleepMs = min(
                    PROFILE_POLL_MS,
                    min(remainingToEnd, remainingToReplay).coerceAtLeast(1L)
                )
                SystemClock.sleep(sleepMs)
            }
            val measuredDurationMs = SystemClock.elapsedRealtime() - measurementStartedAtMs

            val snapshot = FramePerformanceTelemetry.snapshot()
            assertTrue("profiling session recorded frames", snapshot.totalFrames > 0L)
            assertTrue("profiling window contains sustained samples", snapshot.sampledFrames >= 300)
            assertTrue("processing percentiles are ordered", snapshot.p99ProcessingNs >= snapshot.p50ProcessingNs)
            assertTrue("maximum processing time covers p99", snapshot.maximumProcessingNs >= snapshot.p99ProcessingNs)
            assertTrue("heap values are coherent", snapshot.maxHeapBytes >= snapshot.usedHeapBytes)
            assertTrue("scenario was replayed during measurement", replayCount >= 2)

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
                putInt("forest_run_profile_replays", replayCount)
                putLong("forest_run_profile_total_elapsed_ms", SystemClock.elapsedRealtime() - startedAtMs)
            }
            instrumentation.sendStatus(0, status)
        }
    }

    private fun scenarioReplayIntervalMs(scenario: EncounterScenario): Long {
        val finalSpawnMs = ((scenario.steps.maxOfOrNull { it.atSeconds } ?: 0f) * 1_000f).toLong()
        return (finalSpawnMs + SCENARIO_TAIL_MS).coerceIn(MIN_REPLAY_INTERVAL_MS, MAX_REPLAY_INTERVAL_MS)
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
        private const val SCENARIO_TAIL_MS = 2_500L
        private const val MIN_REPLAY_INTERVAL_MS = 4_000L
        private const val MAX_REPLAY_INTERVAL_MS = 8_000L
        private const val PROFILE_POLL_MS = 100L
    }
}
