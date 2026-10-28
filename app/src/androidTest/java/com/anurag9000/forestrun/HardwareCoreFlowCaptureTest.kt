package com.anurag9000.forestrun

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.os.Environment
import android.content.ContentUris
import android.provider.MediaStore
import android.view.PixelCopy
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.anurag9000.forestrun.engine.AppGameState
import com.anurag9000.forestrun.engine.EncounterDirector
import com.anurag9000.forestrun.engine.EncounterScenario
import com.anurag9000.forestrun.engine.GameView
import com.anurag9000.forestrun.engine.RunMode
import com.anurag9000.forestrun.engine.RunState
import com.anurag9000.forestrun.engine.SaveManager
import com.anurag9000.forestrun.entities.PlayerState
import com.anurag9000.forestrun.systems.GhostFrame
import com.anurag9000.forestrun.systems.GhostPlayer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val COPY_PENDING = 0
private const val COPY_COMPLETED = 1
private const val COPY_TIMED_OUT = 2
private const val COPY_RECYCLED = 3

@LargeTest
@RunWith(AndroidJUnit4::class)
class HardwareCoreFlowCaptureTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext.applicationContext
    private val captureRelativeDir = "${Environment.DIRECTORY_PICTURES}/forest_run_hardware/core_flow"

    @Before
    fun setUp() {
        InstrumentationStateReset.clear(targetContext)
        clearPriorCaptures()
    }

    @Test
    fun captureOpeningReadabilityHardwarePass() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireGameView(scenario)
            prepareScenario(gameView, EncounterScenario.OPENING_READABILITY)

            captureAtOffsets(gameView, "opening_readability", 350L, 1100L, 2100L, 3000L)
        }
    }

    @Test
    fun captureBloomShowcaseHardwarePass() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireGameView(scenario)
            prepareScenario(gameView, EncounterScenario.BLOOM_SHOWCASE)

            captureAtOffsets(gameView, "bloom_showcase", 250L, 900L, 1800L, 2800L)
        }
    }

    @Test
    fun captureGhostReadabilityHardwarePass() {
        SaveManager.saveGhostRun(
            targetContext,
            listOf(
                GhostFrame(0.00f, 250f, 860f, PlayerState.RUNNING.ordinal, 1f, 1f),
                GhostFrame(0.35f, 250f, 780f, PlayerState.JUMPING.ordinal, 0.96f, 1.04f),
                GhostFrame(0.72f, 250f, 710f, PlayerState.APEX.ordinal, 0.92f, 1.08f),
                GhostFrame(1.05f, 250f, 790f, PlayerState.FALLING.ordinal, 1.0f, 1f),
                GhostFrame(1.42f, 250f, 860f, PlayerState.RUNNING.ordinal, 1f, 1f),
                GhostFrame(1.80f, 250f, 790f, PlayerState.JUMPING.ordinal, 0.96f, 1.04f),
                GhostFrame(2.15f, 250f, 860f, PlayerState.RUNNING.ordinal, 1f, 1f)
            )
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireGameView(scenario)
            prepareScenario(gameView, EncounterScenario.GHOST_READABILITY)

            val productionGhost = getPrivateField(gameView, "ghostPlayer") as GhostPlayer
            waitForCondition("production GameView ghost becomes visible in the scenario lane") {
                productionGhost.hasGhost && productionGhost.visibilityAlphaForTest > 0f
            }

            captureAtOffsets(gameView, "ghost_readability", 500L, 1300L, 2300L, 3200L)
        }
    }

    @Test
    fun captureRestLoopHardwarePass() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireGameView(scenario)
            prepareScenario(gameView, EncounterScenario.REST_LOOP)

            captureAtOffsets(gameView, "rest_loop_live", 500L)
            waitForCondition("run enters dying", timeoutMs = 8_000L) {
                getPrivateField(gameView, "runState") == RunState.DYING
            }
            saveScreenshot(gameView, "rest_loop_dying")
            waitForCondition("run reaches game over", timeoutMs = 8_000L) {
                getPrivateField(gameView, "runState") == RunState.GAME_OVER
            }
            saveScreenshot(gameView, "rest_loop_game_over")
            tapGameView(gameView, gameView.width / 2f, gameView.height / 2f)
            waitForCondition("returns to garden", timeoutMs = 8_000L) {
                getPrivateField(gameView, "appState") == AppGameState.GARDEN
            }
            saveScreenshot(gameView, "rest_loop_garden_return")
        }
    }

    private fun prepareScenario(gameView: GameView, scenario: EncounterScenario) {
        waitForCondition("game view laid out and rendering", timeoutMs = 8_000L) {
            gameView.width > 0 &&
                gameView.height > 0 &&
                gameView.holder.surface?.isValid == true &&
                gameView.debugFrameCounter > 10
        }

        val launchIntent = Intent(targetContext, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_DEBUG_SCENARIO, scenario.name)
            putExtra(MainActivity.EXTRA_RUN_MODE, RunMode.SCREENSHOT_CAPTURE.name)
            putExtra(MainActivity.EXTRA_DEBUG_AUTOSTART, true)
        }
        instrumentation.runOnMainSync {
            // Use the production deterministic-launch boundary so scenario
            // selection/reset is serialized with the live GameThread and the
            // capture can never spend even a transient frame in NORMAL mode.
            gameView.applyDebugLaunchIntent(launchIntent)
        }
        instrumentation.waitForIdleSync()
        waitForCondition("scenario becomes active", timeoutMs = 8_000L) {
            val director = getPrivateField(gameView, "encounterDirector") as EncounterDirector
            director.activeScenario == scenario &&
                getPrivateField(gameView, "runMode") == RunMode.SCREENSHOT_CAPTURE &&
                getPrivateField(gameView, "runState") == RunState.PLAYING
        }
    }

    private fun captureAtOffsets(gameView: GameView, prefix: String, vararg offsetsMs: Long) {
        val start = SystemClock.uptimeMillis()
        offsetsMs.forEachIndexed { index, targetOffset ->
            val waitMs = (start + targetOffset) - SystemClock.uptimeMillis()
            if (waitMs > 0) {
                SystemClock.sleep(waitMs)
            }
            saveScreenshot(gameView, "${prefix}_${index + 1}")
        }
    }

    private fun saveScreenshot(gameView: GameView, name: String) {
        val firstEligibleFrame = gameView.debugFrameCounter + 2L
        waitForCondition("two rendered game updates before $name", timeoutMs = 3_000L) {
            gameView.holder.surface?.isValid == true &&
                gameView.debugFrameCounter >= firstEligibleFrame
        }
        instrumentation.waitForIdleSync()
        var bitmap: Bitmap? = null
        try {
            val copyLatch = CountDownLatch(1)
            val copyState = AtomicInteger(COPY_PENDING)
            var copyResult = PixelCopy.ERROR_UNKNOWN
            instrumentation.runOnMainSync {
                val capturedBitmap = Bitmap.createBitmap(
                    gameView.width,
                    gameView.height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap = capturedBitmap
                PixelCopy.request(
                    gameView,
                    capturedBitmap,
                    { result ->
                        copyResult = result
                        if (copyState.compareAndSet(COPY_TIMED_OUT, COPY_RECYCLED)) {
                            if (!capturedBitmap.isRecycled) capturedBitmap.recycle()
                        } else {
                            copyState.compareAndSet(COPY_PENDING, COPY_COMPLETED)
                        }
                        copyLatch.countDown()
                    },
                    Handler(Looper.getMainLooper())
                )
            }
            if (!copyLatch.await(3, TimeUnit.SECONDS)) {
                if (copyState.compareAndSet(COPY_PENDING, COPY_TIMED_OUT)) {
                    bitmap = null // The callback owns recycling after a late completion.
                }
                error("Timed out waiting for PixelCopy for $name")
            }
            check(copyResult == PixelCopy.SUCCESS) {
                "PixelCopy failed for $name with result $copyResult"
            }
            check(hasRenderedContent(requireNotNull(bitmap))) {
                "PixelCopy returned a blank game surface for $name"
            }
            PendingCaptureMediaPublisher.publish(
                bitmap = requireNotNull(bitmap),
                displayName = "$name.png",
                relativePath = captureRelativeDir,
                mediaStore = ContentResolverCaptureMediaStore(targetContext.contentResolver)
            )
        } finally {
            bitmap?.let { if (!it.isRecycled) it.recycle() }
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

    private fun waitForCondition(label: String, timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for $label")
    }

    private fun getPrivateField(target: Any, fieldName: String): Any? {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(target)
    }

    private fun clearPriorCaptures() {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val ids: List<Long> = targetContext.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.RELATIVE_PATH}=?",
            arrayOf("$captureRelativeDir/"),
            null
        )?.use { cursor ->
            buildList<Long> {
                while (cursor.moveToNext()) add(cursor.getLong(0))
            }
        } ?: error("Could not query existing capture rows")

        ids.forEach { id ->
            val row = ContentUris.withAppendedId(collection, id)
            check(targetContext.contentResolver.delete(row, null, null) == 1) {
                "Could not remove prior capture row $id from $captureRelativeDir"
            }
        }
    }

    private fun hasRenderedContent(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val distinctSamples = HashSet<Int>()
        var nonBlackSamples = 0
        for (index in pixels.indices step 64) {
            val color = pixels[index]
            distinctSamples += color
            if (Color.red(color) > 8 || Color.green(color) > 8 || Color.blue(color) > 8) {
                nonBlackSamples++
            }
        }
        return distinctSamples.size >= 8 && nonBlackSamples >= 8
    }
}
