package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.EntityType
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncounterSelectionBagTest {

    @Test
    fun `every cycle exhausts the authorized pool before any repeat`() {
        val pool = Biome.ORCHARD.preferredPool

        repeat(64) { seed ->
            val bag = EncounterSelectionBag(Random(seed))
            repeat(12) {
                val cycle = List(pool.size) { bag.next(pool) }
                assertEquals(pool.toSet(), cycle.toSet())
                assertEquals(pool.size, cycle.distinct().size)
            }
        }
    }

    @Test
    fun `multi member pools never repeat across adjacent selections or refill boundaries`() {
        Biome.entries.forEach { biome ->
            val pool = biome.preferredPool
            assertTrue(pool.size > 1)

            repeat(32) { seed ->
                val bag = EncounterSelectionBag(Random(seed))
                val sequence = List(pool.size * 20) { bag.next(pool) }
                sequence.zipWithNext().forEach { (first, second) ->
                    assertFalse("${biome.name} seed=$seed repeated $first", first == second)
                }
            }
        }
    }

    @Test
    fun `pool transition cannot leak an entity that is no longer authorized`() {
        val bag = EncounterSelectionBag(Random(7))
        val firstPool = listOf(EntityType.CACTUS, EntityType.CAT, EntityType.DUCK)
        val secondPool = listOf(EntityType.OWL, EntityType.EAGLE)

        repeat(2) { bag.next(firstPool) }
        val transitioned = List(20) { bag.next(secondPool) }

        assertTrue(transitioned.all { it in secondPool })
        assertTrue(transitioned.none { it in firstPool })
    }

    @Test
    fun `duplicate pool entries cannot create hidden weighting or starvation`() {
        val bag = EncounterSelectionBag(Random(11))
        val weightedLooking = listOf(
            EntityType.CAT,
            EntityType.CAT,
            EntityType.FOX,
            EntityType.FOX,
            EntityType.DOG
        )

        repeat(10) {
            val cycle = List(3) { bag.next(weightedLooking) }
            assertEquals(setOf(EntityType.CAT, EntityType.FOX, EntityType.DOG), cycle.toSet())
        }
    }

    @Test
    fun `empty pool fails closed and reset starts a fresh fairness history`() {
        val bag = EncounterSelectionBag(Random(3))
        assertNull(bag.next(emptyList()))

        val pool = listOf(EntityType.CAT, EntityType.FOX)
        val beforeReset = bag.next(pool)
        bag.reset()
        val afterReset = bag.next(pool)

        assertTrue(beforeReset in pool)
        assertTrue(afterReset in pool)
    }
}
