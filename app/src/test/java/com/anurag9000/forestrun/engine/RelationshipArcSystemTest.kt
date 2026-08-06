package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.CostumeStyle
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
        assertEquals("Answered Trick", reward?.bondRitualLabel)
        assertTrue(reward?.bondRitualLine?.contains("answer", ignoreCase = true) == true)
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
        assertEquals("Meeting Run", RelationshipArcSystem.milestoneRewardFor(context, EntityType.DOG)?.bondRitualLabel)
        assertEquals("Known Shadow", RelationshipArcSystem.milestoneRewardFor(context, EntityType.OWL)?.bondRitualLabel)
        assertEquals("Held Line", RelationshipArcSystem.milestoneRewardFor(context, EntityType.EAGLE)?.bondRitualLabel)
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

        assertEquals(4, dialogue.size)
        assertTrue(dialogue.first().isNotBlank())
        assertTrue(dialogue.last().contains("home", ignoreCase = true) || dialogue.last().contains("soon", ignoreCase = true))
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
    fun `owl alert and pass lines deepen with repeated shadow history`() {
        repeat(2) { PersistentMemoryManager.recordHit(context, EntityType.OWL) }
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.OWL) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.OWL) }
        repeat(4) { PersistentMemoryManager.recordPass(context, EntityType.OWL) }

        val owlAlert = RelationshipArcSystem.encounterCueLine(
            context,
            EntityType.OWL,
            RelationshipArcSystem.EncounterCue.OWL_ALERT
        )
        val owlPass = RelationshipArcSystem.lineFor(context, EntityType.OWL, RelationshipArcSystem.Event.PASS)

        assertTrue(owlAlert.contains("shadow", ignoreCase = true) || owlAlert.contains("remembers", ignoreCase = true))
        assertTrue(
            owlPass.contains("night", ignoreCase = true) ||
                owlPass.contains("dark", ignoreCase = true) ||
                owlPass.contains("shape", ignoreCase = true)
        )
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
    fun `strong warm history makes live encounter lines more personal`() {
        repeat(6) { PersistentMemoryManager.recordEncounter(context, EntityType.CAT) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }
        repeat(5) { PersistentMemoryManager.recordPass(context, EntityType.CAT) }
        repeat(6) { PersistentMemoryManager.recordEncounter(context, EntityType.DOG) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.DOG) }
        repeat(5) { PersistentMemoryManager.recordPass(context, EntityType.DOG) }

        val catPass = RelationshipArcSystem.lineFor(context, EntityType.CAT, RelationshipArcSystem.Event.PASS)
        val catMercy = RelationshipArcSystem.encounterCueLine(
            context,
            EntityType.CAT,
            RelationshipArcSystem.EncounterCue.MERCY
        )
        val dogPass = RelationshipArcSystem.lineFor(context, EntityType.DOG, RelationshipArcSystem.Event.PASS)
        val dogGreeting = RelationshipArcSystem.encounterCueLine(
            context,
            EntityType.DOG,
            RelationshipArcSystem.EncounterCue.DOG_GREETING
        )

        assertTrue(
            catPass.contains("quiet", ignoreCase = true) ||
                catPass.contains("pace", ignoreCase = true)
        )
        assertTrue(
            catMercy.contains("quiet", ignoreCase = true) ||
                catMercy.contains("your step", ignoreCase = true) ||
                catMercy.contains("pace", ignoreCase = true) ||
                catMercy.contains("us", ignoreCase = true) ||
                catMercy.contains("friend", ignoreCase = true)
        )
        assertTrue(
            dogPass.contains("back", ignoreCase = true) ||
                dogPass.contains("with me", ignoreCase = true) ||
                dogPass.contains("friend", ignoreCase = true)
        )
        assertTrue(
            dogGreeting.contains("came back", ignoreCase = true) ||
                dogGreeting.contains("again", ignoreCase = true)
        )
    }

    @Test
    fun `cat repeated-friend lines become more personal after familiar passes`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.CAT) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }
        repeat(4) { PersistentMemoryManager.recordPass(context, EntityType.CAT) }

        val passLine = RelationshipArcSystem.lineFor(context, EntityType.CAT, RelationshipArcSystem.Event.PASS)
        val mercyCue = RelationshipArcSystem.encounterCueLine(
            context,
            EntityType.CAT,
            RelationshipArcSystem.EncounterCue.MERCY
        )

        assertTrue(passLine.contains("quiet", ignoreCase = true) || passLine.contains("pace", ignoreCase = true))
        assertTrue(
            mercyCue.contains("quiet", ignoreCase = true) ||
                mercyCue.contains("pace", ignoreCase = true) ||
                mercyCue.contains("us", ignoreCase = true)
        )
    }

    @Test
    fun `fox repeated-memory lines become more knowingly playful after familiar passes`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.FOX) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.FOX) }
        repeat(4) { PersistentMemoryManager.recordPass(context, EntityType.FOX) }

        val passLine = RelationshipArcSystem.lineFor(context, EntityType.FOX, RelationshipArcSystem.Event.PASS)
        val landingCue = RelationshipArcSystem.encounterCueLine(
            context,
            EntityType.FOX,
            RelationshipArcSystem.EncounterCue.FOX_LANDING
        )

        assertTrue(passLine.contains("remember", ignoreCase = true) || passLine.contains("trick", ignoreCase = true))
        assertTrue(
            landingCue.contains("remembered", ignoreCase = true) ||
                landingCue.contains("read", ignoreCase = true) ||
                landingCue.contains("catch", ignoreCase = true)
        )
    }

    @Test
    fun `strained bond chooses cautious trusted creature and surfaces sharper line`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.WOLF) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.WOLF) }
        repeat(3) { PersistentMemoryManager.recordHit(context, EntityType.WOLF) }

        val featured = RelationshipArcSystem.featuredStrainedBond(context)
        val line = RelationshipArcSystem.strainedBondLine(context, EntityType.WOLF)

        assertEquals(EntityType.WOLF, featured)
        assertTrue(
            line.contains("careful", ignoreCase = true) ||
                line.contains("break", ignoreCase = true) ||
                line.contains("fear", ignoreCase = true)
        )
        assertTrue(RelationshipArcSystem.isStrainedBond(context, EntityType.WOLF))
    }

    @Test
    fun `strained history differentiates disappointment from fear`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.DOG) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.DOG) }
        repeat(4) { PersistentMemoryManager.recordHit(context, EntityType.DOG) }
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.EAGLE) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.EAGLE) }
        repeat(4) { PersistentMemoryManager.recordHit(context, EntityType.EAGLE) }

        val dogThreat = RelationshipArcSystem.lineFor(context, EntityType.DOG, RelationshipArcSystem.Event.THREAT)
        val dogStrained = RelationshipArcSystem.strainedBondLine(context, EntityType.DOG)
        val eagleThreat = RelationshipArcSystem.lineFor(context, EntityType.EAGLE, RelationshipArcSystem.Event.THREAT)
        val eagleStrained = RelationshipArcSystem.strainedBondLine(context, EntityType.EAGLE)

        assertTrue(
            dogThreat.contains("hurts", ignoreCase = true) ||
                dogStrained.contains("hurt", ignoreCase = true) ||
                dogStrained.contains("let down", ignoreCase = true)
        )
        assertTrue(
            eagleThreat.contains("fear", ignoreCase = true) ||
                eagleStrained.contains("fear", ignoreCase = true)
        )
    }

    @Test
    fun `wolf repeated spare lines become more respectful after stand down history`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.WOLF) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.WOLF) }
        repeat(4) { PersistentMemoryManager.recordPass(context, EntityType.WOLF) }

        val spareLine = RelationshipArcSystem.lineFor(context, EntityType.WOLF, RelationshipArcSystem.Event.SPARE)
        val chargeCue = RelationshipArcSystem.encounterCueLine(
            context,
            EntityType.WOLF,
            RelationshipArcSystem.EncounterCue.WOLF_CHARGE
        )

        assertTrue(
            spareLine.contains("peace", ignoreCase = true) ||
                spareLine.contains("stand down", ignoreCase = true) ||
                spareLine.contains("warning", ignoreCase = true)
        )
        assertTrue(
            chargeCue.contains("hold", ignoreCase = true) ||
                chargeCue.contains("ground", ignoreCase = true) ||
                chargeCue.contains("ends", ignoreCase = true)
        )
    }
}