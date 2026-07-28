package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertEquals
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
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
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
}
