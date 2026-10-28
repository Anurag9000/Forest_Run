package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EncounterOutcome
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.animals.Cat
import com.anurag9000.forestrun.entities.animals.Dog
import com.anurag9000.forestrun.entities.animals.Fox
import com.anurag9000.forestrun.entities.animals.Hedgehog
import com.anurag9000.forestrun.systems.ParticleManager
import com.anurag9000.forestrun.ui.DialogueBubbleManager
import com.anurag9000.forestrun.ui.FlavorTextManager
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
class NamedAnimalScenarioIntegrationTest {
    private lateinit var context: Context
    private lateinit var sprites: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
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
    fun `Cat kindness scenario resolves Cat with its kindness reward`() {
        val run = play(EncounterScenario.CAT_KINDNESS)
        val cats = run.entities.filterIsInstance<Cat>()

        assertEquals(2, cats.size)
        assertTrue("authored Cat encounter never resolved", cats.any {
            it.encounterOutcome == EncounterOutcome.CLEAN_PASS
        })
        assertTrue("Cat kindness reward did not produce Seeds", run.state.seedsThisRun > 0)
        assertEquals(0, PersistentMemoryManager.getEncounterCount(context, EntityType.CAT))
    }

    @Test
    fun `Fox mirror scenario makes Fox react to the scripted Player jumps`() {
        val run = play(EncounterScenario.FOX_MIRROR)
        val foxes = run.entities.filterIsInstance<Fox>()

        assertEquals(2, foxes.size)
        assertTrue("no authored Fox mirrored a Player jump", foxes.any {
            privateField(it, "hasJumped") == true
        })
        assertEquals(0, PersistentMemoryManager.getEncounterCount(context, EntityType.FOX))
    }

    @Test
    fun `Hedgehog scenario produces the nonlethal stumble debuff`() {
        val run = play(EncounterScenario.HEDGEHOG_DEBUFF)
        val hedgehogs = run.entities.filterIsInstance<Hedgehog>()

        assertEquals(2, hedgehogs.size)
        assertTrue("authored Hedgehog did not resolve as a stumble", hedgehogs.any {
            it.encounterOutcome == EncounterOutcome.STUMBLE
        })
        assertTrue("Hedgehog scenario never applied its speed debuff", run.sawHedgehogDebuff)
        assertEquals(0, run.state.hitsThisRun)
        assertEquals(0, PersistentMemoryManager.getEncounterCount(context, EntityType.HEDGEHOG))
    }

    @Test
    fun `Dog hazard scenario emits bark projectiles from hazard variant`() {
        val run = play(EncounterScenario.DOG_HAZARD)
        val dogs = run.entities.filterIsInstance<Dog>()

        assertEquals(2, dogs.size)
        assertTrue("authored hazard Dog never emitted its bark attack", run.sawHazardProjectile)
        assertEquals(0, PersistentMemoryManager.getEncounterCount(context, EntityType.DOG))
    }

    @Test
    fun `Dog buddy scenario is harmless and resolves without a collision`() {
        val run = play(EncounterScenario.DOG_BUDDY)
        val buddy = run.entities.filterIsInstance<Dog>().single()

        assertTrue(run.sawBuddyMode)
        assertTrue(privateField(buddy, "mode").toString() in setOf("BUDDY", "BUDDY_DASH"))
        assertEquals(
            com.anurag9000.forestrun.entities.CollisionResult.NONE,
            buddy.onCollision(run.player, run.state)
        )
        assertTrue(
            "buddy encounter should resolve by passing, not physical contact",
            buddy.encounterOutcome == EncounterOutcome.CLEAN_PASS || !buddy.isActive
        )
        assertEquals(0, run.state.hitsThisRun)
        assertEquals(0, PersistentMemoryManager.getEncounterCount(context, EntityType.DOG))
    }

    private data class ScenarioRun(
        val state: GameStateManager,
        val player: Player,
        val entities: List<Entity>,
        val sawHedgehogDebuff: Boolean,
        val sawHazardProjectile: Boolean,
        val sawBuddyMode: Boolean
    )

    private fun play(scenario: EncounterScenario): ScenarioRun {
        val state = GameStateManager(context) { false }
        state.resetRun()
        scenario.initializeDebugRunState(state)
        val player = Player(1_920, 1_080, sprites)
        val manager = EntityManager(context, 1_920f, 1_080f, sprites)
        val director = EncounterDirector().apply {
            selectScenario(scenario)
            startSelectedScenario()
        }
        val script = DebugScenarioScript().apply { prepare(scenario) }
        val observed = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<Entity, Boolean>()
        )
        var sawHedgehogDebuff = false
        var sawHazardProjectile = false
        var sawBuddyMode = false
        val delta = 1f / 60f

        repeat(18 * 60) {
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
            manager.update(
                deltaTime = delta,
                gameState = state,
                player = player,
                encounterDirector = director,
                runMode = RunMode.DEBUG_SCENARIO
            )
            observed.addAll(manager.activeEntities)
            sawHedgehogDebuff = sawHedgehogDebuff ||
                (manager.activeEntities.any { it is Hedgehog } && state.speedDebuffMultiplier < 1f)
            manager.activeEntities.filterIsInstance<Dog>().forEach { dog ->
                val mode = privateField(dog, "mode").toString()
                sawBuddyMode = sawBuddyMode || mode == "BUDDY"
                if (mode == "HAZARD" && (privateField(dog, "projectiles") as List<*>).isNotEmpty()) {
                    sawHazardProjectile = true
                }
            }
            manager.checkCollisions(player, state)
        }

        assertTrue("scenario did not dispatch its complete authored step list", director.remainingSteps == 0)
        return ScenarioRun(
            state,
            player,
            observed.toList(),
            sawHedgehogDebuff,
            sawHazardProjectile,
            sawBuddyMode
        )
    }

    private fun privateField(instance: Any, name: String): Any? =
        instance.javaClass.getDeclaredField(name).let { field ->
            field.isAccessible = true
            field.get(instance)
        }
}
