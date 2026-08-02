package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RunOutcomeRecoveryStoreTest {

    private lateinit var context: Context
    private lateinit var store: SharedPreferencesRunOutcomeRecoveryStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        rawPrefs().edit().clear().commit()
        store = SharedPreferencesRunOutcomeRecoveryStore(context, NAMESPACE)
    }

    @After
    fun tearDown() {
        rawPrefs().edit().clear().commit()
    }

    @Test
    fun `journal round trips every summary and state field`() {
        val record = record()

        assertTrue(store.save(record))
        val loaded = store.load()

        assertTrue(loaded is RunOutcomeRecoveryLoadResult.Pending)
        assertEquals(record, (loaded as RunOutcomeRecoveryLoadResult.Pending).record)
    }

    @Test
    fun `empty and cleared stores load as empty`() {
        assertEquals(RunOutcomeRecoveryLoadResult.Empty, store.load())

        assertTrue(store.save(record()))
        assertTrue(store.clear())

        assertEquals(RunOutcomeRecoveryLoadResult.Empty, store.load())
    }

    @Test
    fun `wrong schema and enum names are retained as corrupt evidence`() {
        rawPrefs().edit()
            .putBoolean("present", true)
            .putInt("schema", 99)
            .commit()
        assertEquals(RunOutcomeRecoveryLoadResult.Corrupt, store.load())

        assertTrue(store.save(record()))
        rawPrefs().edit().putString("phase", "NOT_A_PHASE").commit()
        assertEquals(RunOutcomeRecoveryLoadResult.Corrupt, store.load())
        assertTrue(rawPrefs().getBoolean("present", false))
    }

    @Test
    fun `wrong preference types fail closed instead of escaping`() {
        rawPrefs().edit()
            .putString("present", "yes")
            .commit()

        assertEquals(RunOutcomeRecoveryLoadResult.Corrupt, store.load())
    }

    @Test
    fun `invalid records are rejected without replacing existing evidence`() {
        val valid = record()
        assertTrue(store.save(valid))
        val invalid = valid.copy(summary = valid.summary.copy(score = -1))

        assertFalse(store.save(invalid))

        assertEquals(
            valid,
            (store.load() as RunOutcomeRecoveryLoadResult.Pending).record
        )
    }

    private fun record(): RunOutcomeRecoveryRecord = RunOutcomeRecoveryRecord(
        phase = RunOutcomeRecoveryPhase.RETURN_APPLIED,
        summary = RunSummary(
            score = 12_345,
            distanceM = Float.NaN,
            isNewHighScore = true,
            highScore = 12_345,
            mercyHearts = 8,
            mercyMisses = 3,
            kindnessChain = 6,
            cleanPasses = 14,
            sparedCount = 4,
            hitsTaken = 1,
            seedsCollected = 23,
            bloomConversions = 5,
            lastKiller = EntityType.WOLF,
            restQuote = "The willow kept the exact words.",
            forestMood = ForestMood.GENTLE,
            pacifistRouteTier = PacifistRouteTier.MERCIFUL
        ),
        previousMood = ForestMoodState(
            currentMood = ForestMood.STEADY,
            moodStreak = 2,
            totalRuns = 10,
            gentleRuns = 2,
            recklessRuns = 1,
            fearfulRuns = 3,
            steadyRuns = 4
        ),
        nextMood = ForestMoodState(
            currentMood = ForestMood.GENTLE,
            moodStreak = 1,
            totalRuns = 11,
            gentleRuns = 3,
            recklessRuns = 1,
            fearfulRuns = 3,
            steadyRuns = 4
        ),
        previousReturn = ReturnMomentState(
            lastActiveAtMs = 1_700_000_000_000L,
            lastGardenGreetingDay = 19_000L,
            roughRunStreak = 2
        ),
        nextReturn = ReturnMomentState(
            lastActiveAtMs = 1_725_000_000_000L,
            lastGardenGreetingDay = 19_000L,
            roughRunStreak = 0
        )
    )

    private fun rawPrefs() = context.getSharedPreferences(
        "forest_run_outcome_recovery_test_namespace",
        Context.MODE_PRIVATE
    )

    private companion object {
        const val NAMESPACE = "test/namespace"
    }
}
