package com.yourname.forest_run.engine

/**
 * Global tuning constants shared across the entire game.
 * A single source of truth — changing a value here adjusts everything.
 */
object GameConstants {

    // ── Scroll speed ───────────────────────────────────────────────────────
    /** Starting / minimum scroll speed in pixels per second. */
    const val BASE_SCROLL_SPEED  = 600f

    /** Maximum scroll speed (reached at ~3 000m). */
    const val MAX_SCROLL_SPEED   = 1_800f

    /** Speed added per metre of distance run. */
    const val SPEED_PER_METRE    = 0.25f     // gentle ramp

    // ── Seeds & Bloom ──────────────────────────────────────────────────────
    /** Seeds required to fill the Bloom Meter. */
    const val BLOOM_SEED_COUNT   = 10

    /** Duration of the Bloom invincibility in seconds. */
    const val BLOOM_DURATION_S   = 5f

    // ── Biomes ────────────────────────────────────────────────────────────
    /** Metres between biome transitions. */
    const val BIOME_LENGTH_M       = 500f
    /** Canonical alias used by BiomeManager and Biome enum. */
    const val BIOME_LENGTH_METRES  = 500f

    // ── Score ─────────────────────────────────────────────────────────────
    /** Points per metre of distance travelled. */
    const val POINTS_PER_METRE   = 1f

    // ── Wind ──────────────────────────────────────────────────────────────
    /** Base global wind speed multiplier for SwayComponent. */
    const val BASE_WIND_SPEED    = 1.0f
}
