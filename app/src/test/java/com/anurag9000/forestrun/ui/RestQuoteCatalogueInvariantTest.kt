package com.anurag9000.forestrun.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.Biome
import com.anurag9000.forestrun.engine.ForestMood
import com.anurag9000.forestrun.engine.PacifistRouteTier
import com.anurag9000.forestrun.engine.RunSummary
import com.anurag9000.forestrun.engine.SaveManager
import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RestQuoteCatalogueInvariantTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `every contextual rest quote combination is deterministic safe and readable`() {
        val killers = listOf<EntityType?>(null) + EntityType.entries
        val profiles = listOf(false, true)
        var evaluated = 0

        killers.forEach { killer ->
            ForestMood.entries.forEach { mood ->
                PacifistRouteTier.entries.forEach { route ->
                    Biome.entries.forEach { biome ->
                        profiles.forEach { richRun ->
                            val summary = summary(
                                lastKiller = killer,
                                forestMood = mood,
                                route = route,
                                richRun = richRun
                            )

                            val first = RestQuoteManager.quoteFor(context, summary, biome, killer)
                            val second = RestQuoteManager.quoteFor(context, summary, biome, killer)

                            assertEquals(first, second)
                            assertTrue(first.isNotBlank())
                            assertEquals(first.trim(), first)
                            assertTrue(first.length in 1..512)
                            assertTrue(first.none { character ->
                                character.code < 32 && character != '\n'
                            })
                            evaluated++
                        }
                    }
                }
            }
        }

        assertEquals(
            killers.size *
                ForestMood.entries.size *
                PacifistRouteTier.entries.size *
                Biome.entries.size *
                profiles.size,
            evaluated
        )
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
