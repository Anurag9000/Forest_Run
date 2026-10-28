package com.anurag9000.forestrun.engine

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.flora.Cactus
import com.anurag9000.forestrun.entities.flora.Eucalyptus
import com.anurag9000.forestrun.entities.flora.Hyacinth
import com.anurag9000.forestrun.entities.flora.LilyOfValley
import com.anurag9000.forestrun.entities.flora.VanillaOrchid
import com.anurag9000.forestrun.entities.trees.Bamboo
import com.anurag9000.forestrun.entities.trees.BambooGapPlacement
import com.anurag9000.forestrun.entities.trees.CherryBlossom
import com.anurag9000.forestrun.entities.trees.Jacaranda
import com.anurag9000.forestrun.entities.trees.WeepingWillow
import com.anurag9000.forestrun.systems.ParticleManager
import com.anurag9000.forestrun.ui.DialogueBubbleManager
import com.anurag9000.forestrun.ui.FlavorTextManager
import java.util.IdentityHashMap
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NamedFloraTreeScenarioIntegrationTest {
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
        ParticleManager.clear()
        DialogueBubbleManager.clear()
        FlavorTextManager.clear()
    }

    @After
    fun tearDown() {
        ParticleManager.clear()
        DialogueBubbleManager.clear()
        FlavorTextManager.clear()
    }

    @Test
    fun `flora and tree showcases dispatch their exact ordered real entities`() {
        assertScenarioSpawnOrder(
            EncounterScenario.FLORA_SHOWCASE,
            listOf(
                EntityType.CACTUS,
                EntityType.LILY_OF_VALLEY,
                EntityType.HYACINTH,
                EntityType.EUCALYPTUS,
                EntityType.VANILLA_ORCHID
            )
        )
        assertScenarioSpawnOrder(
            EncounterScenario.TREE_SHOWCASE,
            listOf(
                EntityType.WEEPING_WILLOW,
                EntityType.JACARANDA,
                EntityType.BAMBOO,
                EntityType.CHERRY_BLOSSOM
            )
        )
    }

    @Test
    fun `cactus read scenario actually sends its authored jumps through both cactus encounters`() {
        val run = runScenario(EncounterScenario.CACTUS_READ, untilSeconds = 9f)
        val cacti = run.spawned.filterIsInstance<Cactus>()

        assertEquals(2, cacti.size)
        assertTrue(DeterministicScenarioReplayContract.matches(run.script.traceSnapshot()))
        assertEquals(4, run.script.traceSnapshot().events.size)
        assertTrue("Cactus warning cue never advanced", cacti.all { floatField(it, "warningPulse") > 0f })
        assertEquals(
            "Authored Cactus actions did not produce two clean passes; hits=${run.gameState.hitsThisRun}, " +
                "passes=${run.gameState.cleanPassesThisRun}, outcomes=${cacti.map { it.encounterOutcome }}",
            2,
            run.gameState.cleanPassesThisRun
        )
        assertEquals(0, run.gameState.hitsThisRun)
    }

    @Test
    fun `orchid window scenario creates two actual free threads that admit a player body`() {
        val run = runScenario(EncounterScenario.ORCHID_WINDOW, untilSeconds = 2f)
        val orchids = run.spawned.filterIsInstance<VanillaOrchid>()
        val gameState = run.gameState

        assertEquals(2, orchids.size)
        orchids.forEach { orchid ->
            val thread = rectField(orchid, "threadRect")
            assertFalse("Orchid safe thread is empty", thread.isEmpty)
            val bodyWidth = run.player.hitbox.width()
            val bodyHeight = run.player.hitbox.height()
            assertTrue("Thread cannot admit the live Player width", thread.width() >= bodyWidth)
            assertTrue("Thread cannot admit the live Player height", thread.height() >= bodyHeight)

            val centerX = thread.centerX()
            val centerY = thread.centerY()
            run.player.hitbox.set(
                centerX - bodyWidth / 2f,
                centerY - bodyHeight / 2f,
                centerX + bodyWidth / 2f,
                centerY + bodyHeight / 2f
            )
            assertEquals(CollisionResult.NONE, orchid.onCollision(run.player, gameState))
        }
    }

    @Test
    fun `bamboo gap scenario creates a reachable featured passage in both real encounters`() {
        val run = runScenario(EncounterScenario.BAMBOO_GAP, untilSeconds = 3f)
        val bamboos = run.spawned.filterIsInstance<Bamboo>()

        assertEquals(2, bamboos.size)
        val crossingPlayer = Player(1_920, 1_080, sprites)
        bamboos.forEach { bamboo ->
            @Suppress("UNCHECKED_CAST")
            val gaps = field(bamboo, "gapRects") as Array<RectF>
            val featured = gaps[1]
            assertFalse("Featured Bamboo opening is empty", featured.isEmpty)
            assertTrue("Featured gap cannot admit Player width", featured.width() >= crossingPlayer.hitbox.width())
            assertTrue("Featured gap cannot admit Player height", featured.height() >= crossingPlayer.hitbox.height())

            val reachable = BambooGapPlacement.reachableCentreRange(
                groundY = bamboo.hitbox.bottom,
                gapHeight = featured.height()
            )
            assertTrue("Scenario generated an unreachable vertical gap", featured.centerY() in reachable)

            val centerX = featured.centerX()
            val centerY = featured.centerY()
            val width = crossingPlayer.hitbox.width()
            val height = crossingPlayer.hitbox.height()
            crossingPlayer.hitbox.set(
                centerX - width / 2f,
                centerY - height / 2f,
                centerX + width / 2f,
                centerY + height / 2f
            )
            assertEquals(CollisionResult.NONE, bamboo.onCollision(crossingPlayer, run.gameState))
        }
    }

    @Test
    fun `focused flora and tree scenarios advance their named live presentation geometry`() {
        val lily = runScenario(EncounterScenario.LILY_GLOW, 0.35f).spawned.filterIsInstance<LilyOfValley>().single()
        assertTrue(floatField(lily, "glowPulse") > 0f)
        assertTrue(floatField(lily, "trapPulse") > 0f)

        val hyacinth = runScenario(EncounterScenario.HYACINTH_BRUSH, 0.35f).spawned
            .filterIsInstance<Hyacinth>().single()
        assertFalse(rectField(hyacinth, "brushBox").isEmpty)

        val eucalyptus = runScenario(EncounterScenario.EUCALYPTUS_WHIP, 0.35f).spawned
            .filterIsInstance<Eucalyptus>().single()
        assertTrue(floatField(eucalyptus, "gustPulse") > 0f)
        assertFalse(rectField(eucalyptus, "guideRect").isEmpty)

        val willow = runScenario(EncounterScenario.WILLOW_CURTAIN, 0.40f).spawned
            .filterIsInstance<WeepingWillow>().single()
        assertFalse(rectField(willow, "duckLaneRect").isEmpty)

        val jacaranda = runScenario(EncounterScenario.JACARANDA_PETALS, 0.40f).spawned
            .filterIsInstance<Jacaranda>().single()
        assertFalse(rectField(jacaranda, "canopyBloomRect").isEmpty)
        assertTrue(floatField(jacaranda, "canopyPulse") > 0f)

        val cherry = runScenario(EncounterScenario.CHERRY_GUST, 0.40f).spawned
            .filterIsInstance<CherryBlossom>().single()
        assertFalse(rectField(cherry, "stormVeilRect").isEmpty)
        assertTrue(floatField(cherry, "gustPulse") > 0f)
    }

    private fun assertScenarioSpawnOrder(
        scenario: EncounterScenario,
        expected: List<EntityType>
    ) {
        val run = runScenario(scenario, untilSeconds = scenario.steps.last().atSeconds + 0.1f)
        assertEquals(expected, run.spawned.map(::entityType))
        assertTrue(run.spawned.all { !it.shouldRecordPersistence })
        assertEquals(0, PersistentMemoryManager.getEncounterCount(context, expected.first()))
    }

    private data class ScenarioRun(
        val spawned: List<Entity>,
        val gameState: GameStateManager,
        val player: Player,
        val script: DebugScenarioScript
    )

    private fun runScenario(scenario: EncounterScenario, untilSeconds: Float): ScenarioRun {
        val gameState = GameStateManager(context) { false }
        gameState.resetRun()
        scenario.initializeDebugRunState(gameState)
        val player = Player(1_920, 1_080, sprites)
        val entities = EntityManager(context, 1_920f, 1_080f, sprites)
        val director = EncounterDirector().apply {
            selectScenario(scenario)
            startSelectedScenario()
        }
        val script = DebugScenarioScript().apply { prepare(scenario) }
        val seen = IdentityHashMap<Entity, Boolean>()
        val spawned = mutableListOf<Entity>()
        val delta = 1f / 60f
        val frames = (untilSeconds / delta).toInt()

        repeat(frames) {
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
            entities.activeEntities.forEach { entity ->
                if (seen.put(entity, true) == null) spawned.add(entity)
            }
            entities.checkCollisions(player, gameState)
        }
        return ScenarioRun(spawned, gameState, player, script)
    }

    private fun entityType(entity: Entity): EntityType = when (entity) {
        is Cactus -> EntityType.CACTUS
        is LilyOfValley -> EntityType.LILY_OF_VALLEY
        is Hyacinth -> EntityType.HYACINTH
        is Eucalyptus -> EntityType.EUCALYPTUS
        is VanillaOrchid -> EntityType.VANILLA_ORCHID
        is WeepingWillow -> EntityType.WEEPING_WILLOW
        is Jacaranda -> EntityType.JACARANDA
        is Bamboo -> EntityType.BAMBOO
        is CherryBlossom -> EntityType.CHERRY_BLOSSOM
        else -> error("Unexpected entity in flora/tree showcase: ${entity.javaClass.name}")
    }

    private fun field(instance: Any, name: String) = instance.javaClass.getDeclaredField(name).apply {
        isAccessible = true
    }.get(instance)

    private fun floatField(instance: Any, name: String): Float =
        instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.getFloat(instance)

    private fun rectField(instance: Any, name: String): RectF =
        requireNotNull(field(instance, name) as? RectF)
}
