package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class CollisionRuntimeEffectAdaptersTest {
    @Test
    fun terminalAdapterDelegatesOnlyTerminalSurfaceInOrder() {
        val runtime = RecordingRuntime()
        val adapter = PortBackedTerminalHitImpactEffects(runtime)

        adapter.recordRunHit()
        adapter.suppressGhost(1.35f)
        adapter.triggerPlayerRest()
        adapter.shakeHit()
        adapter.playHit()
        adapter.playRest()
        adapter.terminalImpactHaptic()

        assertEquals(
            listOf(
                "record",
                "suppress:1.35",
                "rest",
                "shakeHit",
                "playHit",
                "playRest",
                "longPulse"
            ),
            runtime.trace
        )
    }

    @Test
    fun nonterminalAdapterDelegatesStumbleAndMercySurfaceExactly() {
        val runtime = RecordingRuntime()
        val adapter = PortBackedNonTerminalCollisionEffects(runtime)

        adapter.recordRunHit()
        adapter.suppressGhost(0.9f)
        adapter.triggerStumble()
        adapter.showStumbleFlash(0x123456)
        adapter.playNonLethalHit()
        adapter.shakeHit()
        adapter.stumbleImpactHaptic()
        adapter.showMercyFlash()
        adapter.playMercyMiss()
        adapter.mercyAcknowledgementHaptic()
        adapter.emitMercyStars(356f, 770f)
        adapter.shakeMercyMiss()

        assertEquals(
            listOf(
                "record",
                "suppress:0.9",
                "stumble",
                "stumbleFlash:1193046",
                "playHit",
                "shakeHit",
                "mediumPulse",
                "mercyFlash",
                "playMercyMiss",
                "doubleTap",
                "stars:356.0:770.0",
                "shakeMercy"
            ),
            runtime.trace
        )
    }

    private class RecordingRuntime : CollisionRuntimePort {
        val trace = mutableListOf<String>()

        override fun recordRunHit() { trace += "record" }
        override fun suppressGhost(seconds: Float) { trace += "suppress:$seconds" }
        override fun triggerPlayerRest() { trace += "rest" }
        override fun triggerStumble() { trace += "stumble" }
        override fun showStumbleFlash(dominantColor: Int) {
            trace += "stumbleFlash:$dominantColor"
        }
        override fun showMercyFlash() { trace += "mercyFlash" }
        override fun shakeHit() { trace += "shakeHit" }
        override fun shakeMercyMiss() { trace += "shakeMercy" }
        override fun playHit() { trace += "playHit" }
        override fun playRest() { trace += "playRest" }
        override fun playMercyMiss() { trace += "playMercyMiss" }
        override fun longPulse() { trace += "longPulse" }
        override fun mediumPulse() { trace += "mediumPulse" }
        override fun doubleTap() { trace += "doubleTap" }
        override fun emitMercyStars(centerX: Float, centerY: Float) {
            trace += "stars:$centerX:$centerY"
        }
    }
}
