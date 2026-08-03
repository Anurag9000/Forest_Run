package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.EntityType

/** Lazy terminal command captured only when HIT is the selected collision result. */
internal data class HitCollisionDispatch(
    val persistEncounter: Boolean,
    val captureAfterImpact: () -> TerminalHitImpactCapture,
    val buildSummaryPreview: (EntityType?) -> RunSummary
)

/** Lazy STUMBLE command captured only when STUMBLE is selected. */
internal data class StumbleCollisionDispatch(
    val input: StumbleCollisionOutcome,
    val deactivateEntity: () -> Unit
)

/**
 * Owns exhaustive routing of one already-arbitrated collision result.
 *
 * Providers are intentionally lazy. Only the provider for the selected result
 * may read live GameView state, detach a ghost, or construct outcome inputs.
 * GameView retains collision arbitration, terminal-summary assignment, and the
 * death-state transition.
 */
internal class CollisionOutcomeDispatcher(
    private val terminalHitImpact: TerminalHitImpactCoordinator,
    private val terminalHitOutcome: TerminalHitOutcomeCoordinator,
    private val nonTerminalCollisionOutcome: NonTerminalCollisionOutcomeCoordinator
) {
    fun dispatch(
        result: CollisionResult,
        hit: () -> HitCollisionDispatch,
        stumble: () -> StumbleCollisionDispatch,
        mercyMiss: () -> MercyMissCollisionOutcome
    ): TerminalHitCompletionResult? = when (result) {
        CollisionResult.HIT -> {
            val command = hit()
            val impact = terminalHitImpact.apply(command.captureAfterImpact)
            terminalHitOutcome.complete(
                killerType = impact.killerType,
                biome = impact.biome,
                presentation = impact.presentation,
                completedGhost = impact.completedGhost,
                persistEncounter = command.persistEncounter
            ) {
                command.buildSummaryPreview(impact.killerType)
            }
        }
        CollisionResult.STUMBLE -> {
            val command = stumble()
            nonTerminalCollisionOutcome.completeStumble(
                input = command.input,
                deactivateEntity = command.deactivateEntity
            )
            null
        }
        CollisionResult.MERCY_MISS -> {
            nonTerminalCollisionOutcome.completeMercyMiss(mercyMiss())
            null
        }
        CollisionResult.NONE -> null
    }
}
