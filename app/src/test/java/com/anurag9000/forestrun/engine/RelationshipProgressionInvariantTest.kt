package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
