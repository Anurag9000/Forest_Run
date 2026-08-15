package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NonTerminalCollisionOutcomeCoordinatorTest {

    @Test
    fun `persistent stumble completes in canonical order`() {
        val calls = mutableListOf<String>()
        val effects = RecordingEffects(calls)
        val presenter = RecordingPresenter(calls)
        val coordinator = coordinator(calls, effects, presenter)
        val input = stumbleInput(
            killerType = EntityType.WOLF,
            persistEncounter = true
        )

        coordinator.completeStumble(input) {
            calls += "deactivate"
        }

        assertEquals(
            listOf(
                "record-run-hit",
                "relationship:WOLF",
                "suppress:0.9",
                "trigger-stumble",
                "stumble-flash:287454020",
                "play-hit",
                "shake-hit",
                "stumble-impact-haptic",
                "present-stumble:WOLF",
                "deactivate"
            ),
            calls
        )
        assertEquals(input, presenter.stumble)
    }

    @Test
    fun `nonpersistent stumble skips relationship but preserves local effects`() {
        val calls = mutableListOf<String>()
        val presenter = RecordingPresenter(calls)
        val coordinator = coordinator(calls, RecordingEffects(calls), presenter)

        coordinator.completeStumble(
            stumbleInput(
                killerType = EntityType.CAT,
                persistEncounter = false
            )
        ) {
            calls += "deactivate"
        }

        assertEquals(
            listOf(
                "record-run-hit",
                "suppress:0.9",
                "trigger-stumble",
                "stumble-flash:287454020",
                "play-hit",
                "shake-hit",
                "stumble-impact-haptic",
                "present-stumble:CAT",
                "deactivate"
            ),
            calls
        )
    }

    @Test
    fun `unknown stumble killer does not invent relationship history`() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(
            calls,
            RecordingEffects(calls),
            RecordingPresenter(calls)
        )

        coordinator.completeStumble(
            stumbleInput(killerType = null, persistEncounter = true)
        ) {
            calls += "deactivate"
        }

        assertTrue(calls.none { it.startsWith("relationship:") })
        assertEquals("present-stumble:null", calls[calls.lastIndex - 1])
        assertEquals("deactivate", calls.last())
    }

    @Test
    fun `mercy miss completes in canonical order and computes player centre`() {
        val calls = mutableListOf<String>()
        val effects = RecordingEffects(calls)
        val presenter = RecordingPresenter(calls)
        val coordinator = coordinator(calls, effects, presenter)
        val input = MercyMissCollisionOutcome(
            entityType = EntityType.EAGLE,
            routeTier = PacifistRouteTier.PEACEFUL,
            mercyHearts = 6,
            kindnessChain = 8,
            playerX = 300f,
            playerY = 700f
        )

        coordinator.completeMercyMiss(input)

        assertEquals(
            listOf(
                "mercy-flash",
                "play-mercy-miss",
                "mercy-acknowledgement-haptic",
                "present-mercy:EAGLE:6:8",
                "mercy-stars:${300f + Player.BASE_WIDTH * 0.5f}:${700f + Player.BASE_HEIGHT * 0.5f}",
                "shake-mercy-miss"
            ),
            calls
        )
        assertEquals(input, presenter.mercy)
    }

    @Test
    fun `presentation coordinates pass through without coordinator rewriting`() {
        val calls = mutableListOf<String>()
        val presenter = RecordingPresenter(calls)
        val coordinator = coordinator(calls, RecordingEffects(calls), presenter)
        val stumble = stumbleInput(
            killerType = EntityType.CAT,
            persistEncounter = true
        ).copy(
            playerX = Float.NaN,
            playerY = Float.NEGATIVE_INFINITY
        )
        val mercy = MercyMissCollisionOutcome(
            entityType = null,
            routeTier = PacifistRouteTier.NONE,
            mercyHearts = 0,
            kindnessChain = 0,
            playerX = -Float.MAX_VALUE,
            playerY = Float.MAX_VALUE
        )

        coordinator.completeStumble(stumble) { calls += "deactivate" }
        coordinator.completeMercyMiss(mercy)

        assertEquals(stumble, presenter.stumble)
        assertEquals(mercy, presenter.mercy)
    }

    private fun coordinator(
        calls: MutableList<String>,
        effects: NonTerminalCollisionEffectSink,
        presenter: RecordingPresenter
    ): NonTerminalCollisionOutcomeCoordinator =
        NonTerminalCollisionOutcomeCoordinator(
            effects = effects,
            relationshipRecorder = object : NonTerminalCollisionRelationshipRecorder {
                override fun recordHit(type: EntityType) {
                    calls += "relationship:${type.name}"
                }
            },
            feedbackPresenter = presenter
        )

    private fun stumbleInput(
        killerType: EntityType?,
        persistEncounter: Boolean
    ): StumbleCollisionOutcome = StumbleCollisionOutcome(
        killerType = killerType,
        routeTier = PacifistRouteTier.MERCIFUL,
        playerX = 320f,
        playerY = 720f,
        dominantColor = 0x11223344,
        persistEncounter = persistEncounter
    )

    private class RecordingPresenter(
        private val calls: MutableList<String>
    ) : NonTerminalCollisionFeedbackPresenter {
        var stumble: StumbleCollisionOutcome? = null
            private set
        var mercy: MercyMissCollisionOutcome? = null
            private set

        override fun presentStumble(input: StumbleCollisionOutcome) {
            stumble = input
            calls += "present-stumble:${input.killerType?.name}"
        }

        override fun presentMercyMiss(input: MercyMissCollisionOutcome) {
            mercy = input
            calls += "present-mercy:${input.entityType?.name}:${input.mercyHearts}:${input.kindnessChain}"
        }
    }

    private class RecordingEffects(
        private val calls: MutableList<String>
    ) : NonTerminalCollisionEffectSink {
        override fun recordRunHit() {
            calls += "record-run-hit"
        }

        override fun suppressGhost(seconds: Float) {
            calls += "suppress:$seconds"
        }

        override fun triggerStumble() {
            calls += "trigger-stumble"
        }

        override fun showStumbleFlash(dominantColor: Int) {
            calls += "stumble-flash:$dominantColor"
        }

        override fun playNonLethalHit() {
            calls += "play-hit"
        }

        override fun shakeHit() {
            calls += "shake-hit"
        }

        override fun stumbleImpactHaptic() {
            calls += "stumble-impact-haptic"
        }

        override fun showMercyFlash() {
            calls += "mercy-flash"
        }

        override fun playMercyMiss() {
            calls += "play-mercy-miss"
        }

        override fun mercyAcknowledgementHaptic() {
            calls += "mercy-acknowledgement-haptic"
        }

        override fun emitMercyStars(centerX: Float, centerY: Float) {
            calls += "mercy-stars:$centerX:$centerY"
        }

        override fun shakeMercyMiss() {
            calls += "shake-mercy-miss"
        }
    }
}
