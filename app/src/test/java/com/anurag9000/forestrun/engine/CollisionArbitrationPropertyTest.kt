package com.anurag9000.forestrun.engine

import android.content.Context
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.EncounterOutcome
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

            val frame = assertNotNull(manager.checkCollisions(player, gameState))
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

            val frame = assertNotNull(manager.checkCollisions(player, gameState))
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
        val collisionResult: CollisionResult
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
