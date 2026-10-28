package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.birds.Duck
import com.anurag9000.forestrun.entities.birds.TitGroup
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
class BirdNamedScenarioMechanicsIntegrationTest {
    private lateinit var context: Context
    private lateinit var sprites: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearRunState()
        clearPresentationState()
        sprites = SpriteManager(context)
    }

    @After
    fun tearDown() {
        clearPresentationState()
    }

    @Test
    fun `authored duck teach scenario dispatches both isolated Duck encounters`() {
        val scenario = EncounterScenario.DUCK_TEACH
        val state = GameStateManager(context) { false }
        state.resetRun()
        scenario.initializeDebugRunState(state)
        val player = Player(1_920, 1_080, sprites)
        val entities = EntityManager(context, 1_920f, 1_080f, sprites)
        val director = EncounterDirector().apply {
            selectScenario(scenario)
            startSelectedScenario()
        }
        val ducks = java.util.Collections.newSetFromMap(IdentityHashMap<Duck, Boolean>())
        val delta = 1f / 60f

        repeat(12 * 60) {
            state.update(delta)
            player.update(delta, state.scrollSpeed)
            entities.update(
                deltaTime = delta,
                gameState = state,
                player = player,
                encounterDirector = director,
                runMode = RunMode.DEBUG_SCENARIO
            )
            entities.activeEntities.filterIsInstance<Duck>().forEach(ducks::add)
        }

        assertEquals("DUCK_TEACH must dispatch both authored Duck steps", 2, ducks.size)
        // DUCK_TEACH is intentionally an unscripted/manual scenario. Its
        // control response is covered by Duck's focused interaction tests;
        // this scenario-level contract is roster dispatch and debug isolation.
        assertEquals(0, PersistentMemoryManager.getEncounterCount(context, EntityType.DUCK))
    }

    @Test
    fun `authored tit wave scenario dispatches both flocks and advances their live wave`() {
        val scenario = EncounterScenario.TIT_WAVE
        val state = GameStateManager(context) { false }
        state.resetRun()
        scenario.initializeDebugRunState(state)
        val player = Player(1_920, 1_080, sprites)
        val entities = EntityManager(context, 1_920f, 1_080f, sprites)
        val director = EncounterDirector().apply {
            selectScenario(scenario)
            startSelectedScenario()
        }
        val observations = IdentityHashMap<TitGroup, MutableList<Float>>()
        val delta = 1f / 60f

        repeat(12 * 60) {
            state.update(delta)
            player.update(delta, state.scrollSpeed)

            entities.update(
                deltaTime = delta,
                gameState = state,
                player = player,
                encounterDirector = director,
                runMode = RunMode.DEBUG_SCENARIO
            )
            entities.activeEntities.filterIsInstance<TitGroup>().forEach { group ->
                observations.getOrPut(group) { mutableListOf() } += group.hitbox.top
            }
        }

        assertEquals("TIT_WAVE must dispatch both authored flocks", 2, observations.size)
        observations.values.forEach { samples ->
            assertTrue("Each authored flock must be observed across multiple updates", samples.size > 1)
            assertTrue("Each live Tit flock must move through its sine wave", samples.any { it != samples.first() })
        }
        // TIT_WAVE is intentionally manual; the live response prompts and
        // full-body trough rule are independently exercised by TitGroupTest.
        assertEquals(0, PersistentMemoryManager.getEncounterCount(context, EntityType.TIT))
    }

    private fun clearRunState() {
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun clearPresentationState() {
        ParticleManager.clear()
        DialogueBubbleManager.clear()
        FlavorTextManager.clear()
    }
}
