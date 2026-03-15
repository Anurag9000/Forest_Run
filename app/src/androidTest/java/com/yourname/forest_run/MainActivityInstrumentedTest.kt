package com.yourname.forest_run

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import com.yourname.forest_run.engine.AppGameState
import com.yourname.forest_run.engine.Biome
import com.yourname.forest_run.engine.GameConstants
import com.yourname.forest_run.engine.EntityManager
import com.yourname.forest_run.engine.GameView
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.entities.PlayerState
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.engine.RunState
import com.yourname.forest_run.engine.SaveManager
import com.yourname.forest_run.ui.MainMenuScreen
import com.yourname.forest_run.systems.GhostPlayer
import org.junit.Ignore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {

    @Test
    fun launchesMainActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.packageName.startsWith("com.yourname.forest_run"))
                assertNotNull(activity.findViewById(android.R.id.content))
            }
        }
    }

    @Test
    fun menuFlowTransitionsIntoPlayingOnDevice() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireGameView(scenario)
            enterPlayingState(gameView)
            assertEquals(AppGameState.PLAYING, getPrivateField(gameView, "appState"))
        }
    }

    @Test
    fun gameplayProgressesAndPlayerCanJumpOnDevice() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireGameView(scenario)
            enterPlayingState(gameView)

            val startFrameCount = gameView.debugFrameCounter
            waitForCondition("game loop advances after entering play", timeoutMs = 2_000L) {
                gameView.debugFrameCounter > startFrameCount + 10
            }
            val entityManager = getPrivateField(gameView, "entityManager") as EntityManager
            waitForCondition("opening entities appear", timeoutMs = 2_000L) {
                entityManager.debugActiveEntityCount > 0
            }

            val player = getPrivateField(gameView, "player") as Player
            tapGameView(gameView, gameView.width / 2f, gameView.height / 2f)
            waitForCondition("player leaves running state", timeoutMs = 1_500L) {
                player.state in setOf(PlayerState.JUMP_START, PlayerState.JUMPING, PlayerState.APEX, PlayerState.FALLING, PlayerState.LANDING)
            }

            assertTrue(gameView.debugFrameCounter > startFrameCount + 10)
            assertTrue(entityManager.debugActiveEntityCount > 0)
        }
    }

    @Test
    fun gardenFlowUnlocksPlantPersistsAndReturnsToMenu() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        SaveManager.saveLifetimeSeeds(appContext, 50)
        SaveManager.saveGardenProgress(appContext, 1)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireGameView(scenario)
            tapGameView(gameView, gameView.width * 0.10f, gameView.height * 0.92f)

            waitForCondition("garden opens") {
                getPrivateField(gameView, "appState") == AppGameState.GARDEN
            }

            val cardWidth = gameView.width / 10.5f
            val cardGap = cardWidth * 0.12f
            val rowStartX = (gameView.width - (9 * (cardWidth + cardGap) - cardGap)) / 2f
            val rowY = gameView.height * 0.20f
            val tapX = rowStartX + (cardWidth + cardGap) + cardWidth / 2f
            val tapY = rowY + (gameView.height * 0.55f) / 2f
            tapGameView(gameView, tapX, tapY)

            waitForCondition("garden unlock persists") {
                SaveManager.loadGardenProgress(appContext) == 2 &&
                    SaveManager.loadLifetimeSeeds(appContext) == 30
            }

            tapGameView(gameView, gameView.width / 2f, gameView.height * 0.93f)
            waitForCondition("returns to menu") {
                getPrivateField(gameView, "appState") == AppGameState.MENU
            }
        }
    }

    @Test
    fun bloomActivationSyncsIntoPlayerState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireGameView(scenario)
            enterPlayingState(gameView)

            scenario.onActivity {
                val gameState = getPrivateField(gameView, "gameState") as com.yourname.forest_run.engine.GameStateManager
                repeat(GameConstants.BLOOM_SEED_COUNT) {
                    gameState.collectSeed()
                }
            }

            waitForCondition("player enters bloom") {
                val player = getPrivateField(gameView, "player") as Player
                player.state == PlayerState.BLOOM || player.isInvincible
            }
        }
    }

    @Test
    fun collisionLeadsToGameOverAndRestart() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireGameView(scenario)
            enterPlayingState(gameView)

            scenario.onActivity {
                val entityManager = getPrivateField(gameView, "entityManager") as EntityManager
                val player = getPrivateField(gameView, "player") as Player
                entityManager.debugSpawnAt(EntityType.CACTUS, player.x + 10f)
            }

            waitForCondition("run enters dying") {
                getPrivateField(gameView, "runState") == RunState.DYING
            }
            waitForCondition("run reaches game over", timeoutMs = 3_000L) {
                getPrivateField(gameView, "runState") == RunState.GAME_OVER
            }

            tapGameView(gameView, gameView.width / 2f, gameView.height / 2f)
            waitForCondition("restart finishes", timeoutMs = 3_000L) {
                getPrivateField(gameView, "runState") == RunState.PLAYING
            }
        }
    }

    @Test
    fun bloomPreventsImmediateCollisionDeath() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireGameView(scenario)
            enterPlayingState(gameView)

            scenario.onActivity {
                val gameState = getPrivateField(gameView, "gameState") as com.yourname.forest_run.engine.GameStateManager
                repeat(GameConstants.BLOOM_SEED_COUNT) {
                    gameState.collectSeed()
                }
            }

            waitForCondition("bloom is active") {
                val player = getPrivateField(gameView, "player") as Player
                player.isInvincible
            }

            scenario.onActivity {
                val entityManager = getPrivateField(gameView, "entityManager") as EntityManager
                val player = getPrivateField(gameView, "player") as Player
                entityManager.debugSpawnAt(EntityType.CACTUS, player.x + 10f)
            }

            SystemClock.sleep(800)
            org.junit.Assert.assertEquals(RunState.PLAYING, getPrivateField(gameView, "runState"))
        }
    }

    @Test
    fun biomeCycleTransitionsAcrossLongRunOnDevice() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireGameView(scenario)
            enterPlayingState(gameView)

            val checkpoints = listOf(
                0f to Biome.MEADOW,
                GameConstants.BIOME_LENGTH_METRES * 1.05f to Biome.ORCHARD,
                GameConstants.BIOME_LENGTH_METRES * 2.05f to Biome.ANCIENT_GROVE,
                GameConstants.BIOME_LENGTH_METRES * 3.05f to Biome.DUSK_CANYON,
                GameConstants.BIOME_LENGTH_METRES * 4.05f to Biome.NIGHT_FOREST
            )

            checkpoints.forEach { (distance, biome) ->
                scenario.onActivity {
                    val gameState = getPrivateField(gameView, "gameState") as com.yourname.forest_run.engine.GameStateManager
                    setPrivateField(gameState, "distanceMetres", distance)
                }

                waitForCondition("biome switches to $biome") {
                    val entityManager = getPrivateField(gameView, "entityManager") as EntityManager
                    entityManager.biomeManager.currentBiome == biome
                }
            }
        }
    }

    @Test
    fun allEntityTypesSpawnAndUpdateOnDevice() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireGameView(scenario)
            enterPlayingState(gameView)

            scenario.onActivity {
                val entityManager = getPrivateField(gameView, "entityManager") as EntityManager
                entityManager.reset()
                EntityType.values().forEachIndexed { index, type ->
                    entityManager.debugSpawnAt(type, gameView.width + 300f + index * 220f)
                }
            }

            val expectedCount = EntityType.values().size
            waitForCondition("all entity types remain active for at least one live update") {
                val entityManager = getPrivateField(gameView, "entityManager") as EntityManager
                entityManager.debugActiveEntityCount >= expectedCount
            }

            val startFrameCount = gameView.debugFrameCounter
            waitForCondition("loop continues updating with full entity roster", timeoutMs = 2_000L) {
                gameView.debugFrameCounter > startFrameCount + 10
            }
        }
    }

    @Ignore("On-device best-run persistence visibility remains flaky; covered by host SaveManager roundtrip tests.")
    @Test
    fun bestRunPersistsGhostAndReloadsOnNextLaunch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireGameView(scenario)
            scenario.onActivity { activity ->
                SaveManager.saveBestDistance(activity, 0f)
                File(activity.filesDir, "ghost_run.bin").delete()
            }
            enterPlayingState(gameView)

            scenario.onActivity {
                val gameState = getPrivateField(gameView, "gameState") as com.yourname.forest_run.engine.GameStateManager
                setPrivateField(gameState, "distanceMetres", 25f)
                val entityManager = getPrivateField(gameView, "entityManager") as EntityManager
                val player = getPrivateField(gameView, "player") as Player
                entityManager.debugSpawnAt(EntityType.CACTUS, player.x + 10f)
            }

            waitForCondition("run enters dying for best run") {
                getPrivateField(gameView, "runState") == RunState.DYING
            }
            waitForCondition("best distance saved", timeoutMs = 4_000L) {
                val saved = AtomicReference(0f)
                val hasGhost = AtomicBoolean(false)
                scenario.onActivity { activity ->
                    saved.set(SaveManager.loadBestDistance(activity))
                    hasGhost.set(SaveManager.hasGhostRun(activity))
                }
                saved.get() > 0f && hasGhost.get()
            }
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireGameView(scenario)
            waitForCondition("ghost reloads on next launch") {
                val ghostPlayer = getPrivateField(gameView, "ghostPlayer") as GhostPlayer
                ghostPlayer.hasGhost
            }
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

    private fun enterPlayingState(gameView: GameView) {
        waitForCondition("menu initialized") {
            getPrivateField(gameView, "mainMenuScreen") != null
        }

        val menu = getPrivateField(gameView, "mainMenuScreen") as MainMenuScreen
        val centerX = gameView.width / 2f
        val centerY = gameView.height / 2f

        tapGameView(gameView, centerX, centerY)
        waitForCondition("menu ready phase") {
            menu.phase == MainMenuScreen.Phase.READY
        }

        tapGameView(gameView, centerX, centerY)
        waitForCondition("game enters playing state") {
            getPrivateField(gameView, "appState") == AppGameState.PLAYING
        }
    }

    private fun tapGameView(gameView: GameView, x: Float, y: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
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
}
