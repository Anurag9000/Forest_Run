package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.CostumeStyle
import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.DOG) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.DOG) }
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.OWL) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.OWL) }
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.EAGLE) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.EAGLE) }
        SaveManager.saveBestDistance(context, 1_650f)

        val newUnlocks = CostumeManager.refreshUnlocks(context)
        val available = CostumeManager.availableCostumes(context)

        assertEquals(
            listOf(
                CostumeStyle.FLOWER_CROWN,
                CostumeStyle.VINE_SCARF,
                CostumeStyle.MOON_CAPE,
                CostumeStyle.BELL_CHARM,
                CostumeStyle.LANTERN_PIN,
                CostumeStyle.SKY_SASH,
                CostumeStyle.BLOOM_RIBBON
            ),
            newUnlocks.map { it.style }
        )
        assertTrue(available.containsAll(CostumeStyle.entries))
        assertTrue(newUnlocks.first().line.isNotBlank())
    }

    @Test
    fun `equip rejects locked costumes and stores unlocked selections`() {
        assertFalse(CostumeManager.equip(context, CostumeStyle.MOON_CAPE))
        SaveManager.saveUnlockedCostumes(context, setOf(CostumeStyle.MOON_CAPE))

        assertTrue(CostumeManager.equip(context, CostumeStyle.MOON_CAPE))
        assertEquals(CostumeStyle.MOON_CAPE, CostumeManager.activeCostume(context))
    }

    @Test
    fun `relationship milestone can unlock matching costume`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.CAT) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }

        val newUnlocks = CostumeManager.refreshUnlocks(context)

        assertTrue(newUnlocks.any { it.style == CostumeStyle.FLOWER_CROWN })
        assertTrue(CostumeManager.availableCostumes(context).contains(CostumeStyle.FLOWER_CROWN))
    }

    @Test
    fun `dog owl and eagle milestones unlock their wardrobe rewards`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.DOG) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.DOG) }
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.OWL) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.OWL) }
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.EAGLE) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.EAGLE) }

        val newUnlocks = CostumeManager.refreshUnlocks(context)

        assertTrue(newUnlocks.any { it.style == CostumeStyle.BELL_CHARM })
        assertTrue(newUnlocks.any { it.style == CostumeStyle.LANTERN_PIN })
        assertTrue(newUnlocks.any { it.style == CostumeStyle.SKY_SASH })
    }

    @Test
    fun `featured costume presentation follows newest unlock and equipped carry-over`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.DOG) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.DOG) }

        CostumeManager.refreshUnlocks(context)
        val unlockedPresentation = CostumeManager.featuredPresentation(context)

        assertNotNull(unlockedPresentation)
        assertEquals(CostumeStyle.BELL_CHARM, unlockedPresentation?.style)
        assertEquals("Glad Welcome", unlockedPresentation?.signLabel)
        assertTrue(unlockedPresentation?.signLine.orEmpty().contains("welcome", ignoreCase = true))

        assertTrue(CostumeManager.equip(context, CostumeStyle.BELL_CHARM))
        val activePresentation = CostumeManager.activePresentation(context)

        assertEquals(CostumeStyle.BELL_CHARM, activePresentation?.style)
        assertEquals("Bell Charm", activePresentation?.activeLabel)
        assertTrue(activePresentation?.activeLine.orEmpty().contains("dog", ignoreCase = true))
        assertEquals(CostumeStyle.BELL_CHARM, CostumeManager.featuredPresentation(context)?.style)
    }
}
