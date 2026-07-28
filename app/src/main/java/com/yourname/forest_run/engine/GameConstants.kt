package com.yourname.forest_run.engine

/**
 * Global tuning constants shared across the entire game.
 * A single source of truth — changing a value here adjusts everything.
 *
 * Phase 25 balance pass values — see commit message for rationale.
 */
object GameConstants {

    // ── Scroll speed ─────────────────────────────────────────────────────
    /** Starting / minimum scroll speed in pixels per second. */
    const val BASE_SCROLL_SPEED  = 650f   // Phase 25: 600→650 (snappier start)

    /** Maximum scroll speed — reached ~2500m. */
    const val MAX_SCROLL_SPEED   = 2_000f // Phase 25: 1800→2000 (higher ceiling)

    /** Speed added per metre of distance run. */
    const val SPEED_PER_METRE    = 0.22f  // Phase 25: 0.25→0.22 (gentler early ramp)

    // ── Seeds & Bloom ────────────────────────────────────────────────────
    /** Seeds required to fill the Bloom Meter. */
    const val BLOOM_SEED_COUNT   = 8      // Phase 25: 10→8 (bloom more frequent)

    /** Duration of the Bloom invincibility in seconds. */
    const val BLOOM_DURATION_S   = 6f     // Phase 25: 5s→6s (more satisfying)

    // ── Mercy System ─────────────────────────────────────────────────────
    /**
     * Fraction of entity hitbox width/height that counts as a mercy-miss
     * zone (player grazes but is not fully overlapping).
     * 0.18 = 18% margin on each side — tuned to feel fair, not cheap.
     */
    const val MERCY_WINDOW_FRAC  = 0.18f  // Phase 25: new constant

    // ── Entity Spawning ──────────────────────────────────────────────────
    /** Spawn-origin distance at the beginning of a run. */
    const val SPAWN_GAP_MAX_PX = 1_100f

    /** Tightest permitted spawn-origin distance at full pacing difficulty. */
    const val SPAWN_GAP_MIN_PX = 780f

    /** Distance over which the world-space gap tightens to its minimum. */
    const val SPAWN_GAP_RAMP_METRES = 2_000f

    // ── Biomes ───────────────────────────────────────────────────────────
    /** Metres between biome transitions. */
    const val BIOME_LENGTH_M       = 500f
    /** Canonical alias used by BiomeManager and Biome enum. */
    const val BIOME_LENGTH_METRES  = 500f

    // ── Score ────────────────────────────────────────────────────────────
    /** Points per metre of distance travelled. */
    const val POINTS_PER_METRE   = 1.5f

    // ── Wind ─────────────────────────────────────────────────────────────
    /** Base global wind speed multiplier for SwayComponent. */
    const val BASE_WIND_SPEED    = 1.0f
}
