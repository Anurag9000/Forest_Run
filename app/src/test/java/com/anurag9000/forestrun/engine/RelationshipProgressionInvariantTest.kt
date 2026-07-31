package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RelationshipProgressionInvariantTest {
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
    fun `familiarity without positive outcomes stops at recognition`() {
        repeat(30) {
            PersistentMemoryManager.recordEncounter(context, EntityType.CAT)
        }

        assertEquals(
            RelationshipStage.RECOGNITION,
            RelationshipArcSystem.stageFor(context, EntityType.CAT)
        )
        assertNull(RelationshipArcSystem.preferredGardenVisitor(context))
        assertNull(RelationshipArcSystem.milestoneRewardFor(context, EntityType.CAT))
    }

    @Test
    fun `repeated hits cannot manufacture trust or a bond`() {
        repeat(12) {
            PersistentMemoryManager.recordEncounter(context, EntityType.WOLF)
            PersistentMemoryManager.recordHit(context, EntityType.WOLF)
        }

        assertEquals(
            RelationshipStage.RECOGNITION,
            RelationshipArcSystem.stageFor(context, EntityType.WOLF)
        )
        assertNull(RelationshipArcSystem.preferredGardenVisitor(context))
        assertNull(RelationshipArcSystem.milestoneRewardFor(context, EntityType.WOLF))
    }

    @Test
    fun `clean passes earn trust and refresh the saved stage immediately`() {
        repeat(3) {
            PersistentMemoryManager.recordEncounter(context, EntityType.CAT)
            PersistentMemoryManager.recordPass(context, EntityType.CAT)
        }

        assertEquals(
            RelationshipStage.TRUST,
            SaveManager.loadRelationshipStage(context, EntityType.CAT)
        )
        assertEquals(
            RelationshipStage.TRUST,
            RelationshipArcSystem.stageFor(context, EntityType.CAT)
        )
        assertEquals(EntityType.CAT, RelationshipArcSystem.preferredGardenVisitor(context))
    }

    @Test
    fun `sustained clean outcomes can earn a milestone without spares`() {
        repeat(7) {
            PersistentMemoryManager.recordEncounter(context, EntityType.CAT)
            PersistentMemoryManager.recordPass(context, EntityType.CAT)
        }

        assertEquals(
            RelationshipStage.MILESTONE,
            RelationshipArcSystem.stageFor(context, EntityType.CAT)
        )
        assertEquals(
            "Napping Patch",
            RelationshipArcSystem.milestoneRewardFor(context, EntityType.CAT)?.label
        )
    }

    @Test
    fun `a hit delays but does not erase later earned trust`() {
        repeat(2) {
            PersistentMemoryManager.recordEncounter(context, EntityType.FOX)
            PersistentMemoryManager.recordHit(context, EntityType.FOX)
        }
        repeat(6) {
            PersistentMemoryManager.recordEncounter(context, EntityType.FOX)
            PersistentMemoryManager.recordPass(context, EntityType.FOX)
        }

        assertEquals(
            RelationshipStage.TRUST,
            RelationshipArcSystem.stageFor(context, EntityType.FOX)
        )
    }

    @Test
    fun `saturated positive history cannot wrap milestone progression backwards`() {
        writeRelationshipCounters(
            type = EntityType.CAT,
            encounters = Int.MAX_VALUE,
            cleanPasses = Int.MAX_VALUE,
            spared = Int.MAX_VALUE,
            hits = Int.MAX_VALUE
        )

        assertEquals(
            RelationshipStage.MILESTONE,
            RelationshipArcSystem.refreshStage(context, EntityType.CAT)
        )
    }

    @Test
    fun `saturated positive affinity remains stronger than a modest bond`() {
        writeRelationshipCounters(
            type = EntityType.CAT,
            encounters = Int.MAX_VALUE,
            cleanPasses = Int.MAX_VALUE,
            spared = Int.MAX_VALUE,
            hits = 0
        )
        writeRelationshipCounters(
            type = EntityType.DOG,
            encounters = 10,
            cleanPasses = 10,
            spared = 5,
            hits = 0
        )
        SaveManager.saveRelationshipStage(context, EntityType.CAT, RelationshipStage.MILESTONE)
        SaveManager.saveRelationshipStage(context, EntityType.DOG, RelationshipStage.MILESTONE)

        assertEquals(
            EntityType.CAT,
            RelationshipArcSystem.strongestRelationship(context)?.first
        )
    }

    @Test
    fun `saturated repeated strain remains severe instead of wrapping to wary`() {
        writeRelationshipCounters(
            type = EntityType.CAT,
            encounters = 10,
            cleanPasses = 0,
            spared = 0,
            hits = Int.MAX_VALUE,
            tenderStreak = Int.MAX_VALUE
        )
        SaveManager.saveRelationshipStage(context, EntityType.CAT, RelationshipStage.MILESTONE)

        val line = RelationshipArcSystem.strainedBondLine(context, EntityType.CAT)

        assertTrue(line.contains("disappointed", ignoreCase = true))
    }

    private fun writeRelationshipCounters(
        type: EntityType,
        encounters: Int,
        cleanPasses: Int,
        spared: Int,
        hits: Int,
        kindnessStreak: Int = 0,
        tenderStreak: Int = 0
    ) {
        val suffix = type.name.lowercase()
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("encounter_$suffix", encounters)
            .putInt("clean_pass_$suffix", cleanPasses)
            .putInt("spared_$suffix", spared)
            .putInt("hit_$suffix", hits)
            .putInt("kindness_streak_$suffix", kindnessStreak)
            .putInt("tender_streak_$suffix", tenderStreak)
            .commit()
    }
}
