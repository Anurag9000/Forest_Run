package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.EntityType
import kotlin.random.Random

/**
 * Bounded-random selector for ordinary encounter pools.
 *
 * Every distinct member of the currently authorized pool is emitted once
 * before any member is eligible again. Refill shuffling keeps variety while
 * preventing probabilistic starvation, and avoids a refill-boundary immediate
 * repeat whenever the pool contains an alternative.
 */
internal class EncounterSelectionBag(
    private val random: Random = Random.Default
) {
    private var activePool: List<EntityType> = emptyList()
    private val remaining = ArrayList<EntityType>()
    private var previous: EntityType? = null

    fun next(pool: List<EntityType>): EntityType? {
        val normalized = pool.distinct()
        if (normalized.isEmpty()) {
            activePool = emptyList()
            remaining.clear()
            return null
        }

        if (normalized != activePool) {
            activePool = normalized
            refill()
        } else if (remaining.isEmpty()) {
            refill()
        }

        val selected = remaining.removeAt(remaining.lastIndex)
        previous = selected
        return selected
    }

    fun reset() {
        activePool = emptyList()
        remaining.clear()
        previous = null
    }

    private fun refill() {
        remaining.clear()
        remaining.addAll(activePool)
        for (index in remaining.lastIndex downTo 1) {
            val other = random.nextInt(index + 1)
            if (other != index) {
                val value = remaining[index]
                remaining[index] = remaining[other]
                remaining[other] = value
            }
        }

        if (remaining.size > 1 && remaining.last() == previous) {
            val alternativeIndex = remaining.indexOfFirst { it != previous }
            if (alternativeIndex >= 0) {
                val value = remaining[remaining.lastIndex]
                remaining[remaining.lastIndex] = remaining[alternativeIndex]
                remaining[alternativeIndex] = value
            }
        }
    }
}
