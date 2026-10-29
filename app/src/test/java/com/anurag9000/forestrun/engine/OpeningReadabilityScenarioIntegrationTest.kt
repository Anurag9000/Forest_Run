package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.animals.Cat
import com.anurag9000.forestrun.entities.birds.Duck
import com.anurag9000.forestrun.entities.birds.TitGroup
import com.anurag9000.forestrun.entities.flora.LilyOfValley
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
class OpeningReadabilityScenarioIntegrationTest {
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
    }

    @After
    fun tearDown() {
        com.anurag9000.forestrun.systems.ParticleManager.clear()
        com.anurag9000.forestrun.ui.DialogueBubbleManager.clear()
        com.anurag9000.forestrun.ui.FlavorTextManager.clear()
    }

    @Test
    fun `opening readability sends its authored roster through real deterministic spawn path`() {
        val scenario = EncounterScenario.OPENING_READABILITY
        val state = GameStateManager(context) { false }
        state.resetRun()
        val player = Player(1_920, 1_080, sprites)
        val manager = EntityManager(context, 1_920f, 1_080f, sprites)
        val director = EncounterDirector().apply {
            selectScenario(scenario)
            startSelectedScenario()
        }
        val observed = java.util.Collections.newSetFromMap(
            IdentityHashMap<Entity, Boolean>()
        )
        val spawned = mutableListOf<Entity>()
        val delta = 1f / 60f

        repeat(4 * 60) {
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
                if (observed.add(entity)) spawned += entity
            }
        }

        assertEquals(0, director.remainingSteps)
        assertEquals(
            listOf(EntityType.DUCK, EntityType.LILY_OF_VALLEY, EntityType.CAT, EntityType.TIT),
            spawned.map(::entityType)
        )
        assertTrue("Opening encounters must remain isolated from normal progression",
            spawned.all { !it.shouldRecordPersistence })
        listOf(EntityType.DUCK, EntityType.LILY_OF_VALLEY, EntityType.CAT, EntityType.TIT)
            .forEach { type ->
                assertEquals("Opening scenario persisted $type", 0,
                    PersistentMemoryManager.getEncounterCount(context, type))
            }
    }

    private fun entityType(entity: Entity): EntityType = when (entity) {
        is Duck -> EntityType.DUCK
        is LilyOfValley -> EntityType.LILY_OF_VALLEY
        is Cat -> EntityType.CAT
        is TitGroup -> EntityType.TIT
        else -> error("Unexpected opening entity ${entity.javaClass.name}")
    }
}
