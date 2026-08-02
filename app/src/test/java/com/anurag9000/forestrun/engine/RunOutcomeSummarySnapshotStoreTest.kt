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
class RunOutcomeSummarySnapshotStoreTest {

    private lateinit var context: Context
    private lateinit var store: SharedPreferencesRunOutcomeSummarySnapshotStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        prefs().edit().clear().commit()
        store = SharedPreferencesRunOutcomeSummarySnapshotStore(
            context,
            SaveManager.activePrefsNameForTests
        )
    }

    @After
    fun tearDown() {
        prefs().edit().clear().commit()
        SaveManager.usePrimaryPreferences()
    }

    @Test
    fun `snapshot writes sanitized summary and route count atomically`() {
        val malformed = summary(
            tier = PacifistRouteTier.MERCIFUL,
            score = -5,
            distanceM = Float.NaN,
            highScore = -9,
            mercyHearts = -1,
            hitsTaken = -3
        )

        assertTrue(store.save(malformed, routeTierCount = 7))

        assertEquals(
            RunOutcomeRecoveryTransitions.persistedSummary(malformed),
            SaveManager.loadLastRunSummary(context)
        )
        assertEquals(7, SaveManager.loadRouteTierCount(context, PacifistRouteTier.MERCIFUL))
    }

    @Test
    fun `replaying the same snapshot does not increment route count`() {
        val summary = summary(tier = PacifistRouteTier.KIND)

        assertTrue(store.save(summary, routeTierCount = 12))
        assertTrue(store.save(summary, routeTierCount = 12))

        assertEquals(12, SaveManager.loadRouteTierCount(context, PacifistRouteTier.KIND))
        assertEquals(summary, SaveManager.loadLastRunSummary(context))
    }

    @Test
    fun `all persistent route tiers map to their canonical counters`() {
        val cases = listOf(
            PacifistRouteTier.KIND to 3,
            PacifistRouteTier.MERCIFUL to 5,
            PacifistRouteTier.PEACEFUL to 8
        )

        cases.forEach { (tier, count) ->
            assertTrue(store.save(summary(tier = tier), routeTierCount = count))
            assertEquals(count, SaveManager.loadRouteTierCount(context, tier))
        }
    }

    @Test
    fun `none route does not mutate its compatibility counter`() {
        prefs().edit().putInt("route_none_runs", 11).commit()

        assertTrue(store.save(summary(tier = PacifistRouteTier.NONE), routeTierCount = 99))

        assertEquals(11, SaveManager.loadRouteTierCount(context, PacifistRouteTier.NONE))
        assertEquals(PacifistRouteTier.NONE, SaveManager.loadLastRunSummary(context)?.pacifistRouteTier)
    }

    @Test
    fun `canonical derived counter ceiling remains saturated`() {
        val summary = summary(tier = PacifistRouteTier.PEACEFUL)

        assertTrue(store.save(summary, routeTierCount = Int.MAX_VALUE))
        assertTrue(store.save(summary, routeTierCount = Int.MAX_VALUE))

        assertEquals(
            MAX_RECOVERABLE_ROUTE_TIER_COUNT,
            SaveManager.loadRouteTierCount(context, PacifistRouteTier.PEACEFUL)
        )
    }

    private fun summary(
        tier: PacifistRouteTier,
        score: Int = 2_400,
        distanceM: Float = 760f,
        highScore: Int = 3_000,
        mercyHearts: Int = 5,
        hitsTaken: Int = 1
    ): RunSummary = RunSummary(
        score = score,
        distanceM = distanceM,
        isNewHighScore = false,
        highScore = highScore,
        mercyHearts = mercyHearts,
        mercyMisses = 2,
        kindnessChain = 4,
        cleanPasses = 9,
        sparedCount = 3,
        hitsTaken = hitsTaken,
        seedsCollected = 14,
        bloomConversions = 2,
        lastKiller = EntityType.CAT,
        restQuote = "The exact snapshot stayed still.",
        forestMood = ForestMood.STEADY,
        pacifistRouteTier = tier
    )

    private fun prefs() = context.getSharedPreferences(
        SaveManager.PREFS_NAME,
        Context.MODE_PRIVATE
    )
}
