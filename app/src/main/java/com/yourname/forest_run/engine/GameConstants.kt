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
    /** Minimum distance between spawned objects (metres). Lower = denser. */
    const val SPAWN_MIN_GAP_M    = 3.5f   // Phase 25: new constant

    /**
     * Multiplier applied to per-entity base spawn rate at max difficulty.
     * 1.0 = normal density, 2.0 = twice as many entities at 3000m.
     */
    const val SPAWN_DENSITY_SCALE = 1.8f  // Phase 25: new constant

    // ── Biomes ───────────────────────────────────────────────────────────
    /** Metres between biome transitions. */
    const val BIOME_LENGTH_M       = 500f
    /** Canonical alias used by BiomeManager and Biome enum. */
    const val BIOME_LENGTH_METRES  = 500f

    // ── Score ─────────────────────────────────────────────────────────────
    /** Points per metre of distance travelled. */
    const val POINTS_PER_METRE   = 1.5f   // Phase 25: 1→1.5 (scores feel bigger)

    // ── Wind ──────────────────────────────────────────────────────────────
    /** Base global wind speed multiplier for SwayComponent. */
    const val BASE_WIND_SPEED    = 1.0f
}
