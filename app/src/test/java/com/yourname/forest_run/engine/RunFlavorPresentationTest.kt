package com.yourname.forest_run.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RunFlavorPresentationTest {

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
    fun `repeat killer collision cue escalates into again messaging`() {
        repeat(2) { PersistentMemoryManager.recordHit(context, EntityType.OWL) }

        val cue = RunFlavorPresentation.collisionCue(
            context = context,
            type = EntityType.OWL,
            result = CollisionResult.HIT,
            routeTier = PacifistRouteTier.NONE
        )

        assertEquals("Again?", cue.bubbleText)
        assertTrue(cue.flavorText.contains("shadow", ignoreCase = true))
    }

    @Test
    fun `tracked relationship collision cue falls back to relationship warning text`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.WOLF) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.WOLF) }
        PersistentMemoryManager.recordHit(context, EntityType.WOLF)

        val cue = RunFlavorPresentation.collisionCue(
            context = context,
            type = EntityType.WOLF,
            result = CollisionResult.STUMBLE,
            routeTier = PacifistRouteTier.NONE
        )

        assertEquals("Too close.", cue.bubbleText)
        assertTrue(
            cue.flavorText.contains("GRRR", ignoreCase = true) ||
                cue.flavorText.contains("remember", ignoreCase = true) ||
                cue.flavorText.contains("calm", ignoreCase = true)
        )
    }

    @Test
    fun `milestone cue changes with route tier and high score`() {
        val peacefulCue = RunFlavorPresentation.milestoneCue(
            context = context,
            score = 2_000,
            routeTier = PacifistRouteTier.PEACEFUL,
            isNewHighScore = false
        )
        val highScoreCue = RunFlavorPresentation.milestoneCue(
            context = context,
            score = 1_000,
            routeTier = PacifistRouteTier.NONE,
            isNewHighScore = true
        )

        assertEquals("Peace held", peacefulCue.bubbleText)
        assertEquals("New best", highScoreCue.bubbleText)
        assertTrue(highScoreCue.flavorText.contains("forest", ignoreCase = true))
    }

    @Test
    fun `milestone cue can surface shared world opinion`() {
        SaveManager.saveForestMoodState(
            context,
            ForestMoodState(currentMood = ForestMood.GENTLE, moodStreak = 3, totalRuns = 3, gentleRuns = 3)
        )

        val cue = RunFlavorPresentation.milestoneCue(
            context = context,
            score = 1_000,
            routeTier = PacifistRouteTier.NONE,
            isNewHighScore = false
        )

        assertEquals("World softens", cue.bubbleText)
        assertTrue(cue.flavorText.contains("forest", ignoreCase = true) || cue.flavorText.contains("gently", ignoreCase = true))
    }

    @Test
    fun `milestone bond can override generic milestone cue with relationship reaction`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.OWL) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.OWL) }

        val cue = RunFlavorPresentation.milestoneCue(
            context = context,
            score = 2_000,
            routeTier = PacifistRouteTier.NONE,
            isNewHighScore = false
        )

        assertEquals("Night kept", cue.bubbleText)
        assertTrue(cue.flavorText.contains("owl", ignoreCase = true) || cue.flavorText.contains("stayed", ignoreCase = true))
    }

    @Test
    fun `mercy cue picks entity-aware flavor during ordinary play`() {
        repeat(2) { PersistentMemoryManager.recordHit(context, EntityType.HEDGEHOG) }

        val cue = RunFlavorPresentation.mercyCue(
            context = context,
            type = EntityType.HEDGEHOG,
            mercyHearts = 1,
            kindnessChain = 0,
            routeTier = PacifistRouteTier.NONE
        )

        assertTrue(cue.flavorText.contains("thorns", ignoreCase = true) || cue.flavorText.contains("hop", ignoreCase = true))
    }

    @Test
    fun `pass cue surfaces tracked and ordinary entity flavor in normal runs`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.DOG) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.DOG) }

        val dogCue = RunFlavorPresentation.passCue(
            context = context,
            type = EntityType.DOG,
            routeTier = PacifistRouteTier.KIND
        )
        val lilyCue = RunFlavorPresentation.passCue(
            context = context,
            type = EntityType.LILY_OF_VALLEY,
            routeTier = PacifistRouteTier.NONE
        )

        assertTrue(dogCue.flavorText.contains("friend", ignoreCase = true) || dogCue.flavorText.contains("home", ignoreCase = true))
        assertTrue(lilyCue.flavorText.contains("glow", ignoreCase = true) || lilyCue.flavorText.contains("above", ignoreCase = true))
    }

    @Test
    fun `pass bubble texts broaden ordinary and warm-bond pass messaging`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.DOG) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.DOG) }

        val dogOptions = RunFlavorPresentation.passBubbleTexts(
            context = context,
            type = EntityType.DOG,
            routeTier = PacifistRouteTier.KIND
        )
        val bambooOptions = RunFlavorPresentation.passBubbleTexts(
            context = context,
            type = EntityType.BAMBOO,
            routeTier = PacifistRouteTier.NONE
        )

        assertTrue(dogOptions.contains("Still with you"))
        assertTrue(dogOptions.contains("Known step"))
        assertTrue(bambooOptions.contains("Held the line"))
        assertTrue(bambooOptions.contains("Stayed exact"))
    }

    @Test
    fun `ordinary progress cue varies with progress kind and route tier`() {
        val mercyCue = RunFlavorPresentation.ordinaryProgressCue(
            progressKind = "mercy",
            currentValue = 4,
            routeTier = PacifistRouteTier.MERCIFUL
        )
        val kindnessCue = RunFlavorPresentation.ordinaryProgressCue(
            progressKind = "kindness",
            currentValue = 5,
            routeTier = PacifistRouteTier.KIND
        )
        val cleanCue = RunFlavorPresentation.ordinaryProgressCue(
            progressKind = "clean",
            currentValue = 12,
            routeTier = PacifistRouteTier.PEACEFUL
        )

        assertEquals("Mercy rises", mercyCue.bubbleText)
        assertTrue(mercyCue.flavorText.contains("leave room", ignoreCase = true) || mercyCue.flavorText.contains("forest", ignoreCase = true))
        assertEquals("Kindness gathers", kindnessCue.bubbleText)
        assertTrue(kindnessCue.flavorText.contains("gentleness", ignoreCase = true) || kindnessCue.flavorText.contains("pace", ignoreCase = true))
        assertEquals("Peace steadies", cleanCue.bubbleText)
        assertTrue(cleanCue.flavorSize >= 28f)
    }
}
