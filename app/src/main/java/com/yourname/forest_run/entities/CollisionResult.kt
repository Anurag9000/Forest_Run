package com.yourname.forest_run.entities

/**
 * Result of a collision check between the Player and an Entity.
 */
enum class CollisionResult {
    /** No collision occurred. */
    NONE,

    /** Player made physical contact with a hazard hitbox. Results in REST/Game Over. */
    HIT,

    /** Player passed extremely close (within 12px) of a hazard but did not hit it. */
    MERCY_MISS
}
