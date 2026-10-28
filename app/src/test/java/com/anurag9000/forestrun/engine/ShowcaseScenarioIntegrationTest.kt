package com.anurag9000.forestrun.engine

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EncounterOutcome
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.PlayerState
import com.anurag9000.forestrun.entities.animals.Cat
import com.anurag9000.forestrun.entities.birds.Duck
import com.anurag9000.forestrun.entities.flora.Cactus
import com.anurag9000.forestrun.entities.flora.LilyOfValley
import com.anurag9000.forestrun.entities.trees.WeepingWillow
import com.anurag9000.forestrun.systems.GhostFrame
import com.anurag9000.forestrun.systems.GhostPlayer
import com.anurag9000.forestrun.systems.ParticleManager
import com.anurag9000.forestrun.ui.DialogueBubbleManager
import com.anurag9000.forestrun.ui.FlavorTextManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.IdentityHashMap
import kotlin.math.hypot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShowcaseScenarioIntegrationTest {
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
    fun `Bloom showcase converts each authored encounter once and awards only conversion rewards`() {
        val scenario = EncounterScenario.BLOOM_SHOWCASE
        val run = createRun(scenario)
        val encountered = java.util.Collections.newSetFromMap(
            IdentityHashMap<Entity, Boolean>()
        )
        val delta = 1f / 60f

        repeat(8 * 60) {
            run.state.update(delta)
            run.player.update(delta, run.state.scrollSpeed)
            run.manager.update(
                deltaTime = delta,
                gameState = run.state,
                player = run.player,
                encounterDirector = run.director,
                runMode = RunMode.DEBUG_SCENARIO
            )
            encountered.addAll(run.manager.activeEntities)
            run.manager.checkCollisions(run.player, run.state)
            encountered.addAll(run.manager.activeEntities)
        }

        val expectedTypes = listOf(
            Cactus::class.java,
            LilyOfValley::class.java,
            Cat::class.java,
            Duck::class.java,
            WeepingWillow::class.java
        )
        assertEquals("all authored steps should have dispatched", 0, run.director.remainingSteps)
        assertEquals(expectedTypes.size, encountered.size)
        encountered.forEach { entity ->
            assertEquals(
                "${entity.javaClass.simpleName} must resolve as a Bloom conversion in the showcase",
                EncounterOutcome.BLOOM_CONVERTED,
                entity.encounterOutcome
            )
        }
        expectedTypes.forEach { type ->
            assertEquals("missing Bloom conversion for ${type.simpleName}", 1,
                encountered.count { type.isInstance(it) })
        }
        assertEquals(expectedTypes.size, run.state.bloomConversionsThisRun)
        assertEquals("each conversion awards one Seed", expectedTypes.size, run.state.seedsThisRun)
        assertEquals(0, run.state.cleanPassesThisRun)
        assertEquals(0, run.state.hitsThisRun)
        assertEquals(0, run.state.sparedThisRun)
        assertTrue("the authored Bloom window should expire after the lane", !run.state.isBloomActive)
        assertEquals(0, PersistentMemoryManager.getEncounterCount(context, EntityType.CACTUS))
        assertEquals(0, PersistentMemoryManager.getEncounterCount(context, EntityType.CAT))
    }

    @Test
    fun `Ghost readability scenario permits and advances the production ghost player`() {
        val scenario = EncounterScenario.GHOST_READABILITY
        val run = createRun(scenario)
        val ghost = GhostPlayer()
        ghost.load(
            listOf(
                GhostFrame(0.00f, 520f, 860f, PlayerState.RUNNING.ordinal, 1f, 1f),
                GhostFrame(0.35f, 560f, 780f, PlayerState.JUMPING.ordinal, 0.96f, 1.04f),
                GhostFrame(0.72f, 610f, 710f, PlayerState.APEX.ordinal, 0.92f, 1.08f),
                GhostFrame(1.05f, 660f, 790f, PlayerState.FALLING.ordinal, 1f, 1f),
                GhostFrame(1.42f, 720f, 860f, PlayerState.RUNNING.ordinal, 1f, 1f),
                GhostFrame(1.80f, 780f, 790f, PlayerState.JUMPING.ordinal, 0.96f, 1.04f),
                GhostFrame(2.15f, 840f, 860f, PlayerState.RUNNING.ordinal, 1f, 1f)
            ),
            revealImmediately = true
        )

        assertTrue("GameView routes deterministic playback only for scenarios that opt in",
            scenario.allowGhostPlayback)
        assertTrue(ghost.hasGhost)

        val delta = 1f / 60f
        var observedScenarioEntity = false
        repeat(2 * 60) {
            run.state.update(delta)
            run.player.update(delta, run.state.scrollSpeed)
            run.manager.update(
                deltaTime = delta,
                gameState = run.state,
                player = run.player,
                encounterDirector = run.director,
                runMode = RunMode.DEBUG_SCENARIO
            )
            observedScenarioEntity = observedScenarioEntity || run.manager.activeEntities.isNotEmpty()
            ghost.update(delta, visibilityContext(run.player, run.manager.activeEntities))
            run.manager.checkCollisions(run.player, run.state)
        }

        assertTrue("the authored scenario should exercise its real encounter path", observedScenarioEntity)
        assertTrue("ghost playback should advance beyond its initial frame", ghost.elapsedForTest > 0f)
        assertTrue("the opt-in ghost should become visible in its readable lane", ghost.visibilityAlphaForTest > 0f)
        assertTrue(ghost.hasGhost)
    }

    @Test
    fun `Rest Loop forced terminal Cactus resolves before authored Wolf step`() {
        val scenario = EncounterScenario.REST_LOOP
        val run = createRun(scenario)

        // Mirrors GameView.prepareEncounterScenario: the forced Cactus is
        // admitted before simulation, while authored steps remain on the
        // Director clock. GameView dispatches the returned terminal collision
        // and returns from its frame before another Director update.
        run.manager.debugSpawnAt(EntityType.CACTUS, run.player.x + 14f)
        val forcedCactus = run.manager.activeEntities.filterIsInstance<Cactus>().single()
        val delta = 1f / 60f
        run.state.update(delta)
        run.player.update(delta, run.state.scrollSpeed)
        run.manager.update(
            deltaTime = delta,
            gameState = run.state,
            player = run.player,
            encounterDirector = run.director,
            runMode = RunMode.DEBUG_SCENARIO
        )
        val collision = run.manager.checkCollisions(run.player, run.state)

        assertEquals("forced startup hit is terminal before the first authored step",
            EntityType.CACTUS, collision?.let { run.manager.entityTypeOf(it.entity) })
        assertEquals(EncounterOutcome.HIT, forcedCactus.encounterOutcome)
        assertEquals("both authored entries remain undispatched after first-frame termination",
            2, run.director.remainingSteps)
        assertFalse(run.manager.activeEntities.any { it is com.anurag9000.forestrun.entities.animals.Wolf })
        assertEquals(0, run.state.bloomConversionsThisRun)
    }

    private data class Run(
        val state: GameStateManager,
        val player: Player,
        val manager: EntityManager,
        val director: EncounterDirector
    )

    private fun createRun(scenario: EncounterScenario): Run {
        val state = GameStateManager(context) { false }
        state.resetRun()
        scenario.initializeDebugRunState(state)
        val player = Player(1_920, 1_080, sprites)
        val manager = EntityManager(context, 1_920f, 1_080f, sprites)
        manager.biomeManager.forceDebugBiome(scenario.forcedBiome)
        val director = EncounterDirector().apply {
            selectScenario(scenario)
            startSelectedScenario()
        }
        if (scenario.startsWithBloom) state.debugActivateBloom()
        return Run(state, player, manager, director)
    }

    private fun visibilityContext(player: Player, entities: List<Entity>): GhostPlayer.VisibilityContext {
        val live = player.hitbox
        val focus = RectF(
            live.left - Player.BASE_WIDTH * 1.4f,
            live.top - Player.BASE_HEIGHT * 0.9f,
            live.right + Player.BASE_WIDTH * 4.8f,
            live.bottom + Player.BASE_HEIGHT * 0.9f
        )
        var count = 0
        var nearest = Float.POSITIVE_INFINITY
        entities.forEach { entity ->
            if (!entity.isActive || entity.hitbox.isEmpty || !RectF.intersects(focus, entity.hitbox)) {
                return@forEach
            }
            count++
            val dx = when {
                live.right < entity.hitbox.left -> entity.hitbox.left - live.right
                entity.hitbox.right < live.left -> live.left - entity.hitbox.right
                else -> 0f
            }
            val dy = when {
                live.bottom < entity.hitbox.top -> entity.hitbox.top - live.bottom
                entity.hitbox.bottom < live.top -> live.top - entity.hitbox.bottom
                else -> 0f
            }
            nearest = minOf(nearest, hypot(dx.toDouble(), dy.toDouble()).toFloat())
        }
        return GhostPlayer.VisibilityContext(
            livePlayerX = player.x,
            livePlayerY = player.y,
            livePlayerWidth = player.currentWidth,
            livePlayerHeight = player.currentHeight,
            nearbyHazardCount = count,
            nearestHazardDistancePx = nearest
        )
    }
}
