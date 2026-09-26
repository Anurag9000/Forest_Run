package com.anurag9000.forestrun.engine

import android.content.Context
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.EncounterOutcome
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollisionArbitrationPropertyTest {
    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager
    private lateinit var player: Player

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        spriteManager = SpriteManager(context)
        player = Player(1_920, 1_080, spriteManager)
    }

    @Test
    fun `all permutations choose the same highest collision priority`() {
        assertPermutationInvariant(
            results = listOf(
                CollisionResult.NONE,
                CollisionResult.MERCY_MISS,
                CollisionResult.STUMBLE,
                CollisionResult.HIT
            ),
            expected = CollisionResult.HIT
        )
        assertPermutationInvariant(
            results = listOf(
                CollisionResult.NONE,
                CollisionResult.MERCY_MISS,
                CollisionResult.STUMBLE
            ),
            expected = CollisionResult.STUMBLE
        )
        assertPermutationInvariant(
            results = listOf(
                CollisionResult.NONE,
                CollisionResult.MERCY_MISS
            ),
            expected = CollisionResult.MERCY_MISS
        )
    }

    @Test
    fun `arbitration selects exactly one entity and leaves all losers pending`() {
        val permutations = permutationsOf(
            listOf(
                CollisionResult.NONE,
                CollisionResult.MERCY_MISS,
                CollisionResult.STUMBLE,
                CollisionResult.HIT
            )
        )
        for ((caseIndex, permutation) in permutations.withIndex()) {
            val manager = manager()
            val gameState = GameStateManager(context)
            val probes = permutation.map { ProbeEntity(context, it) }
            manager.activeEntities += probes

            val frame = requireNotNull(manager.checkCollisions(player, gameState)) {
                "case=$caseIndex permutation=$permutation should resolve a collision"
            }
            assertEquals("case=$caseIndex result", CollisionResult.HIT, frame.result)
            assertEquals(
                "case=$caseIndex selected count",
                1,
                probes.sumOf { it.selectedCount }
            )
            val selected = probes.single { it.selectedCount == 1 }
            assertEquals("case=$caseIndex selected outcome", EncounterOutcome.HIT, selected.encounterOutcome)
            probes.filter { it !== selected }.forEach { loser ->
                assertEquals(
                    "case=$caseIndex loser=${loser.collisionResult}",
                    EncounterOutcome.PENDING,
                    loser.encounterOutcome
                )
                assertEquals(0, loser.selectedCount)
            }
        }
    }


    @Test
    fun `early padded contact cannot immunize a later direct hit`() {
        val manager = manager()
        val state = GameStateManager(context)
        val probe = ProbeEntity(context, CollisionResult.MERCY_MISS)
        manager.activeEntities += probe

        assertEquals(null, manager.checkCollisions(player, state))
        assertEquals(EncounterOutcome.PENDING, probe.encounterOutcome)
        assertTrue(probe.observedMercyContact)
        assertEquals(0, state.mercyHearts)
        assertEquals(0, probe.selectedCount)

        probe.collisionResult = CollisionResult.HIT
        val resolved = requireNotNull(manager.checkCollisions(player, state))
        assertEquals(CollisionResult.HIT, resolved.result)
        assertEquals(EncounterOutcome.HIT, probe.encounterOutcome)
        assertEquals(1, probe.selectedCount)
        assertEquals(0, state.mercyHearts)
        probe.hitbox.set(0f, 600f, 20f, 700f)
        assertEquals(null, manager.checkCollisions(player, state))
        assertEquals(EncounterOutcome.HIT, probe.encounterOutcome)
    }

    @Test
    fun `early padded contact cannot immunize a later stumble`() {
        val manager = manager()
        val state = GameStateManager(context)
        val probe = ProbeEntity(context, CollisionResult.MERCY_MISS)
        manager.activeEntities += probe
        assertEquals(null, manager.checkCollisions(player, state))
        probe.collisionResult = CollisionResult.STUMBLE
        assertEquals(CollisionResult.STUMBLE, manager.checkCollisions(player, state)?.result)
        assertEquals(EncounterOutcome.STUMBLE, probe.encounterOutcome)
        assertEquals(0, state.mercyHearts)
    }

    @Test
    fun `safe passage commits exactly one mercy without a subsequent clean pass`() {
        val manager = manager()
        val state = GameStateManager(context)
        val probe = ProbeEntity(context, CollisionResult.MERCY_MISS)
        manager.activeEntities += probe
        repeat(3) {
            assertEquals(null, manager.checkCollisions(player, state))
        }
        assertEquals(EncounterOutcome.PENDING, probe.encounterOutcome)
        assertEquals(0, state.mercyHearts)

        probe.collisionResult = CollisionResult.NONE
        probe.hitbox.set(0f, 600f, 20f, 700f)
        val frame = requireNotNull(manager.checkCollisions(player, state))
        assertEquals(CollisionResult.MERCY_MISS, frame.result)
        assertEquals(EncounterOutcome.MERCY, probe.encounterOutcome)
        assertEquals(1, state.mercyHearts)
        assertEquals(1, probe.selectedCount)
        repeat(3) {
            assertEquals(null, manager.checkCollisions(player, state))
        }
        assertEquals(1, state.mercyHearts)
        assertEquals(1, probe.selectedCount)
    }

    @Test
    fun `Bloom conversion excludes provisional ordinary mercy reward`() {
        val manager = manager()
        val state = GameStateManager(context)
        val probe = ProbeEntity(context, CollisionResult.MERCY_MISS)
        manager.activeEntities += probe
        assertEquals(null, manager.checkCollisions(player, state))
        state.debugActivateBloom()
        probe.collisionResult = CollisionResult.NONE
        probe.hitbox.set(0f, 600f, 20f, 700f)
        assertEquals(null, manager.checkCollisions(player, state))
        assertEquals(EncounterOutcome.BLOOM_CONVERTED, probe.encounterOutcome)
        assertEquals(0, state.mercyHearts)
        assertEquals(0, probe.selectedCount)
    }

    @Test
    fun `simultaneous safe passages resolve each encounter exactly once`() {
        val manager = manager()
        val state = GameStateManager(context)
        val probes = List(3) { ProbeEntity(context, CollisionResult.MERCY_MISS) }
        manager.activeEntities += probes
        assertEquals(null, manager.checkCollisions(player, state))
        probes.forEach {
            it.collisionResult = CollisionResult.NONE
            it.hitbox.set(0f, 600f, 20f, 700f)
        }
        assertEquals(CollisionResult.MERCY_MISS, manager.checkCollisions(player, state)?.result)
        assertEquals(3, state.mercyHearts)
        assertTrue(probes.all { it.encounterOutcome == EncounterOutcome.MERCY })
        assertTrue(probes.all { it.selectedCount == 1 })
        assertEquals(null, manager.checkCollisions(player, state))
        assertEquals(3, state.mercyHearts)
    }

    @Test
    fun `none-only permutations never manufacture an encounter outcome`() {
        repeat(64) { caseIndex ->
            val manager = manager()
            val gameState = GameStateManager(context)
            val probes = List(1 + caseIndex % 8) { ProbeEntity(context, CollisionResult.NONE) }
            manager.activeEntities += probes

            assertEquals("case=$caseIndex", null, manager.checkCollisions(player, gameState))
            assertTrue(probes.all { it.selectedCount == 0 })
            assertTrue(probes.all { it.encounterOutcome == EncounterOutcome.PENDING })
        }
    }

    private fun assertPermutationInvariant(
        results: List<CollisionResult>,
        expected: CollisionResult
    ) {
        val permutations = permutationsOf(results)
        for ((caseIndex, permutation) in permutations.withIndex()) {
            val manager = manager()
            val gameState = GameStateManager(context)
            val probes = permutation.map { ProbeEntity(context, it) }
            manager.activeEntities += probes

            if (expected == CollisionResult.MERCY_MISS) {
                assertEquals("case=$caseIndex provisional contact", null, manager.checkCollisions(player, gameState))
                assertTrue(probes.all { it.encounterOutcome == EncounterOutcome.PENDING })
                assertEquals(0, gameState.mercyHearts)
                // Complete a genuine safe passage, not an early padded overlap.
                probes.forEach { it.hitbox.set(0f, 600f, 20f, 700f) }
            }
            val frame = requireNotNull(manager.checkCollisions(player, gameState)) {
                "case=$caseIndex permutation=$permutation should resolve a collision"
            }
            assertEquals("case=$caseIndex permutation=$permutation", expected, frame.result)
            assertEquals("case=$caseIndex exactly one selection", 1, probes.sumOf { it.selectedCount })
            assertEquals(expected, probes.single { it.selectedCount == 1 }.collisionResult)
        }
    }

    private fun <T> permutationsOf(values: List<T>): List<List<T>> {
        if (values.size <= 1) return listOf(values)
        val result = mutableListOf<List<T>>()
        for (index in values.indices) {
            val head = values[index]
            val tail = values.filterIndexed { candidateIndex, _ -> candidateIndex != index }
            for (suffix in permutationsOf(tail)) {
                result += listOf(head) + suffix
            }
        }
        return result
    }

    private fun manager(): EntityManager = EntityManager(
        context = context,
        screenWidth = 1_920f,
        screenHeight = 1_080f,
        spriteManager = spriteManager
    )

    private class ProbeEntity(
        context: Context,
        var collisionResult: CollisionResult
    ) : Entity(context) {
        var selectedCount = 0

        init {
            hitbox.set(450f, 600f, 550f, 700f)
        }

        override fun update(deltaTime: Float, scrollSpeed: Float) = Unit
        override fun draw(canvas: Canvas) = Unit

        override fun onCollision(
            player: Player,
            gameState: GameStateManager
        ): CollisionResult = collisionResult

        override fun onOutcomeSelected(
            result: CollisionResult,
            player: Player,
            gameState: GameStateManager
        ) {
            selectedCount++
        }
    }
}
