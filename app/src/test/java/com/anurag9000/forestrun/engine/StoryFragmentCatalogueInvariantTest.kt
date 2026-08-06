package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StoryFragmentCatalogueInvariantTest {
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
    fun `every entity exposes stable readable thought copy and a safe memory identity`() {
        EntityType.entries.forEach { type ->
            repeat(5) { PersistentMemoryManager.recordEncounter(context, type) }
            repeat(3) { PersistentMemoryManager.recordPass(context, type) }
            if (RelationshipArcSystem.isTracked(type)) {
                repeat(3) { PersistentMemoryManager.recordSpare(context, type) }
                RelationshipArcSystem.refreshStage(context, type)
            }

            val first = StoryFragmentSystem.creatureThought(context, type)
            val second = StoryFragmentSystem.creatureThought(context, type)

            assertNotNull("expected authored creature thought for $type", first)
            assertEquals("creature thought must be deterministic for $type", first, second)
            assertSafeText(requireNotNull(first))
        }

        val pageIds = StoryFragmentSystem.unlockedMemoryPages(context)
        EntityType.entries.forEach { type ->
            assertTrue(
                "missing creature memory page for $type",
                pageIds.contains("page_thought_${type.name.lowercase()}")
            )
        }
        assertSafePageIds(pageIds)
    }

    @Test
    fun `rest garden and weather paths publish only safe persistent page identities`() {
        repeat(5) { PersistentMemoryManager.recordBiomeFriendship(context, Biome.NIGHT_FOREST) }
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.OWL) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.OWL) }
        repeat(3) { PersistentMemoryManager.recordPass(context, EntityType.OWL) }
        RelationshipArcSystem.refreshStage(context, EntityType.OWL)
        val summary = richSummary(
            lastKiller = EntityType.OWL,
            forestMood = ForestMood.GENTLE,
            pacifistRouteTier = PacifistRouteTier.PEACEFUL
        )

        Biome.entries.forEach { biome ->
            assertSafeText(
                StoryFragmentSystem.restQuote(context, summary, biome, summary.lastKiller)
            )
        }
        assertSafeText(requireNotNull(StoryFragmentSystem.gardenReflection(context, summary)))
        assertSafeText(StoryFragmentSystem.weatherThought(context, summary))

        val pageIds = StoryFragmentSystem.unlockedMemoryPages(context)
        assertTrue(pageIds.isNotEmpty())
        assertSafePageIds(pageIds)
        assertEquals(pageIds.size, StoryFragmentSystem.memoryPageCount(context))
    }

    @Test
    fun `empty and strained histories remain readable without unsafe page identifiers`() {
        assertSafeText(StoryFragmentSystem.weatherThought(context, null))

        repeat(6) { PersistentMemoryManager.recordEncounter(context, EntityType.WOLF) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.WOLF) }
        repeat(5) { PersistentMemoryManager.recordHit(context, EntityType.WOLF) }
        RelationshipArcSystem.refreshStage(context, EntityType.WOLF)
        val summary = richSummary(
            lastKiller = EntityType.WOLF,
            forestMood = ForestMood.FEARFUL,
            pacifistRouteTier = PacifistRouteTier.NONE
        )

        assertSafeText(requireNotNull(StoryFragmentSystem.creatureThought(context, EntityType.WOLF)))
        assertSafeText(requireNotNull(StoryFragmentSystem.gardenReflection(context, summary)))
        assertSafeText(StoryFragmentSystem.weatherThought(context, summary))
        assertSafePageIds(StoryFragmentSystem.unlockedMemoryPages(context))
    }

    private fun assertSafePageIds(pageIds: Set<String>) {
        assertEquals(pageIds.size, pageIds.toSet().size)
        pageIds.forEach { pageId ->
            assertTrue(pageId.matches(Regex("[a-z0-9_]+")))
            assertTrue(pageId.length in 1..128)
            assertEquals(pageId.trim(), pageId)
        }
    }

    private fun assertSafeText(text: String) {
        assertTrue(text.isNotBlank())
        assertEquals(text.trim(), text)
        assertTrue(text.length in 1..512)
        assertTrue(text.none { character -> character.code < 32 && character != '\n' })
    }

    private fun richSummary(
        lastKiller: EntityType?,
        forestMood: ForestMood,
        pacifistRouteTier: PacifistRouteTier
    ): RunSummary = RunSummary(
        score = 12_345,
        distanceM = 2_500f,
        isNewHighScore = true,
        highScore = 12_345,
        mercyHearts = 7,
        mercyMisses = 8,
        kindnessChain = 6,
        cleanPasses = 12,
        sparedCount = 8,
        hitsTaken = 3,
        seedsCollected = 24,
        bloomConversions = 5,
        lastKiller = lastKiller,
        restQuote = "The forest kept the whole story.",
        forestMood = forestMood,
        pacifistRouteTier = pacifistRouteTier
    )
}
