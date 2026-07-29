package com.anurag9000.forestrun.entities

/**
 * Every locomotion/animation state the player character can occupy.
 * The state machine lives in [Player].
 *
 * Enum order is persistence-sensitive because legacy ghost frames store
 * [Enum.ordinal]. Do not remove or reorder entries without a ghost-schema
 * migration. [BLOOM] is therefore retained as a reserved legacy ordinal even
 * though current Bloom power is an orthogonal flag.
 */
enum class PlayerState {

    /** Running normally on the ground. Loop animation plays. */
    RUNNING,

    /** Short launch squash while the already-applied jump velocity begins ascent. */
    JUMP_START,

    /** Ascending after a jump. Y-velocity is negative (moving up). */
    JUMPING,

    /**
     * Peak of the arc. Y-velocity is near zero.
     * Gravity is reduced for [Player.APEX_GRAVITY_DURATION_S] to give a floaty
     * but deterministic apex.
     */
    APEX,

    /** Descending. Full gravity applies again. */
    FALLING,

    /** Brief landing squash before transitioning automatically to [RUNNING]. */
    LANDING,

    /** Player is ducking under a hazard with a compressed vertical hitbox. */
    DUCKING,

    /**
     * Reserved legacy ghost/debug ordinal. Current Bloom never transitions the
     * player into this state; [Player.isInvincible] is orthogonal to locomotion.
     * Legacy forced values are migrated back to RUNNING/FALLING by [Player].
     */
    @Deprecated("Bloom is an orthogonal power flag; retained for ghost ordinal compatibility")
    BLOOM,

    /** Non-lethal impact recovery before returning to [RUNNING]. */
    STUMBLE,

    /** Game-over/rest animation state. */
    REST
}
