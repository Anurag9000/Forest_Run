package com.yourname.forest_run.systems

/**
 * A single recorded snapshot of the player's position and animation state.
 *
 * Kept as a plain data class with no Android dependencies for easy serialization.
 *
 * Stored in [GhostRecorder] every frame, then persisted by [SaveManager] if
 * the run sets a new personal best distance.
 */
data class GhostFrame(
    /** Elapsed time from run start in seconds (used for playback interpolation). */
    val t: Float,
    /** Player world-X in px (screen-relative: always the fixed horizontal position). */
    val x: Float,
    /** Player world-Y in px (changes during jumps/ducks). */
    val y: Float,
    /** Current [PlayerState] ordinal — used to drive the ghost sprite. */
    val stateOrdinal: Int,
    /** ScaleX applied to player — preserves squash/stretch in playback. */
    val scaleX: Float,
    /** ScaleY applied to player — preserves squash/stretch in playback. */
    val scaleY: Float
)
