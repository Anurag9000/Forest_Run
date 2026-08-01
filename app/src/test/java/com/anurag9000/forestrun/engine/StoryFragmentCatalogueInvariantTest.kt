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
    fun `every entity weathering fragment has safe unique authored identity`() {
        EntityType.entries.forEach { type ->
            repeat(4) { PersistentMemoryManager.recordEncounter(context, type) }
            repeat(2) { PersistentMemoryManager.recordPass(context, type) }
        }

        val pages = EntityType.entries.map { type ->
            requireNotNull(StoryFragmentSystem.weatheringPage(context, type))
        }

        assertPageCatalogue(pages)
        assertEquals(EntityType.entries.size, pages.size)
    }

    @Test
    fun `rich warm history emits collision free relationship and active history catalogues`() {
        trackedRelationships().forEach { type ->
            repeat(6) { PersistentMemoryManager.recordEncounter(context, type) }
            repeat(3) { PersistentMemoryManager.recordSpare(context, type) }
            repeat(5) { PersistentMemoryManager.recordPass(context, type) }
            RelationshipArcSystem.refreshStage(context, type)
        }
        repeat(5) { PersistentMemoryManager.recordBiomeFriendship(context, Biome.NIGHT_FOREST) }
        PersistentMemoryManager.saveRunMemory(
            context,
            lastKiller = EntityType.OWL,
            forestMood = ForestMood.GENTLE,
            pacifistRouteTier = PacifistRouteTier.PEACEFUL
        )
        val summary = richSummary(
            lastKiller = EntityType.OWL,
            forestMood = ForestMood.GENTLE,
            pacifistRouteTier = PacifistRouteTier.PEACEFUL
        )

        val newlyUnlocked = StoryFragmentSystem.unlockPersistentPages(context)
        val timeline = StoryFragmentSystem.relationshipTimelinePages(context)
        val activePages = StoryFragmentSystem.activeHistoryPages(context, summary)
        val marks = StoryFragmentSystem.activeHistoryMarks(context, summary)

        assertPageCatalogue(newlyUnlocked)
        assertPageCatalogue(timeline)
        assertPageCatalogue(activePages)
        assertMarkCatalogue(marks)
        assertNotNull(StoryFragmentSystem.repeatFriendPage(context))
        assertNotNull(StoryFragmentSystem.biomeFriendshipPage(context))
    }

    @Test
    fun `strained history catalogue remains safe and distinct from empty history`() {
        val emptyPages = StoryFragmentSystem.activeHistoryPages(context, null)
        val emptyMarks = StoryFragmentSystem.activeHistoryMarks(context, null)
        assertTrue(emptyPages.isEmpty())
        assertTrue(emptyMarks.isEmpty())

        repeat(6) { PersistentMemoryManager.recordEncounter(context, EntityType.WOLF) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.WOLF) }
        repeat(5) { PersistentMemoryManager.recordHit(context, EntityType.WOLF) }
        RelationshipArcSystem.refreshStage(context, EntityType.WOLF)
        PersistentMemoryManager.saveRunMemory(
            context,
            lastKiller = EntityType.WOLF,
            forestMood = ForestMood.FEARFUL,
            pacifistRouteTier = PacifistRouteTier.NONE
        )
        val summary = richSummary(
            lastKiller = EntityType.WOLF,
            forestMood = ForestMood.FEARFUL,
            pacifistRouteTier = PacifistRouteTier.NONE
        )

        val strained = requireNotNull(StoryFragmentSystem.strainedBondPage(context))
        val pages = StoryFragmentSystem.activeHistoryPages(context, summary)
        val marks = StoryFragmentSystem.activeHistoryMarks(context, summary)

        assertSafePage(strained)
        assertPageCatalogue(pages)
        assertMarkCatalogue(marks)
    }

    private fun assertPageCatalogue(pages: List<StoryFragment>) {
        assertEquals(pages.size, pages.map { it.id }.toSet().size)
        pages.forEach(::assertSafePage)
    }

    private fun assertSafePage(page: StoryFragment) {
        assertTrue(page.id.matches(Regex("[a-z0-9_]+")))
        assertTrue(page.id.length in 1..128)
        assertTrue(page.title.isNotBlank())
        assertTrue(page.body.isNotBlank())
        assertTrue(page.title == page.title.trim())
        assertTrue(page.body == page.body.trim())
    }

    private fun assertMarkCatalogue(marks: List<RunHistoryMark>) {
        assertEquals(marks.size, marks.map { it.id }.toSet().size)
        marks.forEach { mark ->
            assertTrue(mark.id.matches(Regex("[a-z0-9_]+")))
            assertTrue(mark.id.length in 1..128)
            assertTrue(mark.title.isNotBlank())
            assertTrue(mark.line.isNotBlank())
            assertTrue(mark.title == mark.title.trim())
            assertTrue(mark.line == mark.line.trim())
        }
    }

    private fun trackedRelationships(): List<EntityType> =
        EntityType.entries.filter(RelationshipArcSystem::isTracked)

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
