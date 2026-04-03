package com.yourname.forest_run.entities

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

    fun resolve(
        state: PlayerState,
        velocityY: Float,
        bodyHeight: Float,
        elapsed: Float,
        isInvincible: Boolean
    ): PlayerSecondaryMotionState {
        val stride = sin(elapsed * if (state == PlayerState.BLOOM) 12f else 8.2f)
        val floatPulse = sin(elapsed * if (state == PlayerState.BLOOM) 7.2f else 4.8f)
        val verticalInfluence = (velocityY / 1200f).coerceIn(-1f, 1f)
        val fallInfluence = (abs(velocityY) / 1500f).coerceIn(0f, 1f)

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
            PlayerState.RUNNING -> stride * bodyHeight * 0.010f
            PlayerState.JUMP_START -> -bodyHeight * 0.026f
            PlayerState.JUMPING -> -bodyHeight * (0.030f + fallInfluence * 0.010f)
            PlayerState.APEX -> floatPulse * bodyHeight * 0.014f
            PlayerState.FALLING -> bodyHeight * 0.016f
            PlayerState.LANDING -> bodyHeight * 0.020f
            PlayerState.DUCKING -> bodyHeight * 0.012f
            PlayerState.BLOOM -> floatPulse * bodyHeight * 0.024f
            PlayerState.STUMBLE -> stride * bodyHeight * 0.018f
            PlayerState.REST -> 0f
        }
        val swing = when (state) {
            PlayerState.RUNNING -> stride * bodyHeight * 0.028f
            PlayerState.JUMP_START -> -bodyHeight * 0.018f
            PlayerState.JUMPING -> (-0.6f - verticalInfluence) * bodyHeight * 0.026f
            PlayerState.APEX -> floatPulse * bodyHeight * 0.022f
            PlayerState.FALLING -> (0.5f + fallInfluence) * bodyHeight * 0.028f
            PlayerState.LANDING -> bodyHeight * 0.024f
            PlayerState.DUCKING -> stride * bodyHeight * 0.014f
            PlayerState.BLOOM -> stride * bodyHeight * 0.038f
            PlayerState.STUMBLE -> stride * bodyHeight * 0.042f
            PlayerState.REST -> 0f
        }
        val trailLift = when (state) {
            PlayerState.RUNNING -> bodyHeight * 0.012f + abs(stride) * bodyHeight * 0.010f
            PlayerState.JUMP_START -> bodyHeight * 0.026f
            PlayerState.JUMPING -> bodyHeight * 0.034f
            PlayerState.APEX -> bodyHeight * 0.028f
            PlayerState.FALLING -> bodyHeight * 0.018f
            PlayerState.LANDING -> bodyHeight * 0.014f
            PlayerState.DUCKING -> bodyHeight * 0.006f
            PlayerState.BLOOM -> bodyHeight * 0.038f
            PlayerState.STUMBLE -> bodyHeight * 0.020f
            PlayerState.REST -> 0f
        }
        val invincibleLift = if (isInvincible || state == PlayerState.BLOOM) bodyHeight * 0.006f else 0f

        return PlayerSecondaryMotionState(
            bodyTiltDegrees = baseTilt,
            bodyLiftPx = baseLift - invincibleLift,
            costumeSwingPx = swing,
            costumeTrailLiftPx = trailLift + invincibleLift,
            headOffsetPx = baseLift * 0.38f
        )
    }
}
