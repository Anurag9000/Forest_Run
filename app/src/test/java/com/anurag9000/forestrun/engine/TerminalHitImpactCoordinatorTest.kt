package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.systems.GhostFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class TerminalHitImpactCoordinatorTest {

    @Test
    fun `immediate effects precede post impact capture in exact order`() {
        val events = mutableListOf<String>()
        val effects = RecordingEffects(events)
        val coordinator = TerminalHitImpactCoordinator(effects)
        val expected = capture(EntityType.CAT)

        val actual = coordinator.apply {
            events += "capture"
            expected
        }

        assertSame(expected, actual)
        assertEquals(
            listOf(
                "record_run_hit",
                "suppress_ghost:1.35",
                "trigger_player_rest",
                "shake_hit",
                "play_hit",
                "play_rest",
                "terminal_impact_haptic",
                "capture"
            ),
            events
        )
    }

    @Test
    fun `capture is not invoked when an earlier impact effect fails`() {
        val events = mutableListOf<String>()
        val coordinator = TerminalHitImpactCoordinator(
            RecordingEffects(events, failAt = "play_hit")
        )

        assertThrows(IllegalStateException::class.java) {
            coordinator.apply {
                events += "capture"
                capture(EntityType.CAT)
            }
        }

        assertEquals(
            listOf(
                "record_run_hit",
                "suppress_ghost:1.35",
                "trigger_player_rest",
                "shake_hit",
                "play_hit"
            ),
            events
        )
    }

    @Test
    fun `capture rejects presentation with a different killer identity`() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalHitImpactCapture(
                killerType = EntityType.CAT,
                biome = Biome.MEADOW,
                presentation = TerminalHitPresentation(
                    killerType = EntityType.WOLF,
                    routeTier = PacifistRouteTier.NONE,
                    playerX = 120f,
                    playerY = 600f
                ),
                completedGhost = frames()
            )
        }
    }

    private fun capture(killerType: EntityType?): TerminalHitImpactCapture =
        TerminalHitImpactCapture(
            killerType = killerType,
            biome = Biome.MEADOW,
            presentation = TerminalHitPresentation(
                killerType = killerType,
                routeTier = PacifistRouteTier.NONE,
                playerX = 120f,
                playerY = 600f
            ),
            completedGhost = frames()
        )

    private fun frames(): List<GhostFrame> = listOf(
        GhostFrame(0f, 120f, 600f, 0, 1f, 1f),
        GhostFrame(0.04f, 124f, 590f, 1, 0.98f, 1.02f)
    )

    private class RecordingEffects(
        private val events: MutableList<String>,
        private val failAt: String? = null
    ) : TerminalHitImpactEffectSink {
        override fun recordRunHit() = record("record_run_hit")

        override fun suppressGhost(seconds: Float) =
            record("suppress_ghost:$seconds")

        override fun triggerPlayerRest() = record("trigger_player_rest")

        override fun shakeHit() = record("shake_hit")

        override fun playHit() = record("play_hit")

        override fun playRest() = record("play_rest")

        override fun terminalImpactHaptic() = record("terminal_impact_haptic")

        private fun record(event: String) {
            events += event
            if (event == failAt) error("forced impact failure")
        }
    }
}
