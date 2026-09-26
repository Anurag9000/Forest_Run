package com.anurag9000.forestrun.entities

/**
 * Result of a collision check between the Player and an Entity.
 */
enum class CollisionResult {
    /** No collision occurred. */
    NONE,

    /** Player made physical contact with a hazard hitbox. Results in REST/Game Over. */
    HIT,

    /** Player currently occupies the padded mercy band; safe passage must still be proven. */
    MERCY_MISS,

    /** Player hit a non-lethal hazard (e.g., Fox/Wolf) and stumbles instead of dying. */
    STUMBLE
}
