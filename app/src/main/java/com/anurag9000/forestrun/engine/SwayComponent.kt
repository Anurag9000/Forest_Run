package com.anurag9000.forestrun.engine

import kotlin.math.PI
import kotlin.math.sin

/**
 * Calculates a horizontal offset using a sine wave to simulate wind.
 * Attached to Entity subclasses that need procedural animation (e.g. Trees, Flora).
 *
 * @param speed How fast the sway cycles (frequency).
 * @param intensity How far it sways in pixels (amplitude).
 */
class SwayComponent(
    private val speed: Float,
    private val intensity: Float
) {
    companion object {
        private const val DEFAULT_WIND_MULTIPLIER = 1f
        private const val MAX_WIND_MULTIPLIER = 8f
        private val FULL_CYCLE_RADIANS = 2.0 * PI
    }

    init {
        require(speed.isFinite() && speed >= 0f) {
            "speed must be finite and non-negative"
        }
        require(intensity.isFinite() && intensity >= 0f) {
            "intensity must be finite and non-negative"
        }
    }

    private var phaseRadians: Double = 0.0

    /**
     * Advances the wave and returns its bounded horizontal pixel offset.
     * Invalid or non-positive frame deltas are treated as no-ops. Invalid or
     * negative wind values fall back to normal wind rather than reversing time.
     */
    fun getOffset(deltaTime: Float, globalWindMultiplier: Float = DEFAULT_WIND_MULTIPLIER): Float {
        if (deltaTime.isFinite() && deltaTime > 0f) {
            val wind = if (globalWindMultiplier.isFinite() && globalWindMultiplier >= 0f) {
                globalWindMultiplier.coerceAtMost(MAX_WIND_MULTIPLIER)
            } else {
                DEFAULT_WIND_MULTIPLIER
            }
            val increment = deltaTime.toDouble() * speed.toDouble() * wind.toDouble()
            phaseRadians = (phaseRadians + increment) % FULL_CYCLE_RADIANS
        }

        return (sin(phaseRadians) * intensity.toDouble())
            .coerceIn(-intensity.toDouble(), intensity.toDouble())
            .toFloat()
    }

    /** Restores the initial zero phase for deterministic resets and tests. */
    fun reset() {
        phaseRadians = 0.0
    }
}
