package com.yourname.forest_run.systems

import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.engine.RunState

/**
 * Records a [GhostFrame] every game-loop frame during [RunState.PLAYING].
 *
 * At run end, the full frame list is available via [frames].
 * [SaveManager] decides whether to persist it (only if this was a new best distance).
 *
 * Memory: ~40 bytes per frame × 60 fps × typical 120s run ≈ ~280 KB.
 * We cap at 10,000 frames (~167 seconds at 60 fps) which is more than any
 * realistic run; older frames beyond the cap are never reached in playback.
 *
 * Usage:
 *   // Every PLAYING frame in GameView.update():
 *   ghostRecorder.record(deltaTime, player)
 *
 *   // On run end (HIT):
 *   val frames = ghostRecorder.frames
 *   ghostRecorder.reset()
 */
class GhostRecorder {

    companion object {
        const val MAX_FRAMES = 10_000
    }

    /** Accumulated run time since last reset. */
    private var elapsed = 0f

    /** All frames recorded this run (reset on each new run). */
    val frames = ArrayList<GhostFrame>(4096)

    // ── API ───────────────────────────────────────────────────────────────

    /**
     * Call every PLAYING frame. Records the player's current state.
     */
    fun record(deltaTime: Float, player: Player) {
        if (frames.size >= MAX_FRAMES) return
        elapsed += deltaTime
        frames.add(
            GhostFrame(
                t            = elapsed,
                x            = player.x,
                y            = player.y,
                stateOrdinal = player.state.ordinal,
                scaleX       = player.scaleX,
                scaleY       = player.scaleY
            )
        )
    }

    /** The total run time recorded so far. */
    val runDuration: Float get() = elapsed

    /** Returns a defensive copy safe to pass to SaveManager / GhostPlayer. */
    fun snapshot(): List<GhostFrame> = frames.toList()

    /** Call at the start of each new run. */
    fun reset() {
        frames.clear()
        elapsed = 0f
    }
}
