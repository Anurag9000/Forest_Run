package com.anurag9000.forestrun.engine

import kotlin.math.ceil
import kotlin.math.floor
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
 * The maximum admitted 50ms semi-implicit Player step is the source of
 * truth for the conservative vertical launch envelope. A continuous parabola
 * overstates sampled height and reports an unrealistically early first rise.
 * APEX gravity reduction starts only after upward velocity reaches zero and
 * cannot restore height already lost before the apex.
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

        // The finite report fields are sanitized for diagnostics, but a
        // malformed independent input must never turn into a zero-cost action.
        // In particular NaN clearance/decision/safety previously became 0f
        // and could produce a false jump/duck feasibility PASS.
        val validInputs =
            leadDistancePx.isFinite() && leadDistancePx >= 0f &&
            approachSpeedPxPerSec.isFinite() && approachSpeedPxPerSec > 0f &&
            requiredVerticalClearancePx.isFinite() && requiredVerticalClearancePx >= 0f &&
            jumpUpwardSpeedPxPerSec.isFinite() && jumpUpwardSpeedPxPerSec > 0f &&
            gravityPxPerSecSquared.isFinite() && gravityPxPerSecSquared > 0f &&
            gestureDecisionSeconds.isFinite() && gestureDecisionSeconds >= 0f &&
            safetyMarginSeconds.isFinite() && safetyMarginSeconds >= 0f

        if (!validInputs) {
            return invalidObservation(
                lead = lead,
                speed = speed,
                clearance = clearance,
                decision = decision,
                safety = safety
            )
        }

        val contactTime = finiteFloatRatio(lead, speed)
        val maxRise = sampledMaximumRise(jumpSpeed, gravity)
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
            riseTime < Float.MAX_VALUE &&
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

    /**
     * Same semi-implicit update as Player:
     *   v_n = -upwardSpeed + n * gravity * dt
     *   rise_n = n * upwardSpeed * dt - gravity * dt² * n(n+1)/2
     *
     * Its peak is at one of the two integer steps surrounding the parabola's
     * vertex. Compute those directly, without a loop that could grow without
     * bound for malformed-but-finite extreme parameter combinations.
     */
    private fun sampledMaximumRise(upwardSpeed: Float, gravity: Float): Float {
        val dt = FrameInputAdmission.MAX_DELTA_SECONDS.toDouble()
        val speed = upwardSpeed.toDouble()
        val g = gravity.toDouble()
        val effectiveSpeed = speed - 0.5 * g * dt
        if (effectiveSpeed <= 0.0) return 0f
        val vertexStep = effectiveSpeed / (g * dt)
        val earlier = floor(vertexStep).coerceAtLeast(0.0)
        return maxOf(
            riseAtStep(earlier, speed, g, dt),
            riseAtStep(earlier + 1.0, speed, g, dt)
        ).coerceIn(0.0, Float.MAX_VALUE.toDouble()).toFloat()
    }

    private fun riseAtStep(
        steps: Double,
        upwardSpeed: Double,
        gravity: Double,
        deltaTime: Double
    ): Double {
        val time = steps * deltaTime
        return (upwardSpeed * time -
            0.5 * gravity * deltaTime * deltaTime * steps * (steps + 1.0))
            .coerceAtLeast(0.0)
    }

    private fun earliestRiseTime(
        clearancePx: Float,
        upwardSpeedPxPerSec: Float,
        gravityPxPerSecSquared: Float,
        maximumRisePx: Float
    ): Float {
        if (clearancePx <= 0f) return 0f
        if (clearancePx > maximumRisePx) return Float.MAX_VALUE
        val speed = upwardSpeedPxPerSec.toDouble()
        val gravity = gravityPxPerSecSquared.toDouble()
        val dt = FrameInputAdmission.MAX_DELTA_SECONDS.toDouble()
        val effectiveSpeed = speed - 0.5 * gravity * dt
        if (effectiveSpeed <= 0.0) return Float.MAX_VALUE
        val discriminant = effectiveSpeed * effectiveSpeed -
            2.0 * gravity * clearancePx.toDouble()
        if (!discriminant.isFinite() || discriminant < 0.0) return Float.MAX_VALUE

        // Stable smaller root of the sampled-height parabola. Round UP to a
        // complete admitted simulation step; a continuous crossing between
        // update calls is not yet an observable/reachable Player position.
        val denominator = effectiveSpeed + sqrt(discriminant)
        if (!denominator.isFinite() || denominator <= 0.0) return Float.MAX_VALUE
        val continuousStepTime = 2.0 * clearancePx.toDouble() / denominator
        // The exact 495px apex falls at a sampled step. Float 0.05f and
        // a Double root can put that integer boundary a few ulps above 11.
        // Snap near-integers down, then validate the actual sampled height.
        val firstStep = ceil(continuousStepTime / dt - 1e-7).coerceAtLeast(1.0)
        val firstRise = riseAtStep(firstStep, speed, gravity, dt)
        val target = clearancePx.toDouble()
        val roundingTolerance = 0.0001
        val selectedStep = when {
            firstRise + roundingTolerance >= target -> firstStep
            riseAtStep(firstStep + 1.0, speed, gravity, dt) +
                roundingTolerance >= target -> firstStep + 1.0
            else -> return Float.MAX_VALUE
        }
        return (selectedStep * dt).coerceIn(
            0.0, Float.MAX_VALUE.toDouble()
        ).toFloat()
    }

    private fun finiteFloatRatio(numerator: Float, denominator: Float): Float {
        if (numerator <= 0f) return 0f
        if (denominator <= 0f) return 0f
        return (numerator.toDouble() / denominator.toDouble())
            .coerceIn(0.0, Float.MAX_VALUE.toDouble())
            .toFloat()
    }

    private fun finiteDoubleRatio(numerator: Double, denominator: Double): Float {
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
