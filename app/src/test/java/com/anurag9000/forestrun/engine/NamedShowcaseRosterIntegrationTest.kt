package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.animals.Wolf
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
class NamedShowcaseRosterIntegrationTest {
    private lateinit var context: Context
    private lateinit var sprites: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        sprites = SpriteManager(context)
        clearPresentationState()
    }

    @After
    fun tearDown() {
        clearPresentationState()
    }

    @Test
    fun `bird showcase dispatches its real ordered roster and reaches Eagle lock`() {
        val run = runShowcase(EncounterScenario.BIRD_SHOWCASE)

        assertEquals(
            listOf(
                EntityType.DUCK,
                EntityType.TIT,
                EntityType.CHICKADEE,
                EntityType.OWL,
                EntityType.EAGLE
            ),
            run.entities.map(::entityType)
        )
        assertTrue(
            "The Bird Showcase Eagle did not advance through its autonomous mark lock",
            run.entities.filterIsInstance<Eagle>().any { booleanField(it, "isLocked") }
        )
        assertNoScenarioPersistence(run)
    }

    @Test
    fun `animal showcase dispatches its real ordered roster and Wolf howl charge`() {
        val run = runShowcase(EncounterScenario.ANIMAL_SHOWCASE)

        assertEquals(
            listOf(
                EntityType.CAT,
                EntityType.FOX,
                EntityType.WOLF,
                EntityType.HEDGEHOG,
                EntityType.DOG
            ),
            run.entities.map(::entityType)
        )
        val wolfStates = run.wolfStates
        assertTrue("The Animal Showcase Wolf never entered its howl telegraph: $wolfStates", "HOWLING" in wolfStates)
        assertTrue("The Animal Showcase Wolf never advanced into its charge: $wolfStates", "CHARGING" in wolfStates)
        assertNoScenarioPersistence(run)
    }

    private data class ShowcaseRun(
        val entities: List<Entity>,
        val wolfStates: Set<String>
    )

    private fun runShowcase(scenario: EncounterScenario): ShowcaseRun {
        val state = GameStateManager(context) { false }
        state.resetRun()
        scenario.initializeDebugRunState(state)
        val player = Player(1_920, 1_080, sprites)
        val manager = EntityManager(context, 1_920f, 1_080f, sprites)
        val director = EncounterDirector().apply {
            selectScenario(scenario)
            startSelectedScenario()
        }
        val observed = java.util.Collections.newSetFromMap(
            IdentityHashMap<Entity, Boolean>()
        )
        val encounterOrder = mutableListOf<Entity>()
        val wolfStates = mutableSetOf<String>()
        val delta = 1f / 60f

        repeat(18 * 60) {
            state.update(delta)
            player.update(delta, state.scrollSpeed)
            manager.update(
                deltaTime = delta,
                gameState = state,
                player = player,
                encounterDirector = director,
                runMode = RunMode.DEBUG_SCENARIO
            )
            manager.activeEntities.forEach { entity ->
                if (observed.add(entity)) encounterOrder += entity
                if (scenario == EncounterScenario.ANIMAL_SHOWCASE && entity is Wolf) {
                    wolfStates += enumField(entity, "wolfState")
                }
            }
        }

        assertEquals("Showcase did not dispatch every authored step", 0, director.remainingSteps)
        return ShowcaseRun(encounterOrder, wolfStates)
    }

    private fun assertNoScenarioPersistence(run: ShowcaseRun) {
        assertTrue(run.entities.all { !it.shouldRecordPersistence })
        run.entities.map(::entityType).distinct().forEach { type ->
            assertEquals(0, PersistentMemoryManager.getEncounterCount(context, type))
        }
    }

    private fun entityType(entity: Entity): EntityType = when (entity) {
        is com.anurag9000.forestrun.entities.birds.Duck -> EntityType.DUCK
        is com.anurag9000.forestrun.entities.birds.TitGroup -> EntityType.TIT
        is com.anurag9000.forestrun.entities.birds.ChickadeeGroup -> EntityType.CHICKADEE
        is com.anurag9000.forestrun.entities.birds.Owl -> EntityType.OWL
        is Eagle -> EntityType.EAGLE
        is com.anurag9000.forestrun.entities.animals.Cat -> EntityType.CAT
        is com.anurag9000.forestrun.entities.animals.Fox -> EntityType.FOX
        is Wolf -> EntityType.WOLF
        is com.anurag9000.forestrun.entities.animals.Hedgehog -> EntityType.HEDGEHOG
        is com.anurag9000.forestrun.entities.animals.Dog -> EntityType.DOG
        else -> error("Unexpected showcase entity ${entity.javaClass.name}")
    }

    private fun booleanField(instance: Any, name: String): Boolean =
        instance.javaClass.getDeclaredField(name).let { field ->
            field.isAccessible = true
            field.getBoolean(instance)
        }

    private fun enumField(instance: Any, name: String): String =
        instance.javaClass.getDeclaredField(name).let { field ->
            field.isAccessible = true
            requireNotNull(field.get(instance)).toString()
        }

    private fun clearPresentationState() {
        ParticleManager.clear()
        DialogueBubbleManager.clear()
        FlavorTextManager.clear()
    }
}
