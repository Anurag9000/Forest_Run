package com.anurag9000.forestrun.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.Biome
import com.anurag9000.forestrun.engine.ForestMood
import com.anurag9000.forestrun.engine.PacifistRouteTier
import com.anurag9000.forestrun.engine.PersistentMemoryManager
import com.anurag9000.forestrun.engine.RunSummary
import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RestQuoteManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `repeat killer quotes acknowledge repeated deaths`() {
        PersistentMemoryManager.recordHit(context, EntityType.WOLF)
        PersistentMemoryManager.recordHit(context, EntityType.WOLF)
        val summary = RunSummary(
            score = 420,
            distanceM = 300f,
            isNewHighScore = false,
            highScore = 900,
            mercyHearts = 0,
            mercyMisses = 0,
            kindnessChain = 0,
            cleanPasses = 1,
            sparedCount = 0,
            hitsTaken = 1,
            seedsCollected = 2,
            bloomConversions = 0,
            lastKiller = EntityType.WOLF,
            restQuote = "",
            forestMood = ForestMood.FEARFUL
        )

        val quote = RestQuoteManager.quoteFor(context, summary, Biome.DUSK_CANYON, EntityType.WOLF)

        assertTrue(quote.contains("same weak moment") || quote.contains("pattern"))
    }

    @Test
    fun `biome fallback quote reflects biome mood when killer is unknown`() {
        val summary = RunSummary(
            score = 700,
            distanceM = 520f,
            isNewHighScore = false,
            highScore = 1_000,
            mercyHearts = 0,
            mercyMisses = 0,
            kindnessChain = 0,
            cleanPasses = 4,
            sparedCount = 0,
            hitsTaken = 1,
            seedsCollected = 5,
            bloomConversions = 0,
            lastKiller = null,
            restQuote = "",
            forestMood = ForestMood.STEADY
        )

        val quote = RestQuoteManager.quoteFor(context, summary, Biome.NIGHT_FOREST, null)

        assertTrue(quote.contains("Night"))
        assertTrue(
            quote.contains("room", ignoreCase = true) ||
                quote.contains("rest", ignoreCase = true) ||
                quote.contains("night", ignoreCase = true)
        )
    }

    @Test
    fun `route quote adds route coda beyond fragment base`() {
        val summary = RunSummary(
            score = 1_040,
            distanceM = 760f,
            isNewHighScore = false,
            highScore = 1_400,
            mercyHearts = 4,
            mercyMisses = 4,
            kindnessChain = 6,
            cleanPasses = 9,
            sparedCount = 2,
            hitsTaken = 0,
            seedsCollected = 8,
            bloomConversions = 1,
            lastKiller = null,
            restQuote = "",
            forestMood = ForestMood.GENTLE,
            pacifistRouteTier = PacifistRouteTier.MERCIFUL
        )

        val quote = RestQuoteManager.quoteFor(context, summary, Biome.DUSK_CANYON, null)

        assertTrue(
            quote.contains("Mercy", ignoreCase = true) ||
                quote.contains("spared", ignoreCase = true) ||
                quote.contains("Dusk Canyon", ignoreCase = true)
        )
    }

    @Test
    fun `killer quote adds mood sensitive killer coda`() {
        val summary = RunSummary(
            score = 510,
            distanceM = 430f,
            isNewHighScore = false,
            highScore = 1_100,
            mercyHearts = 0,
            mercyMisses = 0,
            kindnessChain = 0,
            cleanPasses = 2,
            sparedCount = 0,
            hitsTaken = 1,
            seedsCollected = 3,
            bloomConversions = 0,
            lastKiller = EntityType.EAGLE,
            restQuote = "",
            forestMood = ForestMood.FEARFUL
        )

        val quote = RestQuoteManager.quoteFor(context, summary, Biome.NIGHT_FOREST, EntityType.EAGLE)

        assertTrue(
            quote.contains("eagle", ignoreCase = true) ||
                quote.contains("room", ignoreCase = true) ||
                quote.contains("careful", ignoreCase = true)
        )
    }
}
