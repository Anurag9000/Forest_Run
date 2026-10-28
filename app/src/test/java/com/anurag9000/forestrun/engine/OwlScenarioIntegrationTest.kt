package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.birds.Owl
import com.anurag9000.forestrun.systems.ParticleManager
import com.anurag9000.forestrun.ui.DialogueBubbleManager
import com.anurag9000.forestrun.ui.FlavorTextManager
import java.util.IdentityHashMap
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OwlScenarioIntegrationTest {
    private lateinit var context: Context
    private lateinit var sprites: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        ParticleManager.clear()
        DialogueBubbleManager.clear()
        FlavorTextManager.clear()
        sprites = SpriteManager(context)
    }

    @After
    fun tearDown() {
        ParticleManager.clear()
        DialogueBubbleManager.clear()
        FlavorTextManager.clear()
    }

    @Test
    fun `authored owl dive scenario sends first Owl from alert into dive without persistence`() {
        val scenario = EncounterScenario.OWL_DIVE
        val state = GameStateManager(context) { false }
        state.resetRun()
        scenario.initializeDebugRunState(state)
        val player = Player(1_920, 1_080, sprites)
        val entities = EntityManager(context, 1_920f, 1_080f, sprites)
        val director = EncounterDirector().apply {
            selectScenario(scenario)
            startSelectedScenario()
        }
        val script = DebugScenarioScript().apply { prepare(scenario) }
        val observations = IdentityHashMap<Owl, MutableSet<String>>()
        var firstOwl: Owl? = null
        val delta = 1f / 60f

        repeat(12 * 60) {
            state.update(delta)
            script.advanceTimed(state.runTimeSeconds) { action ->
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
            player.update(delta, state.scrollSpeed)
            entities.update(
                deltaTime = delta,
                gameState = state,
                player = player,
                encounterDirector = director,
                runMode = RunMode.DEBUG_SCENARIO
            )
            entities.activeEntities.filterIsInstance<Owl>().forEach { owl ->
                if (firstOwl == null) firstOwl = owl
                observations.getOrPut(owl) { mutableSetOf() }.add(owlState(owl))
            }
        }

        assertTrue("The authored scenario must construct an Owl", firstOwl != null)
        val states = observations.getValue(requireNotNull(firstOwl))
        assertTrue("The scripted visible-player jump must trigger ALERT: $states", "ALERT" in states)
        assertTrue("The authored alert must advance into DIVING: $states", "DIVING" in states)
        assertEquals(1, script.traceSnapshot().events.size)
        assertTrue(DeterministicScenarioReplayContract.matches(script.traceSnapshot()))
        assertEquals(0, PersistentMemoryManager.getEncounterCount(context, EntityType.OWL))
    }

    private fun owlState(owl: Owl): String =
        Owl::class.java.getDeclaredField("owlState").let { field ->
            field.isAccessible = true
            requireNotNull(field.get(owl)).toString()
        }
}
