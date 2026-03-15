package com.yourname.forest_run.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.entities.CostumeStyle
import com.yourname.forest_run.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CostumeManagerTest {

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
    fun `refresh unlocks costumes from relationship progress`() {
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.FOX) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.WOLF) }
        SaveManager.saveBestDistance(context, 1_650f)

        val newUnlocks = CostumeManager.refreshUnlocks(context)
        val available = CostumeManager.availableCostumes(context)

        assertEquals(
            listOf(
                CostumeStyle.FLOWER_CROWN,
                CostumeStyle.VINE_SCARF,
                CostumeStyle.MOON_CAPE,
                CostumeStyle.BLOOM_RIBBON
            ),
            newUnlocks
        )
        assertTrue(available.containsAll(CostumeStyle.entries))
    }

    @Test
    fun `equip rejects locked costumes and stores unlocked selections`() {
        assertFalse(CostumeManager.equip(context, CostumeStyle.MOON_CAPE))
        SaveManager.saveUnlockedCostumes(context, setOf(CostumeStyle.MOON_CAPE))

        assertTrue(CostumeManager.equip(context, CostumeStyle.MOON_CAPE))
        assertEquals(CostumeStyle.MOON_CAPE, CostumeManager.activeCostume(context))
    }
}
