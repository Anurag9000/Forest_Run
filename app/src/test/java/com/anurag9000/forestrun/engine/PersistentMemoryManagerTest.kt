package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersistentMemoryManagerTest {

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
        SaveManager.usePrimaryPreferences()
    }

    @Test
    fun `repeated history snapshot resolves unlock marks and featured unlock`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.WOLF) }
        repeat(3) { PersistentMemoryManager.recordHit(context, EntityType.WOLF) }
        repeat(4) { PersistentMemoryManager.recordPass(context, EntityType.CACTUS) }
        repeat(2) { PersistentMemoryManager.recordBiomeFriendship(context, Biome.MEADOW) }

        val snapshot = PersistentMemoryManager.repeatedHistorySnapshot(context)

        assertEquals(EntityType.WOLF, snapshot.featuredRepeatKiller)
        assertEquals(EntityType.CACTUS, snapshot.featuredCleanPass?.type)
        assertEquals(Biome.MEADOW, snapshot.featuredPeaceBiome?.biome)
        assertTrue(snapshot.unlockedMarks.contains("history_repeat_killer_wolf"))
        assertTrue(snapshot.unlockedMarks.contains("history_clean_pass_cactus"))
        assertTrue(snapshot.unlockedMarks.contains("history_peace_meadow"))
        assertEquals("Same Shadow", snapshot.featuredUnlock?.label)
    }

    @Test
    fun `nonpositive selector thresholds cannot feature untouched history`() {
        assertTrue(PersistentMemoryManager.peacefulBiomes(context, Int.MIN_VALUE).isEmpty())
        assertNull(PersistentMemoryManager.featuredPeaceBiome(context, 0))
        assertNull(PersistentMemoryManager.featuredWarmCreature(context, Int.MIN_VALUE))
        assertNull(PersistentMemoryManager.featuredTenderCreature(context, 0))
        assertNull(PersistentMemoryManager.featuredRepeatKiller(context, -100))
        assertNull(
            PersistentMemoryManager.featuredCleanPass(
                context = context,
                minimumPasses = Int.MIN_VALUE
            )
        )
    }

    @Test
    fun `malformed minima still admit only real one-event evidence`() {
        PersistentMemoryManager.recordSpare(context, EntityType.CAT)
        PersistentMemoryManager.recordHit(context, EntityType.WOLF)
        PersistentMemoryManager.recordPass(context, EntityType.CACTUS)
        PersistentMemoryManager.recordBiomeFriendship(context, Biome.MEADOW)

        assertEquals(
            listOf(Biome.MEADOW),
            PersistentMemoryManager.peacefulBiomes(context, -1).map { it.biome }
        )
        assertEquals(
            EntityType.CAT,
            PersistentMemoryManager.featuredWarmCreature(context, -1)
        )
        assertEquals(
            EntityType.WOLF,
            PersistentMemoryManager.featuredTenderCreature(context, -1)
        )
        assertEquals(
            EntityType.WOLF,
            PersistentMemoryManager.featuredRepeatKiller(context, -1)
        )
        assertEquals(
            EntityType.CACTUS,
            PersistentMemoryManager.featuredCleanPass(
                context = context,
                minimumPasses = -1
            )?.type
        )
    }

    @Test
    fun `selector ties resolve deterministically by catalogue order`() {
        PersistentMemoryManager.recordSpare(context, EntityType.CAT)
        PersistentMemoryManager.recordSpare(context, EntityType.DOG)
        PersistentMemoryManager.recordHit(context, EntityType.FOX)
        PersistentMemoryManager.recordHit(context, EntityType.WOLF)

        assertEquals(
            EntityType.DOG,
            PersistentMemoryManager.featuredWarmCreature(context, 1)
        )
        assertEquals(
            EntityType.FOX,
            PersistentMemoryManager.featuredTenderCreature(context, 1)
        )
    }
}
