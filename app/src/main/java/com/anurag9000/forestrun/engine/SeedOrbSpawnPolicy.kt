package com.anurag9000.forestrun.engine

import android.graphics.RectF
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.systems.SeedOrb
import com.anurag9000.forestrun.systems.SeedOrbManager

/** A deterministic staging point whose full random spawn band remains reachable. */
data class SeedOrbStagingPoint(
    val centreX: Float,
    val topY: Float,
    val minimumPossibleCentreY: Float,
    val maximumPossibleCentreY: Float
)

/**
 * Converts encounter geometry into a visible, physically reachable Seed Orb band.
 *
 * [SeedOrbManager] subtracts a random vertical offset from `topY`. The policy
 * therefore clamps `topY` so both offset extremes remain inside the player's
 * conservative full-jump envelope and the visible surface. Its horizontal
 * staging also gives the highest possible Orb enough time to meet a real jump,
 * accounting for the full random X jitter, bob and pickup radius. Vertical
 * possibility without approach time is not an attainable reward.
 */
object SeedOrbSpawnPolicy {
    private const val PLAYER_CLEARANCE_PX = 24f
    private const val JUMP_SAFETY_FACTOR = 0.75f
    private const val VISIBLE_MARGIN_PX = 8f
    // Source-side response assumptions, not measured physical-device latency.
    // Human timing acceptance remains a separate requirement.
    private const val GESTURE_DECISION_SECONDS = 0.075f
    private const val JUMP_MARGIN_SECONDS = 0.08f

    fun forCleanPass(
        encounterBounds: RectF,
        playerBounds: RectF,
        playerGroundY: Float,
        screenWidth: Float,
        screenHeight: Float,
        scrollSpeedPxPerSec: Float = GameConstants.BASE_SCROLL_SPEED
    ): SeedOrbStagingPoint {
        val safeScreenWidth = screenWidth.takeIf { it.isFinite() && it > 0f } ?: 1f
        val safeScreenHeight = screenHeight.takeIf { it.isFinite() && it > 0f } ?: 1f
        val safeGroundY = playerGroundY.takeIf { it.isFinite() && it > 0f }
            ?: safeScreenHeight * 0.82f

        val visibleMargin = SeedOrb.RADIUS + SeedOrb.HALO_MARGIN + VISIBLE_MARGIN_PX
        val ballisticRise =
            (Player.MAX_JUMP_FORCE.toDouble() * Player.MAX_JUMP_FORCE.toDouble() /
                (2.0 * Player.GRAVITY.toDouble())).toFloat()
        val conservativeRise = ballisticRise * JUMP_SAFETY_FACTOR

        val visibleTop = visibleMargin.coerceAtMost(safeScreenHeight / 2f)
        val visibleBottom = (safeScreenHeight - visibleMargin).coerceAtLeast(visibleTop)
        val reachableTop = (safeGroundY - Player.BASE_HEIGHT - conservativeRise)
            .coerceIn(visibleTop, visibleBottom)
        val reachableBottom = (safeGroundY - Player.HITBOX_INSET)
            .coerceIn(reachableTop, visibleBottom)

        // The manager later subtracts [SPAWN_HEIGHT_MIN, SPAWN_HEIGHT_MAX].
        // Clamp the supplied top anchor so every random result stays reachable.
        val minimumTopAnchor = reachableTop + SeedOrbManager.SPAWN_HEIGHT_MAX
        val maximumTopAnchor = reachableBottom + SeedOrbManager.SPAWN_HEIGHT_MIN
        val rawTopAnchor = minOf(
            finiteOr(encounterBounds.top, reachableBottom),
            finiteOr(playerBounds.top, reachableBottom) - PLAYER_CLEARANCE_PX
        )
        val topAnchor = if (minimumTopAnchor <= maximumTopAnchor) {
            rawTopAnchor.coerceIn(minimumTopAnchor, maximumTopAnchor)
        } else {
            // Degenerate tiny surfaces still receive a finite centre-band anchor.
            (reachableTop + reachableBottom) / 2f +
                (SeedOrbManager.SPAWN_HEIGHT_MIN + SeedOrbManager.SPAWN_HEIGHT_MAX) / 2f
        }

        // The highest random centre is also the earliest one a full jump
        // must reach. Bobbing can lift it another BOB_AMP pixels, while the
        // orb's actual core radius allows collection before centre alignment.
        // Use the existing independent action model rather than an invented
        // second gravity curve. A malformed speed takes the supported maximum.
        val highestOrbCentreY = topAnchor - SeedOrbManager.SPAWN_HEIGHT_MAX
        val standingPlayerTop = safeGroundY - Player.BASE_HEIGHT + Player.HITBOX_INSET
        val requiredRise = (
            standingPlayerTop -
                (highestOrbCentreY - SeedOrb.BOB_AMP + SeedOrb.RADIUS)
            ).coerceAtLeast(0f)
        val riseObservation = EncounterActionFeasibility.observe(
            leadDistancePx = 0f,
            approachSpeedPxPerSec = GameConstants.BASE_SCROLL_SPEED,
            requiredVerticalClearancePx = requiredRise,
            jumpUpwardSpeedPxPerSec = -Player.MAX_JUMP_FORCE,
            gravityPxPerSecSquared = Player.GRAVITY,
            gestureDecisionSeconds = 0f
        )
        val riseSeconds = riseObservation.timeToRequiredRiseSeconds
            .takeIf { it.isFinite() && it < Float.MAX_VALUE }
            ?: Player.MAX_HOLD_DURATION_S
        val approachSpeed = scrollSpeedPxPerSec
            .takeIf { it.isFinite() && it > 0f }
            ?.coerceAtMost(GameConstants.MAX_SCROLL_SPEED)
            ?: GameConstants.MAX_SCROLL_SPEED
        val minimumPickupLeadPx = (
            (GESTURE_DECISION_SECONDS + JUMP_MARGIN_SECONDS + riseSeconds).toDouble() *
                approachSpeed.toDouble() +
                SeedOrb.RADIUS.toDouble() +
                SeedOrbManager.SPAWN_HORIZONTAL_JITTER_HALF_SPAN_PX.toDouble()
            ).coerceAtMost(Float.MAX_VALUE.toDouble()).toFloat()
        val minimumAheadX = finiteOr(playerBounds.right, 0f) +
            maxOf(120f, safeScreenWidth * 0.08f, minimumPickupLeadPx)
        val encounterCentreX = if (encounterBounds.left.isFinite() && encounterBounds.right.isFinite()) {
            (encounterBounds.left.toDouble() + encounterBounds.right.toDouble())
                .div(2.0)
                .coerceIn(-Float.MAX_VALUE.toDouble(), Float.MAX_VALUE.toDouble())
                .toFloat()
        } else {
            minimumAheadX
        }
        val centreX = maxOf(encounterCentreX, minimumAheadX)
            .coerceIn(-Float.MAX_VALUE, Float.MAX_VALUE)

        return SeedOrbStagingPoint(
            centreX = centreX,
            topY = topAnchor,
            minimumPossibleCentreY = topAnchor - SeedOrbManager.SPAWN_HEIGHT_MAX,
            maximumPossibleCentreY = topAnchor - SeedOrbManager.SPAWN_HEIGHT_MIN
        )
    }

    private fun finiteOr(value: Float, fallback: Float): Float =
        value.takeIf { it.isFinite() } ?: fallback
}
