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
class NonTerminalCollisionFeedbackPresenterIntegrationTest {

    private lateinit var context: Context
    private lateinit var presenter: AndroidNonTerminalCollisionFeedbackPresenter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        presenter = AndroidNonTerminalCollisionFeedbackPresenter(context)
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
    fun `stumble presenter emits canonical authored cue once`() {
        val input = StumbleCollisionOutcome(
            killerType = EntityType.WOLF,
            routeTier = PacifistRouteTier.MERCIFUL,
            playerX = 320f,
            playerY = 720f,
            dominantColor = 0x11223344,
            persistEncounter = true
        )
        val expected = RunFlavorPresentation.collisionCue(
            context = context,
            type = input.killerType,
            result = CollisionResult.STUMBLE,
            routeTier = input.routeTier
        )

        presenter.presentStumble(input)

        assertEquals(listOf(expected.bubbleText), DialogueBubbleManager.activeTextsForTest())
        assertEquals(listOf(expected.flavorText), FlavorTextManager.activeTextsForTest())
        assertEquals(listOf(1.0f), FlavorTextManager.activeLifetimesForTest())
    }

    @Test
    fun `mercy presenter emits canonical authored cue once`() {
        val input = MercyMissCollisionOutcome(
            entityType = EntityType.EAGLE,
            routeTier = PacifistRouteTier.PEACEFUL,
            mercyHearts = 6,
            kindnessChain = 8,
            playerX = 300f,
            playerY = 700f
        )
        val expected = RunFlavorPresentation.mercyCue(
            context = context,
            type = input.entityType,
            mercyHearts = input.mercyHearts,
            kindnessChain = input.kindnessChain,
            routeTier = input.routeTier
        )

        presenter.presentMercyMiss(input)

        assertEquals(listOf(expected.bubbleText), DialogueBubbleManager.activeTextsForTest())
        assertEquals(listOf(expected.flavorText), FlavorTextManager.activeTextsForTest())
        assertEquals(listOf(1.15f), FlavorTextManager.activeLifetimesForTest())
    }

    @Test
    fun `nonfinite anchors cannot poison either presentation queue`() {
        presenter.presentStumble(
            StumbleCollisionOutcome(
                killerType = EntityType.CAT,
                routeTier = PacifistRouteTier.NONE,
                playerX = Float.NaN,
                playerY = 720f,
                dominantColor = 0,
                persistEncounter = false
            )
        )
        presenter.presentMercyMiss(
            MercyMissCollisionOutcome(
                entityType = EntityType.CAT,
                routeTier = PacifistRouteTier.NONE,
                mercyHearts = 0,
                kindnessChain = 0,
                playerX = 320f,
                playerY = Float.POSITIVE_INFINITY
            )
        )

        assertTrue(DialogueBubbleManager.activeTextsForTest().isEmpty())
        assertTrue(FlavorTextManager.activeTextsForTest().isEmpty())
    }
}
