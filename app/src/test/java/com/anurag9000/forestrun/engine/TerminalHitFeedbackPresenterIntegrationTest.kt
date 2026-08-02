package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.ui.DialogueBubbleManager
import com.anurag9000.forestrun.ui.FlavorTextManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TerminalHitFeedbackPresenterIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DialogueBubbleManager.clear()
        FlavorTextManager.clear()
        DialogueBubbleManager.init(context)
        FlavorTextManager.init(context)
    }

    @After
    fun tearDown() {
        DialogueBubbleManager.clear()
        FlavorTextManager.clear()
    }

    @Test
    fun `presenter emits the canonical authored hit cue once`() {
        val input = TerminalHitPresentation(
            killerType = EntityType.WOLF,
            routeTier = PacifistRouteTier.MERCIFUL,
            playerX = 320f,
            playerY = 720f
        )
        val expected = RunFlavorPresentation.collisionCue(
            context = context,
            type = input.killerType,
            result = CollisionResult.HIT,
            routeTier = input.routeTier
        )

        AndroidTerminalHitFeedbackPresenter(context).present(input)

        assertEquals(listOf(expected.bubbleText), DialogueBubbleManager.activeTextsForTest())
        assertEquals(listOf(expected.flavorText), FlavorTextManager.activeTextsForTest())
        assertEquals(1, FlavorTextManager.activeCountForTest())
    }

    @Test
    fun `nonfinite player anchors cannot poison presentation queues`() {
        val presenter = AndroidTerminalHitFeedbackPresenter(context)

        presenter.present(
            TerminalHitPresentation(
                killerType = EntityType.CAT,
                routeTier = PacifistRouteTier.NONE,
                playerX = Float.NaN,
                playerY = 720f
            )
        )
        presenter.present(
            TerminalHitPresentation(
                killerType = EntityType.CAT,
                routeTier = PacifistRouteTier.NONE,
                playerX = 320f,
                playerY = Float.POSITIVE_INFINITY
            )
        )

        assertTrue(DialogueBubbleManager.activeTextsForTest().isEmpty())
        assertTrue(FlavorTextManager.activeTextsForTest().isEmpty())
    }
}
