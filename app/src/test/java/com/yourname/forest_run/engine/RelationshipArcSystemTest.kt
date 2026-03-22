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
        assertEquals("Quick Path", reward?.homePresenceLabel)
        assertTrue(reward?.homePresenceLine?.contains("path", ignoreCase = true) == true)
        assertEquals("Trail kept", reward?.milestoneBubbleText)
        assertTrue(reward?.gardenReactionLine?.contains("line", ignoreCase = true) == true)
        assertTrue(RelationshipArcSystem.hasUnlockedMilestone(context, EntityType.FOX))
    }

    @Test
    fun `dog owl and eagle milestone bonds unlock matching cosmetics`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.DOG) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.DOG) }
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.OWL) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.OWL) }
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.EAGLE) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.EAGLE) }

        assertEquals(CostumeStyle.BELL_CHARM, RelationshipArcSystem.milestoneRewardFor(context, EntityType.DOG)?.costumeReward)
        assertEquals(CostumeStyle.LANTERN_PIN, RelationshipArcSystem.milestoneRewardFor(context, EntityType.OWL)?.costumeReward)
        assertEquals(CostumeStyle.SKY_SASH, RelationshipArcSystem.milestoneRewardFor(context, EntityType.EAGLE)?.costumeReward)
        assertEquals("Open Gate", RelationshipArcSystem.milestoneRewardFor(context, EntityType.DOG)?.homePresenceLabel)
        assertEquals("Night Watch", RelationshipArcSystem.milestoneRewardFor(context, EntityType.OWL)?.homePresenceLabel)
        assertEquals("High Thread", RelationshipArcSystem.milestoneRewardFor(context, EntityType.EAGLE)?.homePresenceLabel)
        assertEquals("Gate open", RelationshipArcSystem.milestoneRewardFor(context, EntityType.DOG)?.milestoneBubbleText)
        assertEquals("Lantern Owl", RelationshipArcSystem.milestoneRewardFor(context, EntityType.OWL)?.gardenReactionTitle)
        assertEquals("Sky held", RelationshipArcSystem.milestoneRewardFor(context, EntityType.EAGLE)?.milestoneBubbleText)
    }

    @Test
    fun `encounter cue lines deepen with warm or cautious history`() {
        repeat(3) { PersistentMemoryManager.recordEncounter(context, EntityType.CAT) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }
        repeat(2) { PersistentMemoryManager.recordEncounter(context, EntityType.WOLF) }
        repeat(2) { PersistentMemoryManager.recordHit(context, EntityType.WOLF) }

        val catMercy = RelationshipArcSystem.encounterCueLine(
            context,
            EntityType.CAT,
            RelationshipArcSystem.EncounterCue.MERCY
        )
        val wolfCharge = RelationshipArcSystem.encounterCueLine(
            context,
            EntityType.WOLF,
            RelationshipArcSystem.EncounterCue.WOLF_CHARGE
        )

        assertTrue(catMercy.contains("friend", ignoreCase = true) || catMercy.contains("know", ignoreCase = true))
        assertTrue(wolfCharge.contains("remember", ignoreCase = true))
    }

    @Test
    fun `dog buddy dialogue and duration improve with warmer bond`() {
        repeat(3) { PersistentMemoryManager.recordEncounter(context, EntityType.DOG) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.DOG) }

        val dialogue = RelationshipArcSystem.dogBuddyDialogue(context)
        val durationBonus = RelationshipArcSystem.dogBuddyDurationBonusSec(context)

        assertEquals(3, dialogue.size)
        assertTrue(dialogue.first().isNotBlank())
        assertTrue(durationBonus > 0f)
    }

    @Test
    fun `owl and eagle cues reflect relationship history`() {
        repeat(3) { PersistentMemoryManager.recordEncounter(context, EntityType.OWL) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.OWL) }
        repeat(2) { PersistentMemoryManager.recordEncounter(context, EntityType.EAGLE) }
        repeat(2) { PersistentMemoryManager.recordHit(context, EntityType.EAGLE) }

        val owlAlert = RelationshipArcSystem.encounterCueLine(
            context,
            EntityType.OWL,
            RelationshipArcSystem.EncounterCue.OWL_ALERT
        )
        val eagleLock = RelationshipArcSystem.encounterCueLine(
            context,
            EntityType.EAGLE,
            RelationshipArcSystem.EncounterCue.EAGLE_LOCK
        )

        assertTrue(owlAlert.contains("prey", ignoreCase = true) || owlAlert.contains("timing", ignoreCase = true))
        assertTrue(eagleLock.contains("marked", ignoreCase = true))
    }

    @Test
    fun `repeat friend chooses the warmest trusted bond`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.DOG) }
        PersistentMemoryManager.recordSpare(context, EntityType.DOG)
        repeat(3) { PersistentMemoryManager.recordEncounter(context, EntityType.CAT) }
        PersistentMemoryManager.recordSpare(context, EntityType.CAT)

        val featured = RelationshipArcSystem.featuredRepeatFriend(context)
        val line = RelationshipArcSystem.repeatFriendLine(context, EntityType.DOG)

        assertEquals(EntityType.DOG, featured)
        assertTrue(line.contains("habit", ignoreCase = true) || line.contains("belong", ignoreCase = true))
    }

    @Test
    fun `strained bond chooses cautious trusted creature and surfaces sharper line`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.WOLF) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.WOLF) }
        repeat(3) { PersistentMemoryManager.recordHit(context, EntityType.WOLF) }

        val featured = RelationshipArcSystem.featuredStrainedBond(context)
        val line = RelationshipArcSystem.strainedBondLine(context, EntityType.WOLF)

        assertEquals(EntityType.WOLF, featured)
        assertTrue(line.contains("careful", ignoreCase = true) || line.contains("break", ignoreCase = true))
        assertTrue(RelationshipArcSystem.isStrainedBond(context, EntityType.WOLF))
    }
}
