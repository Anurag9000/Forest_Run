package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RunOutcomeRecoveryIntegrationTest {

    private lateinit var context: Context
    private lateinit var store: SharedPreferencesRunOutcomeRecoveryStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = SharedPreferencesRunOutcomeRecoveryStore(
            context,
            SaveManager.activePrefsNameForTests
        )
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        SaveManager.usePrimaryPreferences()
    }

    @Test
    fun `production startup recovery finishes bundle without duplicating applied mood`() {
        val summary = summary()
        val previousMood = ForestMoodState(
            currentMood = ForestMood.STEADY,
            moodStreak = 2,
            totalRuns = 8,
            gentleRuns = 2,
            recklessRuns = 1,
            fearfulRuns = 1,
            steadyRuns = 4
        )
        val nextMood = RunOutcomeRecoveryTransitions.nextForestMood(previousMood, summary)
        val previousReturn = ReturnMomentState(
            lastActiveAtMs = 100L,
            lastGardenGreetingDay = 50L,
            roughRunStreak = 3
        )
        val nextReturn = RunOutcomeRecoveryTransitions.nextReturnMoment(
            previousReturn,
            summary,
            FIXED_NOW_MS
        )
        val pending = RunOutcomeRecoveryRecord(
            phase = RunOutcomeRecoveryPhase.PREPARED,
            summary = summary,
            previousMood = previousMood,
            nextMood = nextMood,
            previousReturn = previousReturn,
            nextReturn = nextReturn
        )

        SaveManager.saveForestMoodState(context, nextMood)
        SaveManager.saveReturnMomentState(context, previousReturn)
        assertTrue(store.save(pending))

        RunOutcomePersistenceCoordinator(AndroidRunOutcomePersistenceSink(context))

        assertEquals(nextMood, SaveManager.loadForestMoodState(context))
        assertEquals(9, SaveManager.loadForestMoodState(context).totalRuns)
        assertEquals(nextReturn, SaveManager.loadReturnMomentState(context))
        assertEquals(summary, SaveManager.loadLastRunSummary(context))
        assertEquals(RunOutcomeRecoveryLoadResult.Empty, store.load())
    }

    @Test
    fun `production conflict retains evidence and blocks a new write bundle`() {
        val summary = summary()
        val previousMood = ForestMoodState()
        val nextMood = RunOutcomeRecoveryTransitions.nextForestMood(previousMood, summary)
        val previousReturn = ReturnMomentState()
        val pending = RunOutcomeRecoveryRecord(
            phase = RunOutcomeRecoveryPhase.PREPARED,
            summary = summary,
            previousMood = previousMood,
            nextMood = nextMood,
            previousReturn = previousReturn,
            nextReturn = RunOutcomeRecoveryTransitions.nextReturnMoment(
                previousReturn,
                summary,
                FIXED_NOW_MS
            )
        )
        SaveManager.saveForestMoodState(
            context,
            previousMood.copy(totalRuns = 7, steadyRuns = 7)
        )
        assertTrue(store.save(pending))
        val coordinator = RunOutcomePersistenceCoordinator(
            AndroidRunOutcomePersistenceSink(context)
        )

        val result = coordinator.commit(summary, emptyList(), persistProgress = true)

        assertEquals(RunOutcomeCommitDisposition.RECOVERY_BLOCKED, result.disposition)
        assertTrue(store.load() is RunOutcomeRecoveryLoadResult.Pending)
        assertEquals(null, SaveManager.loadLastRunSummary(context))
    }

    private fun summary(): RunSummary = RunSummary(
        score = 4_200,
        distanceM = 880f,
        isNewHighScore = true,
        highScore = 4_200,
        mercyHearts = 6,
        mercyMisses = 2,
        kindnessChain = 5,
        cleanPasses = 12,
        sparedCount = 3,
        hitsTaken = 1,
        seedsCollected = 18,
        bloomConversions = 3,
        lastKiller = EntityType.WOLF,
        restQuote = "The grove kept the unfinished run safe.",
        forestMood = ForestMood.GENTLE,
        pacifistRouteTier = PacifistRouteTier.MERCIFUL
    )

    private companion object {
        const val FIXED_NOW_MS = 1_725_000_000_000L
    }
}
