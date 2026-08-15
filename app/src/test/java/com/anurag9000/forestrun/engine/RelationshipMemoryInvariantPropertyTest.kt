package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RelationshipMemoryInvariantPropertyTest {
    private lateinit var context: Context

    private val trackedTypes = listOf(
        EntityType.CAT,
        EntityType.FOX,
        EntityType.WOLF,
        EntityType.DOG,
        EntityType.OWL,
        EntityType.EAGLE
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        clearPrimarySave()
    }

    @Test
    fun `earned milestone unlocks and rewards survive later hostile history for every tracked bond`() {
        trackedTypes.forEach { type ->
            clearPrimarySave()
            repeat(5) { PersistentMemoryManager.recordEncounter(context, type) }
            repeat(3) { PersistentMemoryManager.recordSpare(context, type) }

            assertEquals(RelationshipStage.MILESTONE, RelationshipArcSystem.stageFor(context, type))
            val earnedReward = RelationshipArcSystem.milestoneRewardFor(context, type)
            assertNotNull("missing earned reward for $type", earnedReward)
            assertTrue(RelationshipArcSystem.hasUnlockedMilestone(context, type))

            repeat(64) { PersistentMemoryManager.recordHit(context, type) }

            assertTrue("milestone unlock regressed for $type", RelationshipArcSystem.hasUnlockedMilestone(context, type))
            assertEquals("milestone reward regressed for $type", earnedReward, RelationshipArcSystem.milestoneRewardFor(context, type))
            assertTrue(type in SaveManager.loadUnlockedRelationshipMilestones(context))
        }
    }

    @Test
    fun `history unlocks are additive even when later outcomes reverse the live streak`() {
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }
        val kindnessMark = "history_kindness_cat"
        assertTrue(kindnessMark in PersistentMemoryManager.unlockedHistoryMarks(context))

        repeat(3) { PersistentMemoryManager.recordHit(context, EntityType.CAT) }
        val marksAfterStrain = PersistentMemoryManager.unlockedHistoryMarks(context)

        assertTrue(kindnessMark in marksAfterStrain)
        assertTrue("history_tender_cat" in marksAfterStrain)
        assertEquals(0, PersistentMemoryManager.getKindnessStreak(context, EntityType.CAT))
        assertEquals(3, PersistentMemoryManager.getTenderStreak(context, EntityType.CAT))
    }

    @Test
    fun `kindness and tender streaks remain mutually exclusive across deterministic mixed history`() {
        val random = Random(0xF0E57)
        var expectedKindness = 0
        var expectedTender = 0

        repeat(512) { step ->
            if (random.nextBoolean()) {
                PersistentMemoryManager.recordSpare(context, EntityType.DOG)
                expectedKindness += 1
                expectedTender = 0
            } else {
                PersistentMemoryManager.recordHit(context, EntityType.DOG)
                expectedTender += 1
                expectedKindness = 0
            }

            val actualKindness = PersistentMemoryManager.getKindnessStreak(context, EntityType.DOG)
            val actualTender = PersistentMemoryManager.getTenderStreak(context, EntityType.DOG)
            assertEquals("kindness streak mismatch at step $step", expectedKindness, actualKindness)
            assertEquals("tender streak mismatch at step $step", expectedTender, actualTender)
            assertTrue(
                "opposite streaks were simultaneously active at step $step",
                actualKindness == 0 || actualTender == 0
            )
        }
    }

    private fun clearPrimarySave() {
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
