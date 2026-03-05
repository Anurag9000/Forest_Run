package com.yourname.forest_run.engine

/**
 * All possible top-level states of a single run.
 *
 * GameView holds a [RunState] value and uses it to decide:
 *  - Whether to run physics / entity updates (only in PLAYING / BLOOM states).
 *  - Whether to draw the GameOver overlay (DYING / GAME_OVER).
 *  - When to trigger RunResetManager (on RESTARTING).
 */
enum class RunState {
    /** Normal gameplay — all systems update. */
    PLAYING,

    /**
     * Player hit something. Screen shakes, player REST animation plays.
     * Lasts [RunResetManager.DYING_DURATION_S] seconds, then transitions to GAME_OVER.
     * Gameplay is frozen (no entity spawns, no score tick).
     */
    DYING,

    /**
     * Dying animation done. GameOverScreen overlay is shown.
     * Tap anywhere to begin RESTARTING.
     */
    GAME_OVER,

    /**
     * Tap received. Fade-out begins. After [RunResetManager.RESTART_FADE_S]
     * all systems are reset and state returns to PLAYING.
     */
    RESTARTING
}
