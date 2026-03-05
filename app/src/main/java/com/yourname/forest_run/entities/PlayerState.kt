package com.yourname.forest_run.entities

/**
 * Every state the player character can be in.
 * The state machine lives in [Player].
 */
enum class PlayerState {

    /** Running normally on the ground. Loop animation plays. */
    RUNNING,

    /**
     * 2-frame "windup" squash immediately before launch.
     * The character compresses downward (scaleY 0.8) to build tension.
     */
    JUMP_START,

    /** Ascending after a jump. Y-velocity is negative (moving up). */
    JUMPING,

    /**
     * Peak of the arc. Y-velocity is near zero.
     * Gravity is reduced for [Player.APEX_GRAVITY_DURATION_S] to give a
     * floaty, Ghibli-like feel.
     */
    APEX,

    /** Descending. Full gravity applies again. */
    FALLING,

    /**
     * 3-frame landing squash after touching the ground.
     * Transitions automatically back to [RUNNING].
     */
    LANDING,

    /**
     * Player is sliding/ducking under a hazard.
     * Hitbox is compressed vertically.
     * Sustained by holding the duck input.
     */
    DUCKING,

    /**
     * Bloom State – 5-second invincibility power-up.
     * Transitions back to [RUNNING] after the timer expires.
     */
    BLOOM,

    /**
     * Game-over / rest state.
     * Character sits on the ground; game loop is still running (background scrolls).
     */
    REST
}
