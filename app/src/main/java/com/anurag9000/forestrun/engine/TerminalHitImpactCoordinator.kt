package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.systems.GhostFrame

/**
 * Immutable post-impact inputs captured only after every immediate terminal HIT
 * effect has run.
 */
internal data class TerminalHitImpactCapture(
    val killerType: EntityType?,
    val biome: Biome,
    val presentation: TerminalHitPresentation,
    val completedGhost: List<GhostFrame>
) {
    init {
        require(presentation.killerType == killerType) {
            "terminal impact capture and presentation killer identity must match"
        }
    }
}

/** Live gameplay effects whose exact ordering defines terminal HIT impact feel. */
internal interface TerminalHitImpactEffectSink {
    fun recordRunHit()
    fun suppressGhost(seconds: Float)
    fun triggerPlayerRest()
    fun shakeHit()
    fun playHit()
    fun playRest()

    /** Domain-level terminal-impact cue used by collision orchestration. */
    fun terminalImpactHaptic()
}

/**
 * Owns only the immediate terminal HIT impact sequence.
 *
 * Capture is intentionally callback-based: Player rest, camera/audio/haptic
 * mutations remain before ghost detachment and killer/biome/player snapshots,
 * preserving the original GameView ordering exactly.
 */
internal class TerminalHitImpactCoordinator(
    private val effects: TerminalHitImpactEffectSink
) {
    fun apply(
        captureAfterImpact: () -> TerminalHitImpactCapture
    ): TerminalHitImpactCapture {
        effects.recordRunHit()
        effects.suppressGhost(GHOST_SUPPRESSION_SECONDS)
        effects.triggerPlayerRest()
        effects.shakeHit()
        effects.playHit()
        effects.playRest()
        effects.terminalImpactHaptic()
        return captureAfterImpact()
    }

    private companion object {
        const val GHOST_SUPPRESSION_SECONDS = 1.35f
    }
}
