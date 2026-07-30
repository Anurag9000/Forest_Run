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
 * conservative full-jump envelope and the visible surface.
 */
object SeedOrbSpawnPolicy {
    private const val PLAYER_CLEARANCE_PX = 24f
    private const val JUMP_SAFETY_FACTOR = 0.75f
    private const val VISIBLE_MARGIN_PX = 8f

    fun forCleanPass(
        encounterBounds: RectF,
        playerBounds: RectF,
        playerGroundY: Float,
        screenWidth: Float,
        screenHeight: Float
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

        val minimumAheadX = finiteOr(playerBounds.right, 0f) +
            maxOf(120f, safeScreenWidth * 0.08f)
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
