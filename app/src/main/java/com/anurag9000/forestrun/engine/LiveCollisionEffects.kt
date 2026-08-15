package com.anurag9000.forestrun.engine

/**
 * Concrete live-runtime adapter shared by terminal and nonterminal collision
 * coordinators.
 *
 * This type owns no ordering or game state. It only maps narrow effect ports to
 * callbacks supplied by the live view, keeping mutable Android/gameplay owners
 * outside the pure collision coordinators.
 */
internal class LiveCollisionEffects(
    private val recordRunHitAction: () -> Unit,
    private val suppressGhostAction: (Float) -> Unit,
    private val triggerPlayerRestAction: () -> Unit,
    private val triggerStumbleAction: () -> Unit,
    private val showStumbleFlashAction: (Int) -> Unit,
    private val playHitAction: () -> Unit,
    private val shakeHitAction: () -> Unit,
    private val playRestAction: () -> Unit,
    private val longPulseAction: () -> Unit,
    private val mediumPulseAction: () -> Unit,
    private val showMercyFlashAction: () -> Unit,
    private val playMercyMissAction: () -> Unit,
    private val doubleTapAction: () -> Unit,
    private val emitMercyStarsAction: (Float, Float) -> Unit,
    private val shakeMercyMissAction: () -> Unit
) : TerminalHitImpactEffectSink, NonTerminalCollisionEffectSink {
    override fun recordRunHit() = recordRunHitAction()

    override fun suppressGhost(seconds: Float) = suppressGhostAction(seconds)

    override fun triggerPlayerRest() = triggerPlayerRestAction()

    override fun triggerStumble() = triggerStumbleAction()

    override fun showStumbleFlash(dominantColor: Int) =
        showStumbleFlashAction(dominantColor)

    override fun playHit() = playHitAction()

    override fun playNonLethalHit() = playHitAction()

    override fun shakeHit() = shakeHitAction()

    override fun playRest() = playRestAction()

    override fun terminalImpactHaptic() = longPulseAction()

    override fun stumbleImpactHaptic() = mediumPulseAction()

    override fun showMercyFlash() = showMercyFlashAction()

    override fun playMercyMiss() = playMercyMissAction()

    override fun mercyAcknowledgementHaptic() = doubleTapAction()

    override fun emitMercyStars(centerX: Float, centerY: Float) =
        emitMercyStarsAction(centerX, centerY)

    override fun shakeMercyMiss() = shakeMercyMissAction()
}
