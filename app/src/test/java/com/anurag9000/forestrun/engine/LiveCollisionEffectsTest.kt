package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveCollisionEffectsTest {
    @Test
    fun delegatesEveryPortWithoutOwningOrder() {
        val calls = mutableListOf<String>()
        val effects = LiveCollisionEffects(
            recordRunHitAction = { calls += "record" },
            suppressGhostAction = { calls += "suppress:$it" },
            triggerPlayerRestAction = { calls += "player-rest" },
            triggerStumbleAction = { calls += "stumble" },
            showStumbleFlashAction = { calls += "stumble-flash:$it" },
            playHitAction = { calls += "shared-sound" },
            shakeHitAction = { calls += "shared-shake" },
            playRestAction = { calls += "rest-music" },
            longPulseAction = { calls += "long-pulse" },
            mediumPulseAction = { calls += "medium-pulse" },
            showMercyFlashAction = { calls += "mercy-flash" },
            playMercyMissAction = { calls += "mercy-sound" },
            doubleTapAction = { calls += "double-tap" },
            emitMercyStarsAction = { x, y -> calls += "stars:$x:$y" },
            shakeMercyMissAction = { calls += "mercy-shake" }
        )

        effects.recordRunHit()
        effects.suppressGhost(1.35f)
        effects.triggerPlayerRest()
        effects.triggerStumble()
        effects.showStumbleFlash(42)
        effects.playHit()
        effects.playNonLethalHit()
        effects.shakeHit()
        effects.playRest()
        effects.terminalImpactHaptic()
        effects.stumbleImpactHaptic()
        effects.showMercyFlash()
        effects.playMercyMiss()
        effects.mercyAcknowledgementHaptic()
        effects.emitMercyStars(12f, 34f)
        effects.shakeMercyMiss()

        assertEquals(
            listOf(
                "record",
                "suppress:1.35",
                "player-rest",
                "stumble",
                "stumble-flash:42",
                "shared-sound",
                "shared-sound",
                "shared-shake",
                "rest-music",
                "long-pulse",
                "medium-pulse",
                "mercy-flash",
                "mercy-sound",
                "double-tap",
                "stars:12.0:34.0",
                "mercy-shake"
            ),
            calls
        )
    }
}
