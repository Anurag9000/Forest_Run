package com.anurag9000.forestrun.entities

import kotlin.math.abs
import kotlin.math.sin

data class PlayerSecondaryMotionState(
    val bodyTiltDegrees: Float,
    val bodyLiftPx: Float,
    val costumeSwingPx: Float,
    val costumeTrailLiftPx: Float,
    val headOffsetPx: Float
)

internal object PlayerSecondaryMotion {
    private const val MAX_BODY_HEIGHT_PX = 10_000f
    private const val PHASE_WRAP_SECONDS = 10_000f

    fun resolve(
        state: PlayerState,
        velocityY: Float,
        bodyHeight: Float,
        elapsed: Float,
        isInvincible: Boolean
    ): PlayerSecondaryMotionState {
        val safeVelocity = velocityY.takeIf { it.isFinite() }
            ?.coerceIn(-100_000f, 100_000f)
            ?: 0f
        val safeHeight = bodyHeight.takeIf { it.isFinite() }
            ?.coerceIn(0f, MAX_BODY_HEIGHT_PX)
            ?: 0f
        val safeElapsed = elapsed.takeIf { it.isFinite() && it >= 0f }
            ?.rem(PHASE_WRAP_SECONDS)
            ?: 0f

        val stride = sin(safeElapsed * if (state == PlayerState.BLOOM) 12f else 8.2f)
        val floatPulse = sin(safeElapsed * if (state == PlayerState.BLOOM) 7.2f else 4.8f)
        val verticalInfluence = (safeVelocity / 1200f).coerceIn(-1f, 1f)
        val fallInfluence = (abs(safeVelocity) / 1500f).coerceIn(0f, 1f)

        val baseTilt = when (state) {
            PlayerState.RUNNING -> stride * 1.4f
            PlayerState.JUMP_START -> -5.2f
            PlayerState.JUMPING -> (-6.4f - verticalInfluence * 2.2f)
            PlayerState.APEX -> stride * 1.1f
            PlayerState.FALLING -> (4.8f + verticalInfluence * 2.6f)
            PlayerState.LANDING -> 5.4f
            PlayerState.DUCKING -> -2.8f
            PlayerState.BLOOM -> stride * 2.8f
            PlayerState.STUMBLE -> stride * 4.1f
            PlayerState.REST -> 0f
        }
        val baseLift = when (state) {
            PlayerState.RUNNING -> stride * safeHeight * 0.010f
            PlayerState.JUMP_START -> -safeHeight * 0.026f
            PlayerState.JUMPING -> -safeHeight * (0.030f + fallInfluence * 0.010f)
            PlayerState.APEX -> floatPulse * safeHeight * 0.014f
            PlayerState.FALLING -> safeHeight * 0.016f
            PlayerState.LANDING -> safeHeight * 0.020f
            PlayerState.DUCKING -> safeHeight * 0.012f
            PlayerState.BLOOM -> floatPulse * safeHeight * 0.024f
            PlayerState.STUMBLE -> stride * safeHeight * 0.018f
            PlayerState.REST -> 0f
        }
        val swing = when (state) {
            PlayerState.RUNNING -> stride * safeHeight * 0.028f
            PlayerState.JUMP_START -> -safeHeight * 0.018f
            PlayerState.JUMPING -> (-0.6f - verticalInfluence) * safeHeight * 0.026f
            PlayerState.APEX -> floatPulse * safeHeight * 0.022f
            PlayerState.FALLING -> (0.5f + fallInfluence) * safeHeight * 0.028f
            PlayerState.LANDING -> safeHeight * 0.024f
            PlayerState.DUCKING -> stride * safeHeight * 0.014f
            PlayerState.BLOOM -> stride * safeHeight * 0.038f
            PlayerState.STUMBLE -> stride * safeHeight * 0.042f
            PlayerState.REST -> 0f
        }
        val trailLift = when (state) {
            PlayerState.RUNNING -> safeHeight * 0.012f + abs(stride) * safeHeight * 0.010f
            PlayerState.JUMP_START -> safeHeight * 0.026f
            PlayerState.JUMPING -> safeHeight * 0.034f
            PlayerState.APEX -> safeHeight * 0.028f
            PlayerState.FALLING -> safeHeight * 0.018f
            PlayerState.LANDING -> safeHeight * 0.014f
            PlayerState.DUCKING -> safeHeight * 0.006f
            PlayerState.BLOOM -> safeHeight * 0.038f
            PlayerState.STUMBLE -> safeHeight * 0.020f
            PlayerState.REST -> 0f
        }
        val invincibleLift = if (isInvincible || state == PlayerState.BLOOM) safeHeight * 0.006f else 0f

        return PlayerSecondaryMotionState(
            bodyTiltDegrees = finiteOrZero(baseTilt),
            bodyLiftPx = finiteOrZero(baseLift - invincibleLift),
            costumeSwingPx = finiteOrZero(swing),
            costumeTrailLiftPx = finiteOrZero(trailLift + invincibleLift),
            headOffsetPx = finiteOrZero(baseLift * 0.38f)
        )
    }

    private fun finiteOrZero(value: Float): Float = value.takeIf { it.isFinite() } ?: 0f
}
