package com.yourname.forest_run.engine

/**
 * Calculates a horizontal offset using a sine wave to simulate wind.
 * Attached to Entity subclasses that need procedural animation (e.g. Trees, Flora).
 *
 * @param speed     How fast the sway cycles (frequency).
 * @param intensity How far it sways in pixels (amplitude).
 */
class SwayComponent(
    private val speed: Float,
    private val intensity: Float
) {
    private var time: Float = 0f

    /**
     * Call every frame to advance the wave and get the pixel offset.
     * @param globalWindMultiplier Game-wide modifier (e.g., higher during Cherry Blossom event).
     */
    fun getOffset(deltaTime: Float, globalWindMultiplier: Float = 1.0f): Float {
        time += deltaTime * speed * globalWindMultiplier
        // sin() returns -1.0 to 1.0. Multiply by intensity for pixel offset.
        return Math.sin(time.toDouble()).toFloat() * intensity
    }
}
