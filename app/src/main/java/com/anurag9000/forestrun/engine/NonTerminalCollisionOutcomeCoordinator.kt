package com.anurag9000.forestrun.engine

import android.content.Context
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.ui.DialogueBubbleManager
import com.anurag9000.forestrun.ui.FlavorTextManager

internal data class StumbleCollisionOutcome(
    val killerType: EntityType?,
    val routeTier: PacifistRouteTier,
    val playerX: Float,
    val playerY: Float,
    val dominantColor: Int,
    val persistEncounter: Boolean
)

internal data class MercyMissCollisionOutcome(
    val entityType: EntityType?,
    val routeTier: PacifistRouteTier,
    val mercyHearts: Int,
    val kindnessChain: Int,
    val playerX: Float,
    val playerY: Float
)

internal interface NonTerminalCollisionRelationshipRecorder {
    fun recordHit(type: EntityType)
}

internal interface NonTerminalCollisionFeedbackPresenter {
    fun presentStumble(input: StumbleCollisionOutcome)
    fun presentMercyMiss(input: MercyMissCollisionOutcome)
}

/** Runtime mutations retained by the live GameView owner. */
internal interface NonTerminalCollisionEffectSink {
    fun recordRunHit()
    fun suppressGhost(seconds: Float)
    fun triggerStumble()
    fun showStumbleFlash(dominantColor: Int)
    fun playNonLethalHit()
    fun shakeHit()

    /** Compatibility primitives retained for existing live/test adapters. */
    fun mediumPulse()
    fun doubleTap()

    /** Domain-level semantic haptic cues used by collision orchestration. */
    fun stumbleImpactHaptic() = mediumPulse()
    fun mercyAcknowledgementHaptic() = doubleTap()

    fun showMercyFlash()
    fun playMercyMiss()
    fun emitMercyStars(centerX: Float, centerY: Float)
    fun shakeMercyMiss()
}

internal class AndroidNonTerminalCollisionRelationshipRecorder(context: Context) :
    NonTerminalCollisionRelationshipRecorder {
    private val appContext = context.applicationContext

    override fun recordHit(type: EntityType) {
        PersistentMemoryManager.recordHit(appContext, type)
    }
}

internal class AndroidNonTerminalCollisionFeedbackPresenter(context: Context) :
    NonTerminalCollisionFeedbackPresenter {
    private val appContext = context.applicationContext

    override fun presentStumble(input: StumbleCollisionOutcome) {
        val cue = RunFlavorPresentation.collisionCue(
            context = appContext,
            type = input.killerType,
            result = CollisionResult.STUMBLE,
            routeTier = input.routeTier
        )
        DialogueBubbleManager.spawn(
            text = cue.bubbleText,
            anchorX = input.playerX + Player.BASE_WIDTH * 0.5f,
            anchorY = input.playerY - 24f,
            fillColor = cue.fillColor,
            borderColor = cue.borderColor
        )
        FlavorTextManager.spawn(
            text = cue.flavorText,
            x = input.playerX + Player.BASE_WIDTH * 0.20f,
            y = input.playerY - 10f,
            colour = cue.flavorColor,
            lifetime = 1.0f,
            size = cue.flavorSize
        )
    }

    override fun presentMercyMiss(input: MercyMissCollisionOutcome) {
        val cue = RunFlavorPresentation.mercyCue(
            context = appContext,
            type = input.entityType,
            mercyHearts = input.mercyHearts,
            kindnessChain = input.kindnessChain,
            routeTier = input.routeTier
        )
        DialogueBubbleManager.spawn(
            text = cue.bubbleText,
            anchorX = input.playerX + Player.BASE_WIDTH * 0.5f,
            anchorY = input.playerY - 24f,
            fillColor = cue.fillColor,
            borderColor = cue.borderColor
        )
        FlavorTextManager.spawn(
            text = cue.flavorText,
            x = input.playerX + Player.BASE_WIDTH * 0.22f,
            y = input.playerY - 12f,
            colour = cue.flavorColor,
            lifetime = 1.15f,
            size = cue.flavorSize
        )
    }
}

/**
 * Owns ordered, nonterminal collision completion while delegating live mutable
 * effects back to GameView through [NonTerminalCollisionEffectSink].
 */
internal class NonTerminalCollisionOutcomeCoordinator(
    private val effects: NonTerminalCollisionEffectSink,
    private val relationshipRecorder: NonTerminalCollisionRelationshipRecorder,
    private val feedbackPresenter: NonTerminalCollisionFeedbackPresenter
) {
    fun completeStumble(
        input: StumbleCollisionOutcome,
        deactivateEntity: () -> Unit
    ) {
        effects.recordRunHit()
        if (input.persistEncounter && input.killerType != null) {
            relationshipRecorder.recordHit(input.killerType)
        }
        effects.suppressGhost(STUMBLE_GHOST_SUPPRESSION_SECONDS)
        effects.triggerStumble()
        effects.showStumbleFlash(input.dominantColor)
        effects.playNonLethalHit()
        effects.shakeHit()
        effects.stumbleImpactHaptic()
        feedbackPresenter.presentStumble(input)
        deactivateEntity()
    }

    fun completeMercyMiss(input: MercyMissCollisionOutcome) {
        effects.showMercyFlash()
        effects.playMercyMiss()
        effects.mercyAcknowledgementHaptic()
        feedbackPresenter.presentMercyMiss(input)
        effects.emitMercyStars(
            centerX = input.playerX + Player.BASE_WIDTH * 0.5f,
            centerY = input.playerY + Player.BASE_HEIGHT * 0.5f
        )
        effects.shakeMercyMiss()
    }

    private companion object {
        const val STUMBLE_GHOST_SUPPRESSION_SECONDS = 0.9f
    }
}
