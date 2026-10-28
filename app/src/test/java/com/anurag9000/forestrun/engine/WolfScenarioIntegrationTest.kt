package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.animals.Wolf
import com.anurag9000.forestrun.systems.ParticleManager
import com.anurag9000.forestrun.ui.DialogueBubbleManager
import com.anurag9000.forestrun.ui.FlavorTextManager
import java.util.IdentityHashMap
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.robolectric.annotation.Config
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WolfScenarioIntegrationTest {
    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        spriteManager = SpriteManager(context)
    }

    @After
    fun tearDown() {
        ParticleManager.clear()
        DialogueBubbleManager.clear()
        FlavorTextManager.clear()
    }

    @Test
    fun `wolf scenario mercy fixture is run local and reset leaves durable progress alone`() {
        SaveManager.saveHighScore(context, 900)
        SaveManager.saveLifetimeSeeds(context, 17)
        val debug = GameStateManager(context) { false }

        debug.resetRun()
        EncounterScenario.WOLF_CHARGE.initializeDebugRunState(debug)

        assertEquals(8, debug.mercyHearts)
        assertEquals(0, debug.mercyMissesThisRun)
        assertEquals(0, debug.sparedThisRun)

        debug.addBonus(points = 1_100, seeds = 3)
        debug.save()
        assertEquals(8, debug.mercyHearts)
        assertEquals(900, SaveManager.loadHighScore(context))
        assertEquals(17, SaveManager.loadLifetimeSeeds(context))

        debug.resetRun()
        assertEquals(0, debug.mercyHearts)
        assertEquals(0, debug.mercyMissesThisRun)
    }

    @Test
    fun `wolf charge scripted run spares both wolves without persistent progression`() {
        val scenario = EncounterScenario.WOLF_CHARGE
        val gameState = GameStateManager(context) { false }
        gameState.resetRun()
        scenario.initializeDebugRunState(gameState)
        val player = Player(1_920, 1_080, spriteManager)
        val entities = EntityManager(context, 1_920f, 1_080f, spriteManager)
        val director = EncounterDirector().apply {
            selectScenario(scenario)
            startSelectedScenario()
        }
        val script = DebugScenarioScript().apply { prepare(scenario) }
        val observedWolfStates = IdentityHashMap<Wolf, MutableSet<String>>()
        val delta = 1f / 60f

        repeat(20 * 60) {
            gameState.update(delta)
            script.advanceTimed(gameState.runTimeSeconds) { action ->
                when (action.action) {
                    DebugScenarioAction.TAP_JUMP -> {
                        player.onJumpPressed()
                        player.onJumpReleased(0f)
                    }
                    DebugScenarioAction.HOLD_JUMP_START -> player.onJumpPressed()
                    DebugScenarioAction.HOLD_JUMP_END ->
                        player.onJumpReleased(action.holdDurationSeconds)
                    DebugScenarioAction.DUCK_START -> player.onDuckPressed()
                    DebugScenarioAction.DUCK_END -> player.onDuckReleased()
                }
            }
            player.update(delta, gameState.scrollSpeed)
            entities.update(
                deltaTime = delta,
                gameState = gameState,
                player = player,
                encounterDirector = director,
                runMode = RunMode.DEBUG_SCENARIO
            )
            entities.activeEntities.filterIsInstance<Wolf>().forEach { wolf ->
                observedWolfStates.getOrPut(wolf) { mutableSetOf() }
                    .add(wolfState(wolf))
            }
            entities.checkCollisions(player, gameState)
            entities.activeEntities.filterIsInstance<Wolf>().forEach { wolf ->
                observedWolfStates.getOrPut(wolf) { mutableSetOf() }
                    .add(wolfState(wolf))
            }
        }

        assertEquals(2, gameState.sparedThisRun)
        assertEquals(2, observedWolfStates.size)
        observedWolfStates.values.forEach { states ->
            assertTrue("Wolf never entered HOWLING: $states", "HOWLING" in states)
            assertTrue("Wolf never entered CHARGING: $states", "CHARGING" in states)
            assertTrue("Wolf never entered SPARED: $states", "SPARED" in states)
        }
        assertEquals(0, gameState.hitsThisRun)
        assertEquals(0, gameState.cleanPassesThisRun)
        assertTrue(DeterministicScenarioReplayContract.matches(script.traceSnapshot()))
        assertEquals(0, PersistentMemoryManager.getEncounterCount(context, EntityType.WOLF))
        assertEquals(0, PersistentMemoryManager.getSparedCount(context, EntityType.WOLF))
    }

    private fun wolfState(wolf: Wolf): String {
        val field = Wolf::class.java.getDeclaredField("wolfState")
        field.isAccessible = true
        return requireNotNull(field.get(wolf)).toString()
    }
}
