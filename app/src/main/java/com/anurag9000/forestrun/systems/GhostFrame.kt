package com.anurag9000.forestrun.systems

import com.anurag9000.forestrun.engine.SaveManager
import com.anurag9000.forestrun.entities.PlayerState

/**
 * One sampled snapshot of player position, scale, and animation state.
 *
 * [GhostRecorder] samples at 30 Hz rather than once per rendered frame. The
 * in-memory [stateOrdinal] addresses [PlayerState] for playback. Versioned disk
 * persistence maps it through [GhostStateCodec], so the current file format is
 * independent of future enum ordering while legacy ordinal files remain readable.
 * [SaveManager] persists a detached recording only when it becomes the best run.
 */
data class GhostFrame(
    /** Elapsed time from run start in seconds. */
    val t: Float,
    /** Player world-X in px; normally the fixed horizontal runner position. */
    val x: Float,
    /** Player world-Y in px, including jump, fall, duck, and secondary motion. */
    val y: Float,
    /** Current in-memory [PlayerState] ordinal used by playback. */
    val stateOrdinal: Int,
    /** Horizontal player scale, preserving squash/stretch in playback. */
    val scaleX: Float,
    /** Vertical player scale, preserving squash/stretch in playback. */
    val scaleY: Float
)
