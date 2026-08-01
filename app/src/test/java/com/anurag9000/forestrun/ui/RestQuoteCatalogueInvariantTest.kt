package com.anurag9000.forestrun.ui

import com.anurag9000.forestrun.engine.ForestMood
import com.anurag9000.forestrun.engine.PacifistRouteTier
import com.anurag9000.forestrun.engine.RunSummary
import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestQuoteCatalogueInvariantTest {
    @Test
    fun `every authored rest quote combination is deterministic safe and readable`() {
        val killers = listOf<EntityType?>(null) + EntityType.entries
        val profiles = listOf(false, true)
        var evaluated = 0

        killers.forEach { killer ->
            ForestMood.entries.forEach { mood ->
                PacifistRouteTier.entries.forEach { route ->
                    profiles.forEach { richRun ->
                        val summary = summary(
                            lastKiller = killer,
                            forestMood = mood,
                            route = route,
                            richRun = richRun
                        )

                        val first = RestQuoteManager.getQuote(summary)
                        val second = RestQuoteManager.getQuote(summary)

                        assertEquals(first, second)
                        assertTrue(first.isNotBlank())
                        assertEquals(first.trim(), first)
                        assertTrue(first.length in 1..240)
                        assertTrue(first.none { character ->
                            character.code < 32 && character != '\n'
                        })
                        evaluated++
                    }
                }
            }
        }

        assertEquals(killers.size * ForestMood.entries.size * PacifistRouteTier.entries.size * profiles.size, evaluated)
    }

    private fun summary(
        lastKiller: EntityType?,
        forestMood: ForestMood,
        route: PacifistRouteTier,
        richRun: Boolean
    ): RunSummary = RunSummary(
        score = if (richRun) 250_000 else 0,
        distanceM = if (richRun) 10_000f else 0f,
        isNewHighScore = richRun,
        highScore = if (richRun) 250_000 else 0,
        mercyHearts = if (richRun) 99 else 0,
        mercyMisses = if (richRun) 99 else 0,
        kindnessChain = if (richRun) 99 else 0,
        cleanPasses = if (richRun) 99 else 0,
        sparedCount = if (richRun) 99 else 0,
        hitsTaken = if (richRun) 99 else 0,
        seedsCollected = if (richRun) 999 else 0,
        bloomConversions = if (richRun) 99 else 0,
        lastKiller = lastKiller,
        restQuote = "seed quote",
        forestMood = forestMood,
        pacifistRouteTier = route
    )
}
