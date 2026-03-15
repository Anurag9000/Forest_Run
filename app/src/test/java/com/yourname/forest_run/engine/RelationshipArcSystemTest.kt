package com.yourname.forest_run.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.entities.CostumeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RelationshipArcSystemTest {

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
    fun `tracked encounters progress cat into recognition and trust`() {
        PersistentMemoryManager.recordEncounter(context, EntityType.CAT)
        PersistentMemoryManager.recordEncounter(context, EntityType.CAT)

        assertEquals(RelationshipStage.RECOGNITION, RelationshipArcSystem.stageFor(context, EntityType.CAT))

        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }

        assertEquals(RelationshipStage.TRUST, RelationshipArcSystem.stageFor(context, EntityType.CAT))
    }

    @Test
    fun `wolf can reach milestone bond through encounters and spares`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.WOLF) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.WOLF) }

        assertEquals(RelationshipStage.MILESTONE, RelationshipArcSystem.stageFor(context, EntityType.WOLF))
    }

    @Test
    fun `strongest relationship label returns formatted bond`() {
        repeat(3) { PersistentMemoryManager.recordEncounter(context, EntityType.FOX) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.FOX) }

        val label = RelationshipArcSystem.strongestRelationshipLabel(context)

        assertTrue(label!!.startsWith("Fox"))
        assertTrue(label.contains("Trust") || label.contains("Bond"))
    }

    @Test
    fun `preferred garden visitor requires trust or better`() {
        repeat(3) { PersistentMemoryManager.recordEncounter(context, EntityType.CAT) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }

        assertEquals(EntityType.CAT, RelationshipArcSystem.preferredGardenVisitor(context))
        assertNotNull(RelationshipArcSystem.creatureThought(context, EntityType.CAT))
    }

    @Test
    fun `warm trust bond increases encounter generosity`() {
        repeat(3) { PersistentMemoryManager.recordEncounter(context, EntityType.CAT) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }

        val tuning = RelationshipArcSystem.encounterTuning(context, EntityType.CAT)

        assertTrue(tuning.passBonusPoints > 0)
        assertTrue(tuning.mercyPaddingBonusPx > 0f)
    }

    @Test
    fun `dog bond can raise buddy encounter chance`() {
        repeat(3) { PersistentMemoryManager.recordEncounter(context, EntityType.DOG) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.DOG) }

        assertTrue(RelationshipArcSystem.dogBuddyChance(context) > 0.20f)
    }

    @Test
    fun `milestone bond unlocks persistent relationship reward`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.FOX) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.FOX) }

        val reward = RelationshipArcSystem.milestoneRewardFor(context, EntityType.FOX)

        assertNotNull(reward)
        assertEquals("Trail Ribbon", reward?.label)
        assertEquals(CostumeStyle.VINE_SCARF, reward?.costumeReward)
        assertTrue(RelationshipArcSystem.hasUnlockedMilestone(context, EntityType.FOX))
    }
}
