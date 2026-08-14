package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GardenPurchaseManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        clearActivePreferences()
    }

    @After
    fun tearDown() {
        SaveManager.usePrimaryPreferences()
        clearActivePreferences()
    }

    @Test
    fun `purchase atomically advances progress and deducts canonical balance`() {
        SaveManager.saveGardenProgress(context, 1)
        SaveManager.saveLifetimeSeeds(context, 50)

        val result = GardenPurchaseManager.purchaseNext(context, requestedIndex = 1)

        assertTrue(result.purchased)
        assertEquals(2, result.unlockedCount)
        assertEquals(30, result.remainingSeeds)
        assertEquals(2, SaveManager.loadGardenProgress(context))
        assertEquals(30, SaveManager.loadLifetimeSeeds(context))
    }

    @Test
    fun `exact cumulative paid catalogue budget unlocks every remaining plant to zero`() {
        val totalBudget = paidCatalogueBudget()
        SaveManager.saveGardenProgress(context, 1)
        SaveManager.saveLifetimeSeeds(context, totalBudget)

        var expectedBalance = totalBudget
        for (index in 1 until GardenEconomy.catalogueSize) {
            val cost = requireNotNull(GardenEconomy.seedCostForIndex(index))
            val result = GardenPurchaseManager.purchaseNext(context, index)
            expectedBalance -= cost

            assertEquals(GardenPurchaseStatus.PURCHASED, result.status)
            assertEquals(index + 1, result.unlockedCount)
            assertEquals(expectedBalance, result.remainingSeeds)
            assertEquals(index + 1, SaveManager.loadGardenProgress(context))
            assertEquals(expectedBalance, SaveManager.loadLifetimeSeeds(context))
        }

        assertEquals(GardenEconomy.catalogueSize, SaveManager.loadGardenProgress(context))
        assertEquals(0, SaveManager.loadLifetimeSeeds(context))
    }

    @Test
    fun `one seed below cumulative paid catalogue budget stops only at final boundary`() {
        val oneSeedShort = paidCatalogueBudget() - 1
        SaveManager.saveGardenProgress(context, 1)
        SaveManager.saveLifetimeSeeds(context, oneSeedShort)

        for (index in 1 until GardenEconomy.catalogueSize - 1) {
            val result = GardenPurchaseManager.purchaseNext(context, index)
            assertEquals(GardenPurchaseStatus.PURCHASED, result.status)
        }

        val finalIndex = GardenEconomy.catalogueSize - 1
        val progressBefore = SaveManager.loadGardenProgress(context)
        val seedsBefore = SaveManager.loadLifetimeSeeds(context)
        val finalCost = requireNotNull(GardenEconomy.seedCostForIndex(finalIndex))
        assertEquals(finalCost - 1, seedsBefore)

        val rejected = GardenPurchaseManager.purchaseNext(context, finalIndex)

        assertEquals(GardenPurchaseStatus.INSUFFICIENT_SEEDS, rejected.status)
        assertEquals(progressBefore, rejected.unlockedCount)
        assertEquals(seedsBefore, rejected.remainingSeeds)
        assertEquals(progressBefore, SaveManager.loadGardenProgress(context))
        assertEquals(seedsBefore, SaveManager.loadLifetimeSeeds(context))
    }

    @Test
    fun `stale repeated tap cannot purchase the same index twice`() {
        SaveManager.saveGardenProgress(context, 1)
        SaveManager.saveLifetimeSeeds(context, 50)

        val first = GardenPurchaseManager.purchaseNext(context, 1)
        val second = GardenPurchaseManager.purchaseNext(context, 1)

        assertTrue(first.purchased)
        assertFalse(second.purchased)
        assertEquals(GardenPurchaseStatus.NOT_NEXT_UNLOCK, second.status)
        assertEquals(2, SaveManager.loadGardenProgress(context))
        assertEquals(30, SaveManager.loadLifetimeSeeds(context))
    }

    @Test
    fun `insufficient balance changes neither side of the transaction`() {
        SaveManager.saveGardenProgress(context, 3)
        SaveManager.saveLifetimeSeeds(context, 7)

        val result = GardenPurchaseManager.purchaseNext(context, 3)

        assertEquals(GardenPurchaseStatus.INSUFFICIENT_SEEDS, result.status)
        assertEquals(3, result.unlockedCount)
        assertEquals(7, result.remainingSeeds)
        assertEquals(3, SaveManager.loadGardenProgress(context))
        assertEquals(7, SaveManager.loadLifetimeSeeds(context))
    }

    @Test
    fun `transaction reads persisted values instead of caller cache`() {
        SaveManager.saveGardenProgress(context, 4)
        SaveManager.saveLifetimeSeeds(context, 100)

        val staleIndexResult = GardenPurchaseManager.purchaseNext(context, 2)
        val canonicalResult = GardenPurchaseManager.purchaseNext(context, 4)

        assertEquals(GardenPurchaseStatus.NOT_NEXT_UNLOCK, staleIndexResult.status)
        assertTrue(canonicalResult.purchased)
        assertEquals(5, SaveManager.loadGardenProgress(context))
        assertEquals(60, SaveManager.loadLifetimeSeeds(context))
    }

    @Test
    fun `future schema compatibility namespace receives the transaction`() {
        SaveManager.useCompatibilityPreferences(SaveIntegrityManager.CURRENT_SCHEMA_VERSION)
        clearActivePreferences()
        SaveManager.saveGardenProgress(context, 1)
        SaveManager.saveLifetimeSeeds(context, 35)

        val result = GardenPurchaseManager.purchaseNext(context, 1)

        assertTrue(result.purchased)
        assertEquals(2, SaveManager.loadGardenProgress(context))
        assertEquals(15, SaveManager.loadLifetimeSeeds(context))
        val primary = context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
        assertEquals(1, primary.getInt("garden_unlocked", 1))
        assertEquals(0, primary.getInt("lifetime_seeds", 0))
    }

    @Test
    fun `compatibility overload rejects undercharge and oversized catalogue`() {
        SaveManager.saveGardenProgress(context, 1)
        SaveManager.saveLifetimeSeeds(context, 100)

        val undercharged = GardenPurchaseManager.purchaseNext(context, 1, 0, 9)
        val oversized = GardenPurchaseManager.purchaseNext(context, 1, 20, 99)

        assertEquals(GardenPurchaseStatus.INVALID_REQUEST, undercharged.status)
        assertEquals(GardenPurchaseStatus.INVALID_REQUEST, oversized.status)
        assertEquals(1, SaveManager.loadGardenProgress(context))
        assertEquals(100, SaveManager.loadLifetimeSeeds(context))
    }

    @Test
    fun `complete catalogue and invalid requests are rejected`() {
        SaveManager.saveGardenProgress(context, 9)
        SaveManager.saveLifetimeSeeds(context, 1_000)

        assertEquals(
            GardenPurchaseStatus.CATALOGUE_COMPLETE,
            GardenPurchaseManager.purchaseNext(context, 8).status
        )
        assertEquals(
            GardenPurchaseStatus.INVALID_REQUEST,
            GardenPurchaseManager.purchaseNext(context, -1).status
        )
        assertEquals(
            GardenPurchaseStatus.INVALID_REQUEST,
            GardenPurchaseManager.purchaseNext(context, 0).status
        )
    }

    private fun paidCatalogueBudget(): Int =
        (1 until GardenEconomy.catalogueSize).sumOf { index ->
            requireNotNull(GardenEconomy.seedCostForIndex(index))
        }

    private fun clearActivePreferences() {
        context.getSharedPreferences(
            SaveManager.activePrefsNameForTests,
            Context.MODE_PRIVATE
        ).edit().clear().commit()
    }
}
