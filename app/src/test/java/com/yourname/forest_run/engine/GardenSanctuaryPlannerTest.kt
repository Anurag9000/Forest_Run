package com.yourname.forest_run.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GardenSanctuaryPlannerTest {

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
    fun `gentle bonded runs brighten sanctuary and surface traces`() {
        repeat(3) { PersistentMemoryManager.recordEncounter(context, EntityType.CAT) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }
        SaveManager.saveForestMoodState(
            context,
            ForestMoodState(currentMood = ForestMood.GENTLE, moodStreak = 3, totalRuns = 3, gentleRuns = 3)
        )
        val summary = RunSummary(
            score = 1_240,
            distanceM = 890f,
            isNewHighScore = true,
            highScore = 1_240,
            mercyHearts = 5,
            mercyMisses = 5,
            kindnessChain = 6,
            cleanPasses = 9,
            sparedCount = 2,
            hitsTaken = 0,
            seedsCollected = 10,
            bloomConversions = 2,
            lastKiller = null,
            restQuote = "Softly.",
            forestMood = ForestMood.GENTLE
        )

        val state = GardenSanctuaryPlanner.build(context, summary)

        assertTrue(state.fireflyCount >= 6)
        assertTrue(state.bloomPatchCount >= 2)
        assertTrue(state.traces.any { it.type == EntityType.CAT })
    }

    @Test
    fun `fearful runs keep sanctuary quieter`() {
        SaveManager.saveForestMoodState(
            context,
            ForestMoodState(currentMood = ForestMood.FEARFUL, moodStreak = 2, totalRuns = 2, fearfulRuns = 2)
        )

        val state = GardenSanctuaryPlanner.build(context, null)

        assertEquals(ForestMood.FEARFUL.gardenLine, ForestMoodSystem.currentState(context).currentMood.gardenLine)
        assertTrue(state.sanctuaryLine.contains("lowers its voice"))
        assertTrue(state.canopyShadeAlpha >= 50)
    }

    @Test
    fun `repeated harm leaves a cautious sanctuary trace`() {
        repeat(2) { PersistentMemoryManager.recordHit(context, EntityType.WOLF) }
        SaveManager.saveForestMoodState(
            context,
            ForestMoodState(currentMood = ForestMood.FEARFUL, moodStreak = 3, totalRuns = 3, fearfulRuns = 3)
        )
        val summary = RunSummary(
            score = 420,
            distanceM = 330f,
            isNewHighScore = false,
            highScore = 990,
            mercyHearts = 1,
            mercyMisses = 1,
            kindnessChain = 0,
            cleanPasses = 2,
            sparedCount = 0,
            hitsTaken = 2,
            seedsCollected = 3,
            bloomConversions = 0,
            lastKiller = EntityType.WOLF,
            restQuote = "Again.",
            forestMood = ForestMood.FEARFUL
        )

        val state = GardenSanctuaryPlanner.build(context, summary)

        assertEquals("Cautious Path", state.traces.first().label)
        assertEquals(EntityType.WOLF, state.traces.first().type)
        assertTrue(state.sanctuaryLine.contains("tender"))
        assertTrue(state.carryHomeLine.contains("Wolf"))
        assertTrue(state.canopyShadeAlpha >= 64)
    }

    @Test
    fun `milestone bond adds keepsake trace and warmer carry-home`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.CAT) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }
        SaveManager.saveForestMoodState(
            context,
            ForestMoodState(currentMood = ForestMood.GENTLE, moodStreak = 4, totalRuns = 4, gentleRuns = 4)
        )

        val state = GardenSanctuaryPlanner.build(context, null)

        assertTrue(state.traces.any { it.label == "Napping Patch" })
        assertTrue(state.carryHomeLine.contains("quiet patch") || state.carryHomeLine.contains("home"))
        assertTrue(state.fireflyCount >= 6)
    }

    @Test
    fun `repeated kindness leaves a trust path and warmer carry-home`() {
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.FOX) }
        SaveManager.saveForestMoodState(
            context,
            ForestMoodState(currentMood = ForestMood.GENTLE, moodStreak = 2, totalRuns = 2, gentleRuns = 2)
        )
        val summary = RunSummary(
            score = 760,
            distanceM = 590f,
            isNewHighScore = false,
            highScore = 1_100,
            mercyHearts = 2,
            mercyMisses = 2,
            kindnessChain = 4,
            cleanPasses = 6,
            sparedCount = 1,
            hitsTaken = 0,
            seedsCollected = 5,
            bloomConversions = 0,
            lastKiller = null,
            restQuote = "Softly.",
            forestMood = ForestMood.GENTLE
        )

        val state = GardenSanctuaryPlanner.build(context, summary)

        assertTrue(state.traces.any { it.label == "Trust Path" && it.type == EntityType.FOX })
        assertTrue(state.carryHomeLine.contains("Fox") || state.carryHomeLine.contains("trust"))
    }
}
