package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.EntityType

/** Result returned to the live run owner after one collision outcome is handled. */
internal sealed interface CollisionOutcomeDispatchResult {
    /** No outcome work was required. */
    object Ignored : CollisionOutcomeDispatchResult

    /** A nonterminal outcome completed and the run remains live. */
    object NonTerminal : CollisionOutcomeDispatchResult

    /** A terminal HIT completed and the caller must enter its death transition. */
    data class Terminal(
        val completion: TerminalHitCompletionResult
    ) : CollisionOutcomeDispatchResult
}

/**
 * Exhaustive owner for routing one already-arbitrated [CollisionResult].
 *
 * Inputs are lazy so only the selected branch captures live mutable state. This
 * preserves the original snapshot points: terminal ghost/killer/biome/player
 * capture occurs after immediate HIT effects; STUMBLE and MERCY_MISS values are
 * not read for unrelated outcomes.
 */
internal class CollisionOutcomeDispatcher(
    private val terminalHitImpact: TerminalHitImpactCoordinator,
    private val terminalHitOutcome: TerminalHitOutcomeCoordinator,
    private val nonTerminalOutcome: NonTerminalCollisionOutcomeCoordinator
) {
    fun dispatch(
        result: CollisionResult,
        persistEncounter: Boolean,
        captureTerminalImpact: () -> TerminalHitImpactCapture,
        buildTerminalSummaryPreview: (EntityType?) -> RunSummary,
        buildStumbleInput: () -> StumbleCollisionOutcome,
        deactivateStumbleEntity: () -> Unit,
        buildMercyMissInput: () -> MercyMissCollisionOutcome
    ): CollisionOutcomeDispatchResult = when (result) {
        CollisionResult.HIT -> {
            val impact = terminalHitImpact.apply(captureTerminalImpact)
            val completion = terminalHitOutcome.complete(
                killerType = impact.killerType,
                biome = impact.biome,
                presentation = impact.presentation,
                completedGhost = impact.completedGhost,
                persistEncounter = persistEncounter
            ) {
                buildTerminalSummaryPreview(impact.killerType)
            }
            CollisionOutcomeDispatchResult.Terminal(completion)
        }

        CollisionResult.STUMBLE -> {
            nonTerminalOutcome.completeStumble(
                input = buildStumbleInput(),
                deactivateEntity = deactivateStumbleEntity
            )
            CollisionOutcomeDispatchResult.NonTerminal
        }

        CollisionResult.MERCY_MISS -> {
            nonTerminalOutcome.completeMercyMiss(buildMercyMissInput())
            CollisionOutcomeDispatchResult.NonTerminal
        }

        CollisionResult.NONE -> CollisionOutcomeDispatchResult.Ignored
    }
}
