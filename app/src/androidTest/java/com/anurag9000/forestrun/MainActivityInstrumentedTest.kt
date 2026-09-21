package com.anurag9000.forestrun

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import com.anurag9000.forestrun.engine.AppGameState
import com.anurag9000.forestrun.engine.Biome
import com.anurag9000.forestrun.engine.GameConstants
import com.anurag9000.forestrun.engine.EntityManager
import com.anurag9000.forestrun.engine.GameView
import com.anurag9000.forestrun.engine.SafeContentTransform
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.PlayerState
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.engine.RunMode
import com.anurag9000.forestrun.engine.RunState
import com.anurag9000.forestrun.engine.SaveManager
import com.anurag9000.forestrun.ui.GardenLayoutPlan
import com.anurag9000.forestrun.ui.MainMenuScreen
import com.anurag9000.forestrun.systems.GhostPersistenceManager
import com.anurag9000.forestrun.systems.GhostPlayer
import com.anurag9000.forestrun.systems.GhostRecorder
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext.applicationContext

    @Before
    fun setUp() {
        GhostPersistenceManager.clearMemoryForTests()
        InstrumentationStateReset.clear(targetContext)
    }

    @Test
    fun launchesMainActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.packageName.startsWith("com.anurag9000.forestrun"))
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
            waitForCondition("live game thread advances after entering play", timeoutMs = 8_000L) {
                val thread = getPrivateField(gameView, "gameThread") as Thread
                thread.isAlive && gameView.debugFrameCounter > startFrameCount + 10
            }
            val entityManager = getPrivateField(gameView, "entityManager") as EntityManager
            waitForCondition("opening entities appear", timeoutMs = 5_000L) {
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
        SaveManager.saveLifetimeSeeds(targetContext, 50)
        SaveManager.saveGardenProgress(targetContext, 1)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireGameView(scenario)
            waitForCondition("live menu is ready for Garden entry", timeoutMs = 8_000L) {
                getPrivateField(gameView, "mainMenuScreen") != null &&
                    getPrivateField(gameView, "appState") == AppGameState.MENU &&
                    gameView.width > 0 &&
                    gameView.height > 0 &&
                    gameView.debugFrameCounter > 10
            }
            val menu = getPrivateField(gameView, "mainMenuScreen") as MainMenuScreen
            org.junit.Assert.assertEquals(MainMenuScreen.Phase.IDLE, menu.phase)
            val menuTransform = getPrivateField(gameView, "safeContentTransform") as SafeContentTransform
            tapLogical(
                gameView,
                menuTransform.logicalWidth * 0.175f,
                menuTransform.logicalHeight * 0.925f
            )

            waitForCondition("garden opens", timeoutMs = 8_000L) {
                getPrivateField(gameView, "appState") == AppGameState.GARDEN
            }

            val gardenScreen = requireNotNull(getPrivateField(gameView, "gardenScreen"))
            val layoutPlan = getPrivateField(gardenScreen, "layoutPlan") as GardenLayoutPlan
            val secondPlant = layoutPlan.plantCards[1]
            tapLogical(
                gameView,
                (secondPlant.left + secondPlant.right) / 2f,
                (secondPlant.top + secondPlant.bottom) / 2f
            )

            waitForCondition("garden unlock persists") {
                SaveManager.loadGardenProgress(targetContext) == 2 &&
                    SaveManager.loadLifetimeSeeds(targetContext) == 30
            }

            val transform = getPrivateField(gameView, "safeContentTransform") as SafeContentTransform
            tapLogical(
                gameView,
                transform.logicalWidth / 2f,
                transform.logicalHeight * 0.93f
            )
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

            mutateStopped(gameView) {
                val gameState = getPrivateField(gameView, "gameState") as com.anurag9000.forestrun.engine.GameStateManager
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

            mutateStopped(gameView) {
                val entityManager = getPrivateField(gameView, "entityManager") as EntityManager
                val player = getPrivateField(gameView, "player") as Player
                entityManager.debugSpawnAt(EntityType.CACTUS, player.x + 10f)
            }

            waitForCondition("run enters dying") {
                getPrivateField(gameView, "runState") == RunState.DYING
            }
            waitForCondition("run reaches game over", timeoutMs = 6_000L) {
                getPrivateField(gameView, "runState") == RunState.GAME_OVER
            }

            tapGameView(gameView, gameView.width / 2f, gameView.height / 2f)
            waitForCondition("restart finishes", timeoutMs = 6_000L) {
                getPrivateField(gameView, "runState") == RunState.PLAYING
            }
        }
    }

    @Test
    fun bloomPreventsImmediateCollisionDeath() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireGameView(scenario)
            enterPlayingState(gameView)

            mutateStopped(gameView) {
                val gameState = getPrivateField(gameView, "gameState") as com.anurag9000.forestrun.engine.GameStateManager
                repeat(GameConstants.BLOOM_SEED_COUNT) {
                    gameState.collectSeed()
                }
            }

            waitForCondition("bloom is active") {
                val player = getPrivateField(gameView, "player") as Player
                player.isInvincible
            }

            var conversionsBefore = -1
            mutateStopped(gameView) {
                val gameState = getPrivateField(gameView, "gameState") as com.anurag9000.forestrun.engine.GameStateManager
                val entityManager = getPrivateField(gameView, "entityManager") as EntityManager
                val player = getPrivateField(gameView, "player") as Player
                entityManager.reset()
                conversionsBefore = gameState.bloomConversionsThisRun
                entityManager.debugSpawnAt(EntityType.CACTUS, player.x + 10f)
            }

            val startFrame = gameView.debugFrameCounter
            waitForCondition("Bloom converts isolated Cactus while run stays live", timeoutMs = 4_000L) {
                val entityManager = getPrivateField(gameView, "entityManager") as EntityManager
                gameView.debugFrameCounter > startFrame &&
                    entityManager.debugActiveEntityCount == 0 &&
                    getPrivateField(gameView, "runState") == RunState.PLAYING
            }

            mutateStopped(gameView) {
                val gameState = getPrivateField(gameView, "gameState") as com.anurag9000.forestrun.engine.GameStateManager
                org.junit.Assert.assertEquals(conversionsBefore + 1, gameState.bloomConversionsThisRun)
                org.junit.Assert.assertEquals(RunState.PLAYING, getPrivateField(gameView, "runState"))
            }
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
                mutateStopped(gameView) {
                    val gameState = getPrivateField(gameView, "gameState") as com.anurag9000.forestrun.engine.GameStateManager
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

            mutateStopped(gameView) {
                val entityManager = getPrivateField(gameView, "entityManager") as EntityManager
                val gameState = getPrivateField(gameView, "gameState") as com.anurag9000.forestrun.engine.GameStateManager
                val player = getPrivateField(gameView, "player") as Player
                val expectedTypes = EntityType.values().toSet()

                entityManager.reset()
                // Keep every type in a valid common pre-entry lane. In
                // particular, Eagle intentionally despawns beyond
                // screenWidth + 150f, so an index-growing X offset would test
                // out-of-contract geometry rather than spawn/update viability.
                val stagingX = gameView.width + 100f
                EntityType.values().forEach { type ->
                    entityManager.debugSpawnAt(type, stagingX)
                }
                entityManager.update(
                    deltaTime = 1f / 60f,
                    gameState = gameState,
                    player = player,
                    runMode = RunMode.DEBUG_SCENARIO
                )

                val actualTypes =
                    entityManager.activeEntities.mapNotNull(entityManager::entityTypeOf).toSet()
                assertEquals(expectedTypes, actualTypes)
                assertEquals(expectedTypes.size, entityManager.debugActiveEntityCount)
            }

            val startFrameCount = gameView.debugFrameCounter
            waitForCondition("live loop continues with full entity roster", timeoutMs = 8_000L) {
                val thread = getPrivateField(gameView, "gameThread") as Thread
                thread.isAlive && gameView.debugFrameCounter > startFrameCount + 10
            }
        }
    }

    @Test
    fun bestRunPersistsGhostAndReloadsOnNextLaunch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireGameView(scenario)
            scenario.onActivity { activity ->
                SaveManager.saveBestDistance(activity, 0f)
                File(activity.filesDir, "ghost_run.bin").delete()
                File(activity.filesDir, "ghost_run.bin.bak").delete()
                File(activity.filesDir, "ghost_run.bin.new").delete()
            }
            enterPlayingState(gameView)

            waitForCondition("ghost recorder captures live frames", timeoutMs = 8_000L) {
                val recorder = getPrivateField(gameView, "ghostRecorder") as GhostRecorder
                recorder.recordedFrameCount >= 5
            }

            mutateStopped(gameView) {
                val gameState = getPrivateField(gameView, "gameState") as com.anurag9000.forestrun.engine.GameStateManager
                setPrivateField(gameState, "distanceMetres", 25f)
                val entityManager = getPrivateField(gameView, "entityManager") as EntityManager
                val player = getPrivateField(gameView, "player") as Player
                entityManager.reset()
                entityManager.debugSpawnAt(EntityType.CACTUS, player.x + 500f)
                val cactus = entityManager.activeEntities.last()
                cactus.shouldRecordPersistence = true
                cactus.x = player.x + 10f
            }

            waitForCondition("run enters dying for persistent best run", timeoutMs = 8_000L) {
                getPrivateField(gameView, "runState") == RunState.DYING
            }
            assertTrue(GhostPersistenceManager.awaitPendingWrites(10_000L))
            assertTrue(SaveManager.loadBestDistance(targetContext) >= 25f)
            assertTrue(SaveManager.loadGhostRun(targetContext).isNotEmpty())
        }

        GhostPersistenceManager.clearMemoryForTests()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val gameView = requireGameView(scenario)
            waitForCondition("ghost reloads from disk on next launch", timeoutMs = 8_000L) {
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

    private fun mutateStopped(gameView: GameView, mutation: () -> Unit) {
        instrumentation.runOnMainSync {
            assertTrue(
                "runtime producer must stop before instrumentation mutates live owners",
                gameView.pause()
            )
            try {
                mutation()
            } finally {
                gameView.resume()
            }
        }
        instrumentation.waitForIdleSync()
    }

    private fun tapLogical(gameView: GameView, logicalX: Float, logicalY: Float) {
        val transform = getPrivateField(gameView, "safeContentTransform") as SafeContentTransform
        val physical = transform.toPhysical(logicalX, logicalY)
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
