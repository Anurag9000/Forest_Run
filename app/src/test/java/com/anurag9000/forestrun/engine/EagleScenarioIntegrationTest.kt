package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.birds.Eagle
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
class EagleScenarioIntegrationTest {
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
    fun `eagle mark scenario drives both authored Eagles through target announcement and lock`() {
        val scenario = EncounterScenario.EAGLE_MARK
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
        val observed = IdentityHashMap<Eagle, MutableSet<EagleObservation>>()
        val delta = 1f / 60f

        repeat(20 * 60) {
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
            entities.activeEntities.filterIsInstance<Eagle>().forEach { eagle ->
                observe(eagle, observed)
            }
            entities.checkCollisions(player, state)
            entities.activeEntities.filterIsInstance<Eagle>().forEach { eagle ->
                observe(eagle, observed)
            }
        }

        assertEquals("The authored scenario has two Eagle encounters", 2, observed.size)
        observed.values.forEachIndexed { index, states ->
            assertTrue("Eagle ${index + 1} never announced its target: $states",
                states.any { it.targetAnnounced })
            assertTrue("Eagle ${index + 1} never locked the announced target: $states",
                states.any { it.locked })
        }
        assertEquals(4, script.traceSnapshot().events.size)
        assertTrue(DeterministicScenarioReplayContract.matches(script.traceSnapshot()))
        assertEquals(0, PersistentMemoryManager.getEncounterCount(context, EntityType.EAGLE))
    }

    private fun observe(
        eagle: Eagle,
        observations: IdentityHashMap<Eagle, MutableSet<EagleObservation>>
    ) {
        observations.getOrPut(eagle) { mutableSetOf() }.add(
            EagleObservation(
                targetAnnounced = privateBoolean(eagle, "targetAnnounced"),
                locked = privateBoolean(eagle, "isLocked")
            )
        )
    }

    private fun privateBoolean(eagle: Eagle, fieldName: String): Boolean =
        Eagle::class.java.getDeclaredField(fieldName).let { field ->
            field.isAccessible = true
            field.getBoolean(eagle)
        }

    private data class EagleObservation(
        val targetAnnounced: Boolean,
        val locked: Boolean
    )
}
