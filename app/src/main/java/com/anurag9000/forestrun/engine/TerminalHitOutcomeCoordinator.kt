package com.anurag9000.forestrun.engine

import android.content.Context
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.systems.GhostFrame
import com.anurag9000.forestrun.ui.DialogueBubbleManager
import com.anurag9000.forestrun.ui.FlavorTextManager
import com.anurag9000.forestrun.ui.RestQuoteManager

internal data class TerminalHitPresentation(
    val killerType: EntityType?,
    val routeTier: PacifistRouteTier,
    val playerX: Float,
    val playerY: Float
)

internal data class TerminalHitCompletionResult(
    val summary: RunSummary,
    val persistence: RunOutcomeCommitResult
)

internal interface TerminalHitRelationshipRecorder {
    fun recordHit(type: EntityType)
}

internal interface TerminalHitFeedbackPresenter {
    fun present(input: TerminalHitPresentation)
}

internal interface TerminalHitRestQuoteResolver {
    fun resolve(
        summaryPreview: RunSummary,
        biome: Biome,
        killerType: EntityType?
    ): String
}

internal class AndroidTerminalHitRelationshipRecorder(context: Context) :
    TerminalHitRelationshipRecorder {
    private val appContext = context.applicationContext

    override fun recordHit(type: EntityType) {
        PersistentMemoryManager.recordHit(appContext, type)
    }
}

internal class AndroidTerminalHitFeedbackPresenter(context: Context) :
    TerminalHitFeedbackPresenter {
    private val appContext = context.applicationContext

    override fun present(input: TerminalHitPresentation) {
        val cue = RunFlavorPresentation.collisionCue(
            context = appContext,
            type = input.killerType,
            result = CollisionResult.HIT,
            routeTier = input.routeTier
        )
        DialogueBubbleManager.spawn(
            text = cue.bubbleText,
            anchorX = input.playerX + Player.BASE_WIDTH * 0.5f,
            anchorY = input.playerY - 28f,
            fillColor = cue.fillColor,
            borderColor = cue.borderColor
        )
        FlavorTextManager.spawn(
            text = cue.flavorText,
            x = input.playerX + Player.BASE_WIDTH * 0.18f,
            y = input.playerY - 8f,
            colour = cue.flavorColor,
            lifetime = 1.25f,
            size = cue.flavorSize
        )
    }
}

internal class AndroidTerminalHitRestQuoteResolver(context: Context) :
    TerminalHitRestQuoteResolver {
    private val appContext = context.applicationContext

    override fun resolve(
        summaryPreview: RunSummary,
        biome: Biome,
        killerType: EntityType?
    ): String = RestQuoteManager.quoteFor(
        context = appContext,
        summary = summaryPreview,
        biome = biome,
        killer = killerType
    )
}

/**
 * Owns the behavior-preserving completion sequence after immediate hit feedback.
 *
 * Ordering is intentional: relationship memory first, authored collision copy,
 * summary snapshot, rest quote, then exactly-once persistence.
 */
internal class TerminalHitOutcomeCoordinator(
    private val relationshipRecorder: TerminalHitRelationshipRecorder,
    private val feedbackPresenter: TerminalHitFeedbackPresenter,
    private val restQuoteResolver: TerminalHitRestQuoteResolver,
    private val outcomeCommitter: RunOutcomeCommitter
) {
    fun complete(
        killerType: EntityType?,
        biome: Biome,
        presentation: TerminalHitPresentation,
        completedGhost: List<GhostFrame>,
        persistEncounter: Boolean,
        buildSummaryPreview: () -> RunSummary
    ): TerminalHitCompletionResult {
        require(presentation.killerType == killerType) {
            "terminal hit presentation and completion killer identity must match"
        }

        if (persistEncounter && killerType != null) {
            relationshipRecorder.recordHit(killerType)
        }

        feedbackPresenter.present(presentation)

        val summaryPreview = buildSummaryPreview()
        val restQuote = restQuoteResolver.resolve(summaryPreview, biome, killerType)
        val completedSummary = summaryPreview.copy(restQuote = restQuote)
        val persistence = outcomeCommitter.commit(
            summary = completedSummary,
            completedGhost = completedGhost,
            persistProgress = persistEncounter
        )

        return TerminalHitCompletionResult(
            summary = completedSummary,
            persistence = persistence
        )
    }
}
