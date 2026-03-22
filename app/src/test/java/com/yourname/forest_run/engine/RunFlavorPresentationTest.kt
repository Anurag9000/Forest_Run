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
        assertTrue(cue.flavorText.contains("GRRR", ignoreCase = true) || cue.flavorText.contains("remember", ignoreCase = true))
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
}
