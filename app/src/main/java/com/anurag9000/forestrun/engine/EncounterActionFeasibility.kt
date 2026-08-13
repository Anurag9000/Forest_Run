package com.anurag9000.forestrun.engine

import kotlin.math.sqrt

/**
 * Pure, conservative timing model for one avoidance action window.
 *
 * This intentionally does not duplicate entity hitboxes or author a second
 * difficulty curve. Callers supply measured/authoritative geometry and timing;
 * the model answers whether the available lead can accommodate input
 * arbitration plus the requested ballistic rise and an explicit safety margin.
 */
internal data class EncounterActionFeasibilityObservation(
    val leadDistancePx: Float,
    val approachSpeedPxPerSec: Float,
    val contactTimeSeconds: Float,
    val gestureDecisionSeconds: Float,
    val safetyMarginSeconds: Float,
    val requiredVerticalClearancePx: Float,
    val maximumBallisticRisePx: Float,
    val timeToRequiredRiseSeconds: Float,
    val jumpFeasible: Boolean,
    val duckFeasible: Boolean
) {
    val isFinite: Boolean
        get() = leadDistancePx.isFinite() &&
            approachSpeedPxPerSec.isFinite() &&
            contactTimeSeconds.isFinite() &&
            gestureDecisionSeconds.isFinite() &&
            safetyMarginSeconds.isFinite() &&
            requiredVerticalClearancePx.isFinite() &&
            maximumBallisticRisePx.isFinite() &&
            timeToRequiredRiseSeconds.isFinite()
}

/**
 * Deterministic experiment boundary for reaction-window analysis.
 *
 * The jump equation uses a constant supplied gravity, which is conservative for
 * Forest Run's full jump because Player temporarily reduces gravity around the
 * apex. Therefore a passing result does not overstate the production jump.
 */
internal object EncounterActionFeasibility {
    fun observe(
        leadDistancePx: Float,
        approachSpeedPxPerSec: Float,
        requiredVerticalClearancePx: Float,
        jumpUpwardSpeedPxPerSec: Float,
        gravityPxPerSecSquared: Float,
        gestureDecisionSeconds: Float,
        safetyMarginSeconds: Float = 0f
    ): EncounterActionFeasibilityObservation {
        val lead = finiteNonNegative(leadDistancePx)
        val speed = finitePositive(approachSpeedPxPerSec)
        val clearance = finiteNonNegative(requiredVerticalClearancePx)
        val jumpSpeed = finitePositive(jumpUpwardSpeedPxPerSec)
        val gravity = finitePositive(gravityPxPerSecSquared)
        val decision = finiteNonNegative(gestureDecisionSeconds)
        val safety = finiteNonNegative(safetyMarginSeconds)

        if (speed == 0f || jumpSpeed == 0f || gravity == 0f) {
            return invalidObservation(
                lead = lead,
                speed = speed,
                clearance = clearance,
                decision = decision,
                safety = safety
            )
        }

        val contactTime = finiteRatio(lead, speed)
        val maxRise = finiteRatio(
            jumpSpeed.toDouble() * jumpSpeed.toDouble(),
            2.0 * gravity.toDouble()
        )
        val riseTime = earliestRiseTime(
            clearancePx = clearance,
            upwardSpeedPxPerSec = jumpSpeed,
            gravityPxPerSecSquared = gravity,
            maximumRisePx = maxRise
        )
        val actionWindowAvailable = decision.toDouble() + safety.toDouble() <=
            contactTime.toDouble() + 0.0001
        val availableAfterDecision =
            (contactTime.toDouble() - decision.toDouble() - safety.toDouble())
                .coerceAtLeast(0.0)
                .coerceAtMost(Float.MAX_VALUE.toDouble())
                .toFloat()
        val jumpFeasible = actionWindowAvailable &&
            clearance <= maxRise + 0.0001f &&
            riseTime.isFinite() &&
            riseTime <= availableAfterDecision + 0.0001f
        val duckFeasible = actionWindowAvailable

        return EncounterActionFeasibilityObservation(
            leadDistancePx = lead,
            approachSpeedPxPerSec = speed,
            contactTimeSeconds = contactTime,
            gestureDecisionSeconds = decision,
            safetyMarginSeconds = safety,
            requiredVerticalClearancePx = clearance,
            maximumBallisticRisePx = maxRise,
            timeToRequiredRiseSeconds = riseTime,
            jumpFeasible = jumpFeasible,
            duckFeasible = duckFeasible
        )
    }

    private fun earliestRiseTime(
        clearancePx: Float,
        upwardSpeedPxPerSec: Float,
        gravityPxPerSecSquared: Float,
        maximumRisePx: Float
    ): Float {
        if (clearancePx <= 0f) return 0f
        if (clearancePx > maximumRisePx + 0.0001f) return Float.MAX_VALUE
        val speed = upwardSpeedPxPerSec.toDouble()
        val gravity = gravityPxPerSecSquared.toDouble()
        val discriminant = speed * speed - 2.0 * gravity * clearancePx.toDouble()
        if (!discriminant.isFinite() || discriminant < 0.0) return Float.MAX_VALUE
        val seconds = (speed - sqrt(discriminant)) / gravity
        return seconds
            .coerceIn(0.0, Float.MAX_VALUE.toDouble())
            .toFloat()
    }

    private fun finiteRatio(numerator: Float, denominator: Float): Float {
        if (numerator <= 0f) return 0f
        if (denominator <= 0f) return 0f
        return (numerator.toDouble() / denominator.toDouble())
            .coerceIn(0.0, Float.MAX_VALUE.toDouble())
            .toFloat()
    }

    private fun finiteRatio(numerator: Double, denominator: Double): Float {
        if (!numerator.isFinite() || numerator <= 0.0) return 0f
        if (!denominator.isFinite() || denominator <= 0.0) return 0f
        return (numerator / denominator)
            .coerceIn(0.0, Float.MAX_VALUE.toDouble())
            .toFloat()
    }

    private fun finiteNonNegative(value: Float): Float =
        value.takeIf { it.isFinite() && it >= 0f } ?: 0f

    private fun finitePositive(value: Float): Float =
        value.takeIf { it.isFinite() && it > 0f } ?: 0f

    private fun invalidObservation(
        lead: Float,
        speed: Float,
        clearance: Float,
        decision: Float,
        safety: Float
    ): EncounterActionFeasibilityObservation = EncounterActionFeasibilityObservation(
        leadDistancePx = lead,
        approachSpeedPxPerSec = speed,
        contactTimeSeconds = 0f,
        gestureDecisionSeconds = decision,
        safetyMarginSeconds = safety,
        requiredVerticalClearancePx = clearance,
        maximumBallisticRisePx = 0f,
        timeToRequiredRiseSeconds = Float.MAX_VALUE,
        jumpFeasible = false,
        duckFeasible = false
    )
}
