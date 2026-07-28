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
    }

    private var elapsed = 0f
    private var lastSampleTime = Float.NEGATIVE_INFINITY

    /** Mutable internally; callers should use [snapshot] at run end. */
    val frames = ArrayList<GhostFrame>(SAMPLE_RATE_HZ * 180)

    fun record(deltaTime: Float, player: Player) {
        if (!deltaTime.isFinite() || deltaTime <= 0f || frames.size >= MAX_FRAMES) return

        elapsed = (elapsed + deltaTime).coerceAtMost(MAX_DURATION_S.toFloat())
        if (frames.isNotEmpty() && elapsed - lastSampleTime < SAMPLE_INTERVAL_S) return

        frames.add(
            GhostFrame(
                t = elapsed,
                x = player.x,
                y = player.y,
                stateOrdinal = player.state.ordinal,
                scaleX = player.scaleX,
                scaleY = player.scaleY
            )
        )
        lastSampleTime = elapsed
    }

    val runDuration: Float
        get() = elapsed

    /**
     * Returns the current frame list without duplicating tens of thousands of
     * objects. SaveManager consumes it synchronously before GameView resets the
     * recorder, so this read-only view is safe for the existing call path.
     */
    fun snapshot(): List<GhostFrame> = frames

    fun reset() {
        frames.clear()
        elapsed = 0f
        lastSampleTime = Float.NEGATIVE_INFINITY
    }
}
