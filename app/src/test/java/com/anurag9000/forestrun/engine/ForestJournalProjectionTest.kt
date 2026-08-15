package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForestJournalProjectionTest {

    @Test
    fun `garden economy exposes one contiguous canonical catalogue`() {
        val entries = GardenEconomy.entries

        assertEquals(9, entries.size)
        assertEquals(entries.indices.toList(), entries.map(GardenPlantEconomy::index))
        assertEquals(entries.size, entries.map(GardenPlantEconomy::displayName).distinct().size)
        assertEquals(entries.size, entries.map(GardenPlantEconomy::compactName).distinct().size)
        assertTrue(entries.all { it.seedCost > 0 })
        assertEquals(
            listOf(15, 20, 25, 30, 40, 50, 60, 75, 100),
            entries.map(GardenPlantEconomy::seedCost)
        )
        assertEquals("Vanilla Orchid", GardenEconomy.plantForIndex(4)?.displayName)
        assertEquals("Orchid", GardenEconomy.plantForIndex(4)?.compactName)
        assertEquals("Weeping Willow", GardenEconomy.plantForIndex(5)?.displayName)
        assertEquals("Willow", GardenEconomy.plantForIndex(5)?.compactName)
        assertEquals("Cherry Blossom", GardenEconomy.plantForIndex(8)?.displayName)
        assertEquals("Cherry", GardenEconomy.plantForIndex(8)?.compactName)
        assertEquals(40, GardenEconomy.seedCostForIndex(4))
        assertNull(GardenEconomy.plantForIndex(-1))
        assertNull(GardenEconomy.seedCostForIndex(entries.size))
    }

    @Test
    fun `path history completion requires every gentle route tier`() {
        val partial = ForestPathHistorySnapshot(
            paths = listOf(
                path(PacifistRouteTier.KIND, 2),
                path(PacifistRouteTier.MERCIFUL, 1),
                path(PacifistRouteTier.PEACEFUL, 0)
            )
        )
        assertEquals(2, partial.discoveredTiers)
        assertEquals(3, partial.totalTiers)
        assertFalse(partial.allGentleShapesSeen)

        val complete = ForestPathHistorySnapshot(
            paths = listOf(
                path(PacifistRouteTier.KIND, 2),
                path(PacifistRouteTier.MERCIFUL, 1),
                path(PacifistRouteTier.PEACEFUL, 1)
            )
        )
        assertTrue(complete.allGentleShapesSeen)
    }

    @Test
    fun `whole forest capstone needs every collection track and route pillar`() {
        val completeCollection = collection(
            listOf(
                track("families", 19, 19),
                track("bonds", 6, 6),
                track("garden", 9, 9),
                track("wardrobe", 8, 8),
                track("biomes", 5, 5)
            )
        )
        val completePaths = ForestPathHistorySnapshot(
            listOf(
                path(PacifistRouteTier.KIND, 1),
                path(PacifistRouteTier.MERCIFUL, 1),
                path(PacifistRouteTier.PEACEFUL, 1)
            )
        )

        val complete = ForestCompletionCapstoneComposer.compose(completeCollection, completePaths)
        assertEquals(6, complete.completedPillars)
        assertEquals(6, complete.totalPillars)
        assertTrue(complete.complete)
        assertEquals("The Forest Knows Your Name", complete.title)

        val missingGarden = collection(
            listOf(
                track("families", 19, 19),
                track("bonds", 6, 6),
                track("garden", 8, 9),
                track("wardrobe", 8, 8),
                track("biomes", 5, 5)
            )
        )
        val incomplete = ForestCompletionCapstoneComposer.compose(missingGarden, completePaths)
        assertEquals(5, incomplete.completedPillars)
        assertFalse(incomplete.complete)
        assertEquals("A Forest Still Becoming", incomplete.title)

        val missingRouteShape = ForestCompletionCapstoneComposer.compose(
            completeCollection,
            ForestPathHistorySnapshot(
                listOf(
                    path(PacifistRouteTier.KIND, 1),
                    path(PacifistRouteTier.MERCIFUL, 1),
                    path(PacifistRouteTier.PEACEFUL, 0)
                )
            )
        )
        assertEquals(5, missingRouteShape.completedPillars)
        assertFalse(missingRouteShape.complete)
    }

    @Test
    fun `legacy mood sanitization happens before dominant mood selection`() {
        val corrupt = ForestMoodState(
            currentMood = ForestMood.FEARFUL,
            moodStreak = -8,
            totalRuns = -20,
            gentleRuns = -4,
            recklessRuns = -3,
            fearfulRuns = 2,
            steadyRuns = -1
        )

        val sanitized = ForestRunLegacyComposer.sanitizedMoodState(corrupt)

        assertEquals(0, sanitized.moodStreak)
        assertEquals(0, sanitized.totalRuns)
        assertEquals(0, sanitized.gentleRuns)
        assertEquals(0, sanitized.recklessRuns)
        assertEquals(2, sanitized.fearfulRuns)
        assertEquals(0, sanitized.steadyRuns)
        assertEquals(ForestMood.FEARFUL, sanitized.dominantMood)
    }

    @Test
    fun `legacy distance projection rejects nonfinite and negative values`() {
        assertEquals(0, ForestRunLegacyComposer.safeDistanceMetres(Float.NaN))
        assertEquals(0, ForestRunLegacyComposer.safeDistanceMetres(Float.POSITIVE_INFINITY))
        assertEquals(0, ForestRunLegacyComposer.safeDistanceMetres(-10f))
        assertEquals(0, ForestRunLegacyComposer.safeDistanceMetres(0f))
        assertEquals(1_234, ForestRunLegacyComposer.safeDistanceMetres(1_234.9f))
    }

    @Test
    fun `memory page narrative specializes known persistent history families`() {
        val learned = presentation(
            id = "page_thought_learned_fox",
            title = "Fox — Familiar Lesson",
            category = "CREATURE MEMORY",
            fallback = "fallback"
        )
        assertTrue(ForestMemoryPageNarrative.lineFor(learned).contains("Fox"))
        assertTrue(ForestMemoryPageNarrative.lineFor(learned).contains("Repeated gentle meetings"))

        val bloom = presentation(
            id = "page_weather_bloom",
            title = "Bloom in the Evening Air",
            category = "FOREST WEATHER",
            fallback = "fallback"
        )
        assertTrue(ForestMemoryPageNarrative.lineFor(bloom).contains("six-second"))

        val strained = presentation(
            id = "page_garden_strained_wolf",
            title = "Wolf — Garden",
            category = "GARDEN MEMORY",
            fallback = "fallback"
        )
        assertTrue(ForestMemoryPageNarrative.lineFor(strained).contains("Wolf"))
        assertTrue(ForestMemoryPageNarrative.lineFor(strained).contains("guarded"))
    }

    @Test
    fun `memory page narrative preserves presenter fallback for unknown ids`() {
        val unknown = presentation(
            id = "page_future_constellation_memory",
            title = "Future Constellation Memory",
            category = "FOREST MEMORY",
            fallback = "A future-safe fallback line."
        )

        assertEquals("A future-safe fallback line.", ForestMemoryPageNarrative.lineFor(unknown))
    }

    private fun path(tier: PacifistRouteTier, runs: Int) = ForestPathMemory(
        tier = tier,
        label = tier.displayName,
        runCount = runs,
        line = "Remembered ${tier.displayName} history."
    )

    private fun track(id: String, completed: Int, total: Int) = ForestCollectionTrack(
        id = id,
        label = id,
        completed = completed,
        total = total,
        detail = "Derived progression for $id."
    )

    private fun collection(tracks: List<ForestCollectionTrack>) = ForestCollectionSnapshot(
        tracks = tracks,
        milestones = emptyList(),
        relationships = emptyList(),
        wardrobe = emptyList(),
        memoryPages = emptyList(),
        kindRuns = 0,
        mercifulRuns = 0,
        peacefulRuns = 0
    )

    private fun presentation(
        id: String,
        title: String,
        category: String,
        fallback: String
    ) = ForestMemoryPagePresentation(
        id = id,
        title = title,
        category = category,
        line = fallback
    )
}
