package com.yourname.forest_run.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StoryFragmentSystemTest {

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
    fun `rest fragments unlock memory pages for repeat killers`() {
        PersistentMemoryManager.recordHit(context, EntityType.WOLF)
        PersistentMemoryManager.recordHit(context, EntityType.WOLF)

        val quote = StoryFragmentSystem.restQuote(context, Biome.DUSK_CANYON, EntityType.WOLF)

        assertTrue(quote.contains("howl") || quote.contains("pattern"))
        assertTrue(StoryFragmentSystem.unlockedMemoryPages(context).contains("page_repeat_wolf"))
    }

    @Test
    fun `garden reflections unlock a page after a gentle spared run`() {
        val summary = RunSummary(
            score = 900,
            distanceM = 510f,
            isNewHighScore = false,
            highScore = 1200,
            mercyHearts = 3,
            mercyMisses = 2,
            kindnessChain = 4,
            cleanPasses = 5,
            sparedCount = 1,
            hitsTaken = 0,
            seedsCollected = 7,
            bloomConversions = 1,
            lastKiller = null,
            restQuote = "Softly.",
            forestMood = ForestMood.GENTLE
        )

        val line = StoryFragmentSystem.gardenReflection(context, summary)

        assertTrue(line!!.contains("trusted") || line.contains("breathes"))
        assertEquals(1, StoryFragmentSystem.memoryPageCount(context))
    }

    @Test
    fun `creature thoughts and weather thoughts are available for bonded saves`() {
        repeat(3) { PersistentMemoryManager.recordEncounter(context, EntityType.FOX) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.FOX) }

        val creatureThought = StoryFragmentSystem.creatureThought(context, EntityType.FOX)
        val weatherThought = StoryFragmentSystem.weatherThought(context, null)

        assertNotNull(creatureThought)
        assertTrue(creatureThought!!.contains("fox") || creatureThought.contains("path") || creatureThought.contains("answer"))
        assertTrue(weatherThought.contains("wind") || weatherThought.contains("air") || weatherThought.contains("branches"))
    }

    @Test
    fun `garden reflection unlocks caution page for repeated harm`() {
        PersistentMemoryManager.recordHit(context, EntityType.EAGLE)
        PersistentMemoryManager.recordHit(context, EntityType.EAGLE)
        val summary = RunSummary(
            score = 510,
            distanceM = 440f,
            isNewHighScore = false,
            highScore = 1200,
            mercyHearts = 0,
            mercyMisses = 0,
            kindnessChain = 0,
            cleanPasses = 2,
            sparedCount = 0,
            hitsTaken = 1,
            seedsCollected = 3,
            bloomConversions = 0,
            lastKiller = EntityType.EAGLE,
            restQuote = "Marked.",
            forestMood = ForestMood.FEARFUL
        )

        val line = StoryFragmentSystem.gardenReflection(context, summary)

        assertNotNull(line)
        assertTrue(line!!.contains("nerves") || line.contains("gentle"))
        assertTrue(StoryFragmentSystem.unlockedMemoryPages(context).contains("page_garden_caution_eagle"))
    }

    @Test
    fun `milestone gentle reflection unlocks a milestone page`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.CAT) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }
        val summary = RunSummary(
            score = 980,
            distanceM = 650f,
            isNewHighScore = false,
            highScore = 1_200,
            mercyHearts = 3,
            mercyMisses = 3,
            kindnessChain = 6,
            cleanPasses = 8,
            sparedCount = 1,
            hitsTaken = 0,
            seedsCollected = 8,
            bloomConversions = 1,
            lastKiller = null,
            restQuote = "Gentle.",
            forestMood = ForestMood.GENTLE
        )

        val line = StoryFragmentSystem.gardenReflection(context, summary)

        assertNotNull(line)
        assertTrue(line!!.contains("gentle", ignoreCase = true) || line.contains("patch"))
        assertTrue(StoryFragmentSystem.unlockedMemoryPages(context).contains("page_milestone_gentle_cat"))
    }

    @Test
    fun `weather thought deepens with milestone bond`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.WOLF) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.WOLF) }
        val summary = RunSummary(
            score = 1_100,
            distanceM = 760f,
            isNewHighScore = false,
            highScore = 1_500,
            mercyHearts = 2,
            mercyMisses = 2,
            kindnessChain = 2,
            cleanPasses = 9,
            sparedCount = 0,
            hitsTaken = 0,
            seedsCollected = 6,
            bloomConversions = 0,
            lastKiller = null,
            restQuote = "Steady.",
            forestMood = ForestMood.STEADY
        )

        val weatherThought = StoryFragmentSystem.weatherThought(context, summary)

        assertTrue(weatherThought.contains("recognizes") || weatherThought.contains("patient"))
    }
}
