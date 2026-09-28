package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.Player

/** Converts readability spacing and independent action-reaction lead into world distance. */
object SpawnPacing {
    private const val MIN_EFFECTIVE_SPEED_PX_PER_SEC = 1f
    // Source-side controller assumptions already used by action/Orb analysis.
    // They are not measured human/device latency.
    private const val GESTURE_DECISION_SECONDS = 0.075f
    private const val ACTION_SAFETY_SECONDS = 0.08f
    // Hedgehog is the fastest ordinary family that starts approaching
    // immediately on its creation frame (1.15x world scroll). Staged divers
    // begin their attack only after their own telegraph/lock.
    private const val MAX_IMMEDIATE_CREATION_APPROACH_MULTIPLIER = 1.15f


    /**
     * Minimum player-to-new-origin lead for an ordinary random encounter.
     *
     * Origin-to-origin spacing cannot by itself guarantee that a compact
     * surface gives the Player enough world distance for a newly staged
     * encounter. Use the real maximum admitted Player step and sampled
     * full-jump apex as a conservative cross-family reaction envelope.
     * One additional admitted frame accounts for the fact that a newly
     * created entity is updated later in the same EntityManager tick.
     */
    fun minimumRandomEncounterLeadPx(scrollSpeedPxPerSec: Float): Float {
        val speed = scrollSpeedPxPerSec
            .takeIf { it.isFinite() && it > 0f }
            ?.coerceAtMost(GameConstants.MAX_SCROLL_SPEED)
            ?: GameConstants.MAX_SCROLL_SPEED
        val apex = EncounterActionFeasibility.observe(
            leadDistancePx = Float.MAX_VALUE,
            approachSpeedPxPerSec = speed,
            requiredVerticalClearancePx = 0f,
            jumpUpwardSpeedPxPerSec = -Player.MAX_JUMP_FORCE,
            gravityPxPerSecSquared = Player.GRAVITY,
            gestureDecisionSeconds = 0f
        ).maximumBallisticRisePx
        val fullJump = EncounterActionFeasibility.observe(
            leadDistancePx = Float.MAX_VALUE,
            approachSpeedPxPerSec = speed,
            requiredVerticalClearancePx = apex,
            jumpUpwardSpeedPxPerSec = -Player.MAX_JUMP_FORCE,
            gravityPxPerSecSquared = Player.GRAVITY,
            gestureDecisionSeconds = GESTURE_DECISION_SECONDS,
            safetyMarginSeconds = ACTION_SAFETY_SECONDS
        )
        val actionSeconds = (
            GESTURE_DECISION_SECONDS.toDouble() +
                ACTION_SAFETY_SECONDS.toDouble() +
                fullJump.timeToRequiredRiseSeconds.toDouble() +
                FrameInputAdmission.MAX_DELTA_SECONDS.toDouble() *
                    MAX_IMMEDIATE_CREATION_APPROACH_MULTIPLIER.toDouble()
            ).coerceAtMost(Float.MAX_VALUE.toDouble())
        return (actionSeconds * speed.toDouble())
            .coerceIn(0.0, Float.MAX_VALUE.toDouble())
            .toFloat()
    }

    fun requiredGapPx(
        distanceMetres: Float,
        runTimeSeconds: Float,
        scrollSpeedPxPerSec: Float
    ): Float {
        val defaultGap = DifficultyScaler.getSpawnGapPx(distanceMetres)
            .takeIf { it.isFinite() && it >= 0f }
            ?: GameConstants.SPAWN_GAP_MAX_PX
        val speed = scrollSpeedPxPerSec
            .takeIf { it.isFinite() && it > 0f }
            ?.coerceAtLeast(MIN_EFFECTIVE_SPEED_PX_PER_SEC)
            ?: MIN_EFFECTIVE_SPEED_PX_PER_SEC
        val defaultInterval = (defaultGap / speed)
            .takeIf { it.isFinite() && it >= 0f }
            ?: 0f
        val adjustedInterval = OpeningReadabilityGuide.adjustedSpawnInterval(
            runTimeSeconds = runTimeSeconds,
            defaultInterval = defaultInterval
        )
        val equivalentDistance = (adjustedInterval.toDouble() * speed.toDouble())
            .coerceIn(0.0, Float.MAX_VALUE.toDouble())
            .toFloat()
        return maxOf(defaultGap, equivalentDistance)
    }
}
