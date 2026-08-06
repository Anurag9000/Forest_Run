package com.anurag9000.forestrun.engine

/**
 * Small live-runtime boundary required by collision effect adapters.
 *
 * A future GameView integration implements this one port while the terminal and
 * nonterminal coordinators depend only on their existing narrow sink contracts.
 * Static managers, mutable Paint objects, Player, GameStateManager, and ghost
 * playback therefore stop leaking into the orchestration layer.
 */
internal interface CollisionRuntimePort {
    fun recordRunHit()
    fun suppressGhost(seconds: Float)
    fun triggerPlayerRest()
    fun triggerStumble()
    fun showStumbleFlash(dominantColor: Int)
    fun showMercyFlash()
    fun shakeHit()
    fun shakeMercyMiss()
    fun playHit()
    fun playRest()
    fun playMercyMiss()
    fun longPulse()
    fun mediumPulse()
    fun doubleTap()
    fun emitMercyStars(centerX: Float, centerY: Float)
}

/** Terminal effect adapter backed only by [CollisionRuntimePort]. */
internal class PortBackedTerminalHitImpactEffects(
    private val runtime: CollisionRuntimePort
) : TerminalHitImpactEffectSink {
    override fun recordRunHit() = runtime.recordRunHit()
    override fun suppressGhost(seconds: Float) = runtime.suppressGhost(seconds)
    override fun triggerPlayerRest() = runtime.triggerPlayerRest()
    override fun shakeHit() = runtime.shakeHit()
    override fun playHit() = runtime.playHit()
    override fun playRest() = runtime.playRest()
    override fun longPulse() = runtime.longPulse()
}

/** Nonterminal effect adapter backed only by [CollisionRuntimePort]. */
internal class PortBackedNonTerminalCollisionEffects(
    private val runtime: CollisionRuntimePort
) : NonTerminalCollisionEffectSink {
    override fun recordRunHit() = runtime.recordRunHit()
    override fun suppressGhost(seconds: Float) = runtime.suppressGhost(seconds)
    override fun triggerStumble() = runtime.triggerStumble()
    override fun showStumbleFlash(dominantColor: Int) =
        runtime.showStumbleFlash(dominantColor)

    override fun playNonLethalHit() = runtime.playHit()
    override fun shakeHit() = runtime.shakeHit()
    override fun mediumPulse() = runtime.mediumPulse()
    override fun showMercyFlash() = runtime.showMercyFlash()
    override fun playMercyMiss() = runtime.playMercyMiss()
    override fun doubleTap() = runtime.doubleTap()
    override fun emitMercyStars(centerX: Float, centerY: Float) =
        runtime.emitMercyStars(centerX, centerY)

    override fun shakeMercyMiss() = runtime.shakeMercyMiss()
}
