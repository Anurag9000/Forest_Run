package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RunOutcomeRecoveryTransitionIntegrationTest {

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

    @After
    fun tearDown() {
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        SaveManager.usePrimaryPreferences()
    }

    @Test
    fun `journal mood transitions match canonical system for every mood`() {
        ForestMood.entries.forEachIndexed { index, mood ->
            val previous = ForestMoodState(
                currentMood = if (index % 2 == 0) mood else ForestMood.STEADY,
                moodStreak = 3 + index,
                totalRuns = 10 + index,
                gentleRuns = 1,
                recklessRuns = 2,
                fearfulRuns = 3,
                steadyRuns = 4
            )
            SaveManager.saveForestMoodState(context, previous)
            val summary = summary(forestMood = mood)

            val expected = RunOutcomeRecoveryTransitions.nextForestMood(previous, summary)
            val actual = ForestMoodSystem.recordRun(context, summary)

            assertEquals(mood.name, expected, actual)
        }
    }

    @Test
    fun `journal mood transition matches canonical saturation behavior`() {
        val previous = ForestMoodState(
            currentMood = ForestMood.GENTLE,
            moodStreak = Int.MAX_VALUE,
            totalRuns = Int.MAX_VALUE,
            gentleRuns = Int.MAX_VALUE,
            recklessRuns = 2,
            fearfulRuns = 3,
            steadyRuns = 4
        )
        SaveManager.saveForestMoodState(context, previous)
        val summary = summary(forestMood = ForestMood.GENTLE)

        val expected = RunOutcomeRecoveryTransitions.nextForestMood(previous, summary)
        val actual = ForestMoodSystem.recordRun(context, summary)

        assertEquals(expected, actual)
    }

    @Test
    fun `journal rough return transition matches canonical system`() {
        val previous = ReturnMomentState(
            lastActiveAtMs = 100L,
            lastGardenGreetingDay = 12L,
            roughRunStreak = 4
        )
        SaveManager.saveReturnMomentState(context, previous)
        val summary = summary(
            distanceM = 300f,
            forestMood = ForestMood.FEARFUL,
            hitsTaken = 2,
            kindnessChain = 0,
            seedsCollected = 1
        )

        val expected = RunOutcomeRecoveryTransitions.nextReturnMoment(
            previous,
            summary,
            FIXED_NOW_MS
        )
        ReturnMomentsSystem.recordRunOutcome(context, summary, FIXED_NOW_MS)
        val actual = SaveManager.loadReturnMomentState(context)

        assertEquals(expected, actual)
    }

    @Test
    fun `journal calm return transition matches canonical reset behavior`() {
        val previous = ReturnMomentState(
            lastActiveAtMs = 100L,
            lastGardenGreetingDay = 44L,
            roughRunStreak = 9
        )
        SaveManager.saveReturnMomentState(context, previous)
        val summary = summary(
            distanceM = 1_100f,
            forestMood = ForestMood.STEADY,
            hitsTaken = 0,
            kindnessChain = 5,
            seedsCollected = 12
        )

        val expected = RunOutcomeRecoveryTransitions.nextReturnMoment(
            previous,
            summary,
            FIXED_NOW_MS
        )
        ReturnMomentsSystem.recordRunOutcome(context, summary, FIXED_NOW_MS)
        val actual = SaveManager.loadReturnMomentState(context)

        assertEquals(expected, actual)
    }

    @Test
    fun `journal route transition matches canonical single increment`() {
        val tier = PacifistRouteTier.KIND
        val summary = summary(forestMood = ForestMood.STEADY, routeTier = tier)
        val snapshot = SharedPreferencesRunOutcomeSummarySnapshotStore(
            context,
            SaveManager.activePrefsNameForTests
        )
        val previous = SaveManager.loadRouteTierCount(context, tier)
        val expected = RunOutcomeRecoveryTransitions.nextRouteTierCount(previous, tier)

        snapshot.save(summary, expected)

        assertEquals(expected, SaveManager.loadRouteTierCount(context, tier))
    }

    @Test
    fun `journal route transition preserves none and saturated counts`() {
        assertEquals(
            7,
            RunOutcomeRecoveryTransitions.nextRouteTierCount(7, PacifistRouteTier.NONE)
        )
        assertEquals(
            Int.MAX_VALUE,
            RunOutcomeRecoveryTransitions.nextRouteTierCount(
                Int.MAX_VALUE,
                PacifistRouteTier.PEACEFUL
            )
        )
    }

    private fun summary(
        distanceM: Float = 800f,
        forestMood: ForestMood,
        hitsTaken: Int = 1,
        kindnessChain: Int = 4,
        seedsCollected: Int = 10,
        routeTier: PacifistRouteTier = PacifistRouteTier.KIND
    ): RunSummary = RunSummary(
        score = 2_000,
        distanceM = distanceM,
        isNewHighScore = false,
        highScore = 3_000,
        mercyHearts = 4,
        mercyMisses = 1,
        kindnessChain = kindnessChain,
        cleanPasses = 7,
        sparedCount = 2,
        hitsTaken = hitsTaken,
        seedsCollected = seedsCollected,
        bloomConversions = 2,
        lastKiller = null,
        restQuote = "Rest.",
        forestMood = forestMood,
        pacifistRouteTier = routeTier
    )

    private companion object {
        const val FIXED_NOW_MS = 1_725_000_000_000L
    }
}
