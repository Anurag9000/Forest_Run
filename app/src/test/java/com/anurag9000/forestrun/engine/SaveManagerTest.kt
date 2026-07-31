package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.CostumeStyle
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.systems.GhostFrame
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
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        File(context.filesDir, "ghost_run.bin").delete()
    }

    @Test
    fun `legacy Garden write sequence commits progress and Seeds atomically`() {
        SaveManager.saveLifetimeSeeds(context, 50)
        SaveManager.saveGardenProgress(context, 1)

        SaveManager.saveGardenProgress(context, 2)

        // Both values are already durable before the historical second call.
        assertEquals(2, SaveManager.loadGardenProgress(context))
        assertEquals(30, SaveManager.loadLifetimeSeeds(context))

        SaveManager.saveLifetimeSeeds(context, 30)
        assertEquals(2, SaveManager.loadGardenProgress(context))
        assertEquals(30, SaveManager.loadLifetimeSeeds(context))
    }

    @Test
    fun `stale high Garden cache cannot overwrite canonical transaction balance`() {
        SaveManager.saveLifetimeSeeds(context, 50)
        SaveManager.saveGardenProgress(context, 1)

        SaveManager.saveGardenProgress(context, 2)
        // A stale screen believed it held 100 Seeds and tries to persist 80.
        SaveManager.saveLifetimeSeeds(context, 80)

        assertEquals(2, SaveManager.loadGardenProgress(context))
        assertEquals(30, SaveManager.loadLifetimeSeeds(context))
    }

    @Test
    fun `rejected Garden purchase absorbs stale follow up without charging`() {
        SaveManager.saveLifetimeSeeds(context, 10)
        SaveManager.saveGardenProgress(context, 1)

        SaveManager.saveGardenProgress(context, 2)
        SaveManager.saveLifetimeSeeds(context, 0)

        assertEquals(1, SaveManager.loadGardenProgress(context))
        assertEquals(10, SaveManager.loadLifetimeSeeds(context))
    }

    @Test
    fun `non sequential administrative progress remains a direct clamped write`() {
        SaveManager.saveLifetimeSeeds(context, 12)

        SaveManager.saveGardenProgress(context, 7)

        assertEquals(7, SaveManager.loadGardenProgress(context))
        assertEquals(12, SaveManager.loadLifetimeSeeds(context))
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
    fun `kindness and tender streaks persist across reloads`() {
        SaveManager.incrementKindnessStreak(context, EntityType.CAT)
        SaveManager.incrementKindnessStreak(context, EntityType.CAT)
        SaveManager.incrementTenderStreak(context, EntityType.WOLF)

        assertEquals(2, SaveManager.loadKindnessStreak(context, EntityType.CAT))
        assertEquals(1, SaveManager.loadTenderStreak(context, EntityType.WOLF))

        SaveManager.resetKindnessStreak(context, EntityType.CAT)
        SaveManager.resetTenderStreak(context, EntityType.WOLF)

        assertEquals(0, SaveManager.loadKindnessStreak(context, EntityType.CAT))
        assertEquals(0, SaveManager.loadTenderStreak(context, EntityType.WOLF))
    }

    @Test
    fun `clean pass memory persists across reloads`() {
        SaveManager.incrementCleanPassCount(context, EntityType.CACTUS)
        SaveManager.incrementCleanPassCount(context, EntityType.CACTUS)
        SaveManager.incrementCleanPassCount(context, EntityType.LILY_OF_VALLEY)

        assertEquals(2, SaveManager.loadCleanPassCount(context, EntityType.CACTUS))
        assertEquals(1, SaveManager.loadCleanPassCount(context, EntityType.LILY_OF_VALLEY))
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
            forestMood = ForestMood.GENTLE,
            pacifistRouteTier = PacifistRouteTier.MERCIFUL
        )

        SaveManager.saveLastRunSummary(context, summary)

        assertEquals(summary, SaveManager.loadLastRunSummary(context))
    }

    @Test
    fun `route history counts accumulate from saved run summaries`() {
        SaveManager.saveLastRunSummary(
            context,
            RunSummary(
                score = 200,
                distanceM = 180f,
                isNewHighScore = false,
                highScore = 400,
                mercyHearts = 2,
                mercyMisses = 2,
                kindnessChain = 4,
                cleanPasses = 4,
                sparedCount = 1,
                hitsTaken = 1,
                seedsCollected = 2,
                bloomConversions = 0,
                lastKiller = null,
                restQuote = "Kindly.",
                forestMood = ForestMood.GENTLE,
                pacifistRouteTier = PacifistRouteTier.KIND
            )
        )
        SaveManager.saveLastRunSummary(
            context,
            RunSummary(
                score = 320,
                distanceM = 260f,
                isNewHighScore = false,
                highScore = 400,
                mercyHearts = 3,
                mercyMisses = 3,
                kindnessChain = 6,
                cleanPasses = 7,
                sparedCount = 2,
                hitsTaken = 0,
                seedsCollected = 3,
                bloomConversions = 0,
                lastKiller = null,
                restQuote = "Mercy.",
                forestMood = ForestMood.GENTLE,
                pacifistRouteTier = PacifistRouteTier.MERCIFUL
            )
        )
        SaveManager.saveLastRunSummary(
            context,
            RunSummary(
                score = 520,
                distanceM = 420f,
                isNewHighScore = false,
                highScore = 600,
                mercyHearts = 5,
                mercyMisses = 5,
                kindnessChain = 8,
                cleanPasses = 10,
                sparedCount = 2,
                hitsTaken = 0,
                seedsCollected = 5,
                bloomConversions = 1,
                lastKiller = null,
                restQuote = "Peace.",
                forestMood = ForestMood.GENTLE,
                pacifistRouteTier = PacifistRouteTier.PEACEFUL
            )
        )

        assertEquals(1, SaveManager.loadRouteTierCount(context, PacifistRouteTier.KIND))
        assertEquals(1, SaveManager.loadRouteTierCount(context, PacifistRouteTier.MERCIFUL))
        assertEquals(1, SaveManager.loadRouteTierCount(context, PacifistRouteTier.PEACEFUL))
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

    @Test
    fun `return moment state persists across reloads`() {
        val state = ReturnMomentState(
            lastActiveAtMs = 9_876L,
            lastGardenGreetingDay = 42L,
            roughRunStreak = 3
        )

        SaveManager.saveReturnMomentState(context, state)

        assertEquals(state, SaveManager.loadReturnMomentState(context))
    }

    @Test
    fun `relationship stages persist across reloads`() {
        SaveManager.saveRelationshipStage(context, EntityType.CAT, RelationshipStage.TRUST)

        assertEquals(RelationshipStage.TRUST, SaveManager.loadRelationshipStage(context, EntityType.CAT))
    }

    @Test
    fun `memory pages persist across reloads`() {
        SaveManager.saveUnlockedMemoryPages(context, setOf("page_repeat_wolf", "page_after_best"))

        assertEquals(
            setOf("page_repeat_wolf", "page_after_best"),
            SaveManager.loadUnlockedMemoryPages(context)
        )
    }

    @Test
    fun `history marks persist across reloads`() {
        SaveManager.saveUnlockedHistoryMarks(context, setOf("history_kindness_cat", "history_repeat_killer_wolf"))

        assertEquals(
            setOf("history_kindness_cat", "history_repeat_killer_wolf"),
            SaveManager.loadUnlockedHistoryMarks(context)
        )
    }
}
