package com.anurag9000.forestrun.systems

import com.anurag9000.forestrun.engine.RunState
import com.anurag9000.forestrun.entities.Player

/**
 * Records a time-stamped player pose during [RunState.PLAYING].
 *
 * Sampling is capped at 30 Hz rather than the render rate. This keeps playback
 * smooth while allowing a 20-minute run within a bounded ~864 KB raw payload
 * (36,000 frames × 24 bytes) instead of silently ending after ~167 seconds.
 */
class GhostRecorder {
    companion object {
        const val SAMPLE_RATE_HZ = 30
        const val SAMPLE_INTERVAL_S = 1f / SAMPLE_RATE_HZ
        const val MAX_DURATION_S = 20 * 60
        const val MAX_FRAMES = SAMPLE_RATE_HZ * MAX_DURATION_S

        private const val INITIAL_CAPACITY = SAMPLE_RATE_HZ * 180
    }

    private var elapsed = 0f
    private var lastSampleTime = Float.NEGATIVE_INFINITY
    private var activeFrames = ArrayList<GhostFrame>(INITIAL_CAPACITY)

    /** Read-only view of the currently recording run. */
    val frames: List<GhostFrame>
        get() = activeFrames

    fun record(deltaTime: Float, player: Player) {
        if (!deltaTime.isFinite() || deltaTime <= 0f || activeFrames.size >= MAX_FRAMES) return

        elapsed = (elapsed.toDouble() + deltaTime.toDouble())
            .coerceAtMost(MAX_DURATION_S.toDouble())
            .toFloat()
        if (activeFrames.isNotEmpty() && elapsed - lastSampleTime < SAMPLE_INTERVAL_S) return

        val frame = GhostFrame(
            t = elapsed,
            x = player.x,
            y = player.y,
            stateOrdinal = player.state.ordinal,
            scaleX = player.scaleX,
            scaleY = player.scaleY
        )
        if (!GhostRunValidator.isValidFrame(frame, lastSampleTime)) return

        activeFrames.add(frame)
        lastSampleTime = elapsed
    }

    val runDuration: Float
        get() = elapsed

    /**
     * Stable defensive snapshot for diagnostics and tests. The gameplay death
     * path uses [detachSnapshot] to avoid copying tens of thousands of entries.
     */
    fun snapshot(): List<GhostFrame> = activeFrames.toList()

    /**
     * Transfers ownership of the completed run in O(1), immediately preparing
     * an independent buffer for the next run. The returned list is never
     * mutated by this recorder again and is therefore safe for async storage.
     */
    fun detachSnapshot(): List<GhostFrame> {
        if (activeFrames.isEmpty()) {
            resetClock()
            return emptyList()
        }

        val completedRun = activeFrames
        activeFrames = ArrayList(INITIAL_CAPACITY)
        resetClock()
        return completedRun
    }

    fun reset() {
        activeFrames.clear()
        resetClock()
    }

    private fun resetClock() {
        elapsed = 0f
        lastSampleTime = Float.NEGATIVE_INFINITY
    }
}
