package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ForestGardenHistoryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPreferences()
    }

    @After
    fun tearDown() {
        clearPreferences()
    }

    @Test
    fun `initial journal projection shows first plant grown and second as next`() {
        SaveManager.saveLifetimeSeeds(context, requireNotNull(GardenEconomy.seedCostForIndex(1)))

        val snapshot = ForestGardenHistoryComposer.snapshot(context)

        assertEquals(GardenEconomy.catalogueSize, snapshot.plants.size)
        assertEquals(1, snapshot.unlockedCount)
        assertEquals(20, snapshot.availableSeeds)
        assertEquals(ForestGardenPlantState.GROWN, snapshot.plants[0].state)
        assertEquals(ForestGardenPlantState.NEXT, snapshot.plants[1].state)
        assertTrue(snapshot.plants[1].affordableNow)
        assertEquals(GardenEconomy.seedCostForIndex(1), snapshot.nextPlant?.seedCost)
        assertEquals("Cactus", snapshot.nextPlant?.displayName)
        assertFalse(snapshot.complete)
        assertTrue(snapshot.plants.drop(2).all { it.state == ForestGardenPlantState.LOCKED })
    }

    @Test
    fun `next plant affordability changes exactly at its canonical seed cost`() {
        SaveManager.saveGardenProgress(context, 4)
        val nextCost = requireNotNull(GardenEconomy.seedCostForIndex(4))

        SaveManager.saveLifetimeSeeds(context, nextCost - 1)
        val short = ForestGardenHistoryComposer.snapshot(context)
        assertFalse(short.nextPlant!!.affordableNow)

        SaveManager.saveLifetimeSeeds(context, nextCost)
        val exact = ForestGardenHistoryComposer.snapshot(context)
        assertTrue(exact.nextPlant!!.affordableNow)
        assertEquals(nextCost, exact.nextPlant!!.seedCost)
    }

    @Test
    fun `complete catalogue has no next plant and marks every entry grown`() {
        SaveManager.saveGardenProgress(context, GardenEconomy.catalogueSize)

        val snapshot = ForestGardenHistoryComposer.snapshot(context)

        assertTrue(snapshot.complete)
        assertNull(snapshot.nextPlant)
        assertTrue(snapshot.plants.all { it.state == ForestGardenPlantState.GROWN })
        assertEquals(GardenEconomy.catalogueSize, snapshot.unlockedCount)
    }

    @Test
    fun `out of range stored progress and negative seeds are bounded for presentation`() {
        val preferences = context.getSharedPreferences(
            SaveManager.activePrefsNameForTests,
            Context.MODE_PRIVATE
        )
        preferences.edit()
            .putInt("garden_unlocked", Int.MAX_VALUE)
            .putInt("lifetime_seeds", -20)
            .commit()

        val snapshot = ForestGardenHistoryComposer.snapshot(context)

        assertEquals(GardenEconomy.catalogueSize, snapshot.unlockedCount)
        assertEquals(0, snapshot.availableSeeds)
        assertTrue(snapshot.complete)
        assertNull(snapshot.nextPlant)
    }

    @Test
    fun `garden plant projection rejects impossible affordability`() {
        assertThrows(IllegalArgumentException::class.java) {
            ForestGardenPlantMemory(
                index = 0,
                displayName = "Lily",
                seedCost = 15,
                state = ForestGardenPlantState.GROWN,
                affordableNow = true
            )
        }
    }

    private fun clearPreferences() {
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences(
            SaveManager.activePrefsNameForTests,
            Context.MODE_PRIVATE
        ).edit().clear().commit()
    }
}
