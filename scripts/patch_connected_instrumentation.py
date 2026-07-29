#!/usr/bin/env python3
"""Make connected tests isolated, production-coordinate-driven, and zero-skipped."""

from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    main_test = Path(
        "app/src/androidTest/java/com/anurag9000/forestrun/MainActivityInstrumentedTest.kt"
    )
    replace_once(
        main_test,
        '''import com.anurag9000.forestrun.engine.GameView
''',
        '''import com.anurag9000.forestrun.engine.GameView
import com.anurag9000.forestrun.engine.SafeContentTransform
''',
        "safe-content import",
    )
    replace_once(
        main_test,
        '''import com.anurag9000.forestrun.ui.MainMenuScreen
''',
        '''import com.anurag9000.forestrun.ui.GardenLayoutPlan
import com.anurag9000.forestrun.ui.MainMenuScreen
''',
        "Garden layout import",
    )
    replace_once(
        main_test,
        '''import com.anurag9000.forestrun.systems.GhostPlayer
''',
        '''import com.anurag9000.forestrun.systems.GhostPersistenceManager
import com.anurag9000.forestrun.systems.GhostPlayer
import com.anurag9000.forestrun.systems.GhostRecorder
''',
        "ghost persistence imports",
    )
    replace_once(
        main_test,
        '''import org.junit.Ignore
''',
        '''import org.junit.Before
''',
        "remove ignored-test import",
    )
    replace_once(
        main_test,
        '''class MainActivityInstrumentedTest {

    @Test
''',
        '''class MainActivityInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext.applicationContext

    @Before
    fun setUp() {
        GhostPersistenceManager.clearMemoryForTests()
        InstrumentationStateReset.clear(targetContext)
    }

    @Test
''',
        "per-test persistent state reset",
    )
    replace_once(
        main_test,
        '''            val startFrameCount = gameView.debugFrameCounter
            waitForCondition("game loop advances after entering play", timeoutMs = 2_000L) {
                gameView.debugFrameCounter > startFrameCount + 10
            }
            val entityManager = getPrivateField(gameView, "entityManager") as EntityManager
            waitForCondition("opening entities appear", timeoutMs = 2_000L) {
                entityManager.debugActiveEntityCount > 0
            }
''',
        '''            val startFrameCount = gameView.debugFrameCounter
            waitForCondition("live game thread advances after entering play", timeoutMs = 8_000L) {
                val thread = getPrivateField(gameView, "gameThread") as Thread
                thread.isAlive && gameView.debugFrameCounter > startFrameCount + 10
            }
            val entityManager = getPrivateField(gameView, "entityManager") as EntityManager
            waitForCondition("opening entities appear", timeoutMs = 5_000L) {
                entityManager.debugActiveEntityCount > 0
            }
''',
        "connected software-renderer frame budget",
    )
    replace_once(
        main_test,
        '''            val startFrameCount = gameView.debugFrameCounter
            waitForCondition("loop continues updating with full entity roster", timeoutMs = 2_000L) {
                gameView.debugFrameCounter > startFrameCount + 10
            }
''',
        '''            val startFrameCount = gameView.debugFrameCounter
            waitForCondition("live loop continues with full entity roster", timeoutMs = 8_000L) {
                val thread = getPrivateField(gameView, "gameThread") as Thread
                thread.isAlive && gameView.debugFrameCounter > startFrameCount + 10
            }
''',
        "full-roster software-renderer frame budget",
    )
    replace_once(
        main_test,
        '''        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        SaveManager.saveLifetimeSeeds(appContext, 50)
        SaveManager.saveGardenProgress(appContext, 1)
''',
        '''        SaveManager.saveLifetimeSeeds(targetContext, 50)
        SaveManager.saveGardenProgress(targetContext, 1)
''',
        "use isolated target context",
    )
    replace_once(
        main_test,
        '''            val gameView = requireGameView(scenario)
            tapGameView(gameView, gameView.width * 0.10f, gameView.height * 0.92f)

            waitForCondition("garden opens") {
''',
        '''            val gameView = requireGameView(scenario)
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
''',
        "ready safe logical Garden entry tap",
    )
    replace_once(
        main_test,
        '''            val cardWidth = gameView.width / 10.5f
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
''',
        '''            val gardenScreen = requireNotNull(getPrivateField(gameView, "gardenScreen"))
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
''',
        "production Garden and safe-content coordinates",
    )
    replace_once(
        main_test,
        '''    @Ignore("On-device best-run persistence visibility remains flaky; covered by host SaveManager roundtrip tests.")
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
                val gameState = getPrivateField(gameView, "gameState") as com.anurag9000.forestrun.engine.GameStateManager
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
''',
        '''    @Test
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
                recorder.frames.size >= 5
            }

            scenario.onActivity {
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
''',
        "persistent async ghost disk reload test",
    )
    replace_once(
        main_test,
        '''    private fun tapGameView(gameView: GameView, x: Float, y: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
''',
        '''    private fun tapLogical(gameView: GameView, logicalX: Float, logicalY: Float) {
        val transform = getPrivateField(gameView, "safeContentTransform") as SafeContentTransform
        val physical = transform.toPhysical(logicalX, logicalY)
        tapGameView(gameView, physical.x, physical.y)
    }

    private fun tapGameView(gameView: GameView, x: Float, y: Float) {
''',
        "logical-to-physical connected touch helper",
    )

    capture_test = Path(
        "app/src/androidTest/java/com/anurag9000/forestrun/HardwareCoreFlowCaptureTest.kt"
    )
    replace_once(
        capture_test,
        '''import androidx.test.ext.junit.runners.AndroidJUnit4
''',
        '''import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
''',
        "LargeTest import",
    )
    replace_once(
        capture_test,
        '''@RunWith(AndroidJUnit4::class)
class HardwareCoreFlowCaptureTest {
''',
        '''@LargeTest
@RunWith(AndroidJUnit4::class)
class HardwareCoreFlowCaptureTest {
''',
        "mark screenshot capture as large hardware evidence",
    )
    replace_once(
        capture_test,
        '''    @Before
    fun setUp() {
        targetContext.getSharedPreferences("forest_run_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        runShell("rm -rf /sdcard/Pictures/forest_run_hardware/core_flow && mkdir -p /sdcard/Pictures/forest_run_hardware/core_flow")
    }
''',
        '''    @Before
    fun setUp() {
        InstrumentationStateReset.clear(targetContext)
        targetContext.contentResolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Images.Media.RELATIVE_PATH}=?",
            arrayOf("$captureRelativeDir/")
        )
        runShell("rm -rf /sdcard/Pictures/forest_run_hardware/core_flow && mkdir -p /sdcard/Pictures/forest_run_hardware/core_flow")
    }
''',
        "reset every persisted namespace and MediaStore row",
    )


if __name__ == "__main__":
    main()
