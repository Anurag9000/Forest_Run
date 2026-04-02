package com.yourname.forest_run

import android.os.SystemClock
import android.view.MotionEvent
import android.graphics.Bitmap
import android.content.ContentValues
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.os.Environment
import android.provider.MediaStore
import android.view.PixelCopy
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yourname.forest_run.engine.AppGameState
import com.yourname.forest_run.engine.EncounterDirector
import com.yourname.forest_run.engine.EncounterScenario
import com.yourname.forest_run.engine.GameView
import com.yourname.forest_run.engine.RunState
import com.yourname.forest_run.engine.SaveManager
import com.yourname.forest_run.entities.PlayerState
import com.yourname.forest_run.systems.GhostFrame
import com.yourname.forest_run.ui.MainMenuScreen
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class HardwareCoreFlowCaptureTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext.applicationContext
    private val captureRelativeDir = "${Environment.DIRECTORY_PICTURES}/forest_run_hardware/core_flow"

    @Before
    fun setUp() {
        targetContext.getSharedPreferences("forest_run_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        runShell("rm -rf /sdcard/Pictures/forest_run_hardware/core_flow && mkdir -p /sdcard/Pictures/forest_run_hardware/core_flow")
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
        enterPlayingState(gameView)
        instrumentation.runOnMainSync {
            val director = getPrivateField(gameView, "encounterDirector") as EncounterDirector
            setPrivateField(director, "selectedIndex", EncounterScenario.entries.indexOf(scenario))
            invokePrivate(gameView, "prepareEncounterScenario")
        }
        instrumentation.waitForIdleSync()
        waitForCondition("scenario becomes active", timeoutMs = 8_000L) {
            val director = getPrivateField(gameView, "encounterDirector") as EncounterDirector
            director.activeScenario == scenario &&
                getPrivateField(gameView, "runState") == RunState.PLAYING
        }
    }

    private fun enterPlayingState(gameView: GameView) {
        waitForCondition("menu initialized") {
            getPrivateField(gameView, "mainMenuScreen") != null
        }
        waitForCondition("game view laid out and rendering", timeoutMs = 8_000L) {
            gameView.width > 0 &&
                gameView.height > 0 &&
                gameView.debugFrameCounter > 10
        }

        val menu = getPrivateField(gameView, "mainMenuScreen") as MainMenuScreen
        val centerX = gameView.width / 2f
        val centerY = gameView.height / 2f

        tapGameView(gameView, centerX, centerY)
        waitForCondition("menu leaves idle phase") {
            menu.phase != MainMenuScreen.Phase.IDLE
        }
        waitForCondition("menu ready phase", timeoutMs = 8_000L) {
            menu.phase == MainMenuScreen.Phase.READY && gameView.debugFrameCounter > 20
        }

        tapGameView(gameView, centerX, centerY)
        waitForCondition("game enters playing state", timeoutMs = 8_000L) {
            getPrivateField(gameView, "appState") == AppGameState.PLAYING
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
        instrumentation.waitForIdleSync()
        lateinit var bitmap: Bitmap
        val copyLatch = CountDownLatch(1)
        var copyResult = PixelCopy.ERROR_UNKNOWN
        instrumentation.runOnMainSync {
            val activity = gameView.context as MainActivity
            val decorView = activity.window.decorView
            bitmap = Bitmap.createBitmap(decorView.width, decorView.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(
                activity.window,
                bitmap,
                { result ->
                    copyResult = result
                    copyLatch.countDown()
                },
                Handler(Looper.getMainLooper())
            )
        }
        check(copyLatch.await(3, TimeUnit.SECONDS)) {
            "Timed out waiting for PixelCopy for $name"
        }
        check(copyResult == PixelCopy.SUCCESS) {
            "PixelCopy failed for $name with result $copyResult"
        }
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, captureRelativeDir)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = targetContext.contentResolver
        val uri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        ) { "Failed to create MediaStore item for $name" }
        resolver.openOutputStream(uri)?.use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                "Failed to write bitmap for $name"
            }
        } ?: error("Failed to open output stream for $name")
        contentValues.clear()
        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)
        bitmap.recycle()
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

    private fun setPrivateField(target: Any, fieldName: String, value: Any) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun invokePrivate(target: Any, methodName: String) {
        val method = target.javaClass.getDeclaredMethod(methodName)
        method.isAccessible = true
        method.invoke(target)
    }

    private fun runShell(command: String) {
        val escaped = command.replace("'", "'\\''")
        instrumentation.uiAutomation.executeShellCommand("sh -c '$escaped'").close()
    }
}
