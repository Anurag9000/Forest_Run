package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.birds.ChickadeeGroup
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
class ChickadeeScenarioIntegrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearRunState()
        clearPresentationState()
    }

    @After
    fun tearDown() {
        clearPresentationState()
    }

    @Test
    fun `authored chickadee swerve scenario produces repeatable seeded altitude changes`() {
        val first = runScenario()
        val second = runScenario()

        assertEquals("Both authored Chickadees must be constructed", 2, first.samples.size)
        assertEquals(first.samples, second.samples)
        first.samples.forEachIndexed { index, samples ->
            assertTrue("Chickadee ${index + 1} must be observed across multiple frames", samples.size > 1)
            val initial = samples.first()
            assertTrue(
                "Chickadee ${index + 1} never changed altitude in CHICKADEE_SWERVE",
                samples.drop(1).any { frame -> frame.indices.any { frame[it] != initial[it] } }
            )
        }
        assertEquals(0, PersistentMemoryManager.getEncounterCount(context, EntityType.CHICKADEE))
    }

    private fun runScenario(): ScenarioSamples {
        clearRunState()
        clearPresentationState()
        val sprites = SpriteManager(context)
        val scenario = EncounterScenario.CHICKADEE_SWERVE
        val state = GameStateManager(context) { false }
        state.resetRun()
        scenario.initializeDebugRunState(state)
        val player = Player(1_920, 1_080, sprites)
        val entities = EntityManager(context, 1_920f, 1_080f, sprites)
        val director = EncounterDirector().apply {
            selectScenario(scenario)
            startSelectedScenario()
        }
        val observations = IdentityHashMap<ChickadeeGroup, MutableList<List<Float>>>()
        val encounterOrder = mutableListOf<ChickadeeGroup>()
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
            entities.activeEntities.filterIsInstance<ChickadeeGroup>().forEach { group ->
                if (group !in observations) {
                    encounterOrder += group
                    observations[group] = mutableListOf()
                }
                observations.getValue(group) += readAltitudes(group).toList()
            }
        }

        return ScenarioSamples(encounterOrder.map { observations.getValue(it).toList() })
    }

    private fun readAltitudes(group: ChickadeeGroup): FloatArray =
        ChickadeeGroup::class.java.getDeclaredField("altitudes").let { field ->
            field.isAccessible = true
            field.get(group) as FloatArray
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

    private data class ScenarioSamples(val samples: List<List<List<Float>>>)
}
