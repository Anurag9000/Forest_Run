package com.yourname.forest_run.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.entities.CostumeStyle
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.systems.GhostFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SaveManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        File(context.filesDir, "ghost_run.bin").delete()
    }

    @Test
    fun `best distance persists across reloads`() {
        SaveManager.saveBestDistance(context, 123.5f)

        assertEquals(123.5f, SaveManager.loadBestDistance(context), 0.0001f)
    }

    @Test
    fun `ghost frames round trip through binary persistence`() {
        val frames = listOf(
            GhostFrame(0.016f, 100f, 200f, 0, 1f, 1f),
            GhostFrame(0.032f, 100f, 180f, 1, 0.9f, 1.1f)
        )

        SaveManager.saveGhostRun(context, frames)
        val reloaded = SaveManager.loadGhostRun(context)

        assertTrue(SaveManager.hasGhostRun(context))
        assertEquals(frames, reloaded)
    }

    @Test
    fun `persistent memory counters and last killer persist`() {
        assertNull(SaveManager.loadLastKiller(context))

        SaveManager.incrementEncounterCount(context, EntityType.FOX)
        SaveManager.incrementEncounterCount(context, EntityType.FOX)
        SaveManager.incrementSparedCount(context, EntityType.FOX)
        SaveManager.incrementHitCount(context, EntityType.WOLF)
        SaveManager.saveLastKiller(context, EntityType.WOLF)

        assertEquals(2, SaveManager.loadEncounterCount(context, EntityType.FOX))
        assertEquals(1, SaveManager.loadSparedCount(context, EntityType.FOX))
        assertEquals(1, SaveManager.loadHitCount(context, EntityType.WOLF))
        assertEquals(EntityType.WOLF, SaveManager.loadLastKiller(context))
    }

    @Test
    fun `costume unlocks and active costume persist`() {
        SaveManager.saveUnlockedCostumes(context, setOf(CostumeStyle.FLOWER_CROWN, CostumeStyle.MOON_CAPE))
        SaveManager.saveActiveCostume(context, CostumeStyle.MOON_CAPE)

        assertEquals(
            setOf(CostumeStyle.FLOWER_CROWN, CostumeStyle.MOON_CAPE),
            SaveManager.loadUnlockedCostumes(context)
        )
        assertEquals(CostumeStyle.MOON_CAPE, SaveManager.loadActiveCostume(context))
    }

    @Test
    fun `biome friendship persists across reloads`() {
        SaveManager.incrementBiomeFriendship(context, Biome.MEADOW)
        SaveManager.incrementBiomeFriendship(context, Biome.MEADOW)
        SaveManager.incrementBiomeFriendship(context, Biome.NIGHT_FOREST)

        assertEquals(2, SaveManager.loadBiomeFriendship(context, Biome.MEADOW))
        assertEquals(1, SaveManager.loadBiomeFriendship(context, Biome.NIGHT_FOREST))
    }

    @Test
    fun `last run summary persists across reloads`() {
        val summary = RunSummary(
            score = 1280,
            distanceM = 642.5f,
            isNewHighScore = true,
            highScore = 1280,
            mercyHearts = 4,
            mercyMisses = 4,
            kindnessChain = 7,
            cleanPasses = 9,
            sparedCount = 2,
            hitsTaken = 1,
            seedsCollected = 11,
            bloomConversions = 3,
            lastKiller = EntityType.WOLF,
            restQuote = "The grove asks for patience before bravery.",
            forestMood = ForestMood.GENTLE
        )

        SaveManager.saveLastRunSummary(context, summary)

        assertEquals(summary, SaveManager.loadLastRunSummary(context))
    }

    @Test
    fun `forest mood state persists across reloads`() {
        val state = ForestMoodState(
            currentMood = ForestMood.RECKLESS,
            moodStreak = 2,
            totalRuns = 5,
            gentleRuns = 1,
            recklessRuns = 3,
            fearfulRuns = 0,
            steadyRuns = 1
        )

        SaveManager.saveForestMoodState(context, state)

        assertEquals(state, SaveManager.loadForestMoodState(context))
    }
}
