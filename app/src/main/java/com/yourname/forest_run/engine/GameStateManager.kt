package com.yourname.forest_run.engine

import android.content.Context
import android.content.SharedPreferences
import com.yourname.forest_run.utils.MathUtils

/**
 * Single source of truth for all mutable game state.
 *
 * Phase 5 (now): score, distance, seeds, bloom meter, scroll speed, high score.
 * Phase 22 will expand this with the full state machine (MENU/PLAYING/BLOOM/REST).
 *
 * GameView owns one instance and passes it to every subsystem that needs to read
 * or write game state.
 */
class GameStateManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("forest_run_save", Context.MODE_PRIVATE)

    // -----------------------------------------------------------------------
    // Scroll & distance
    // -----------------------------------------------------------------------

    /** Current scroll speed in pixels/second. */
    var scrollSpeed: Float = GameConstants.BASE_SCROLL_SPEED
        private set

    /** Total metres run this session. */
    var distanceMetres: Float = 0f
        private set

    // -----------------------------------------------------------------------
    // Score
    // -----------------------------------------------------------------------

    /** Current run score (distance × multiplier + bonus). */
    var score: Int = 0
        private set

    /** Current score multiplier (1× base, boosted by Kindness Bonus etc.). */
    var scoreMultiplier: Float = 1f

    /** High score loaded from SharedPreferences. */
    var highScore: Int = prefs.getInt("high_score", 0)
        private set

    /** Whether the current run has beaten the high score yet. */
    var isNewHighScore: Boolean = false
        private set

    // -----------------------------------------------------------------------
    // Seeds & Bloom Meter
    // -----------------------------------------------------------------------

    /** Seeds collected this run (resets each run). */
    var seedsThisRun: Int = 0
        private set

    /** Lifetime seeds (persists across sessions – used for Garden meta-loop). */
    var lifetimeSeeds: Int = prefs.getInt("lifetime_seeds", 0)
        private set

    /**
     * Bloom meter fill 0–[GameConstants.BLOOM_SEED_COUNT].
     * When it reaches the max value, Bloom activates and this resets to 0.
     */
    var bloomMeter: Int = 0
        private set

    /** True during the 5-second Bloom invincibility window. */
    var isBloomActive: Boolean = false
        private set

    private var bloomTimer: Float = 0f

    // -----------------------------------------------------------------------
    // Update (called every frame by GameView)
    // -----------------------------------------------------------------------

    fun update(deltaTime: Float) {
        // ── Scroll speed ramp ────────────────────────────────────────────
        distanceMetres += scrollSpeed / 1000f * deltaTime
        scrollSpeed = MathUtils.clamp(
            GameConstants.BASE_SCROLL_SPEED + distanceMetres * GameConstants.SPEED_PER_METRE,
            GameConstants.BASE_SCROLL_SPEED,
            GameConstants.MAX_SCROLL_SPEED
        )

        // ── Score ────────────────────────────────────────────────────────
        score += (GameConstants.POINTS_PER_METRE * scoreMultiplier * scrollSpeed / 1000f * deltaTime * 1000f).toInt()
        if (score > highScore) {
            highScore = score
            isNewHighScore = true
        }

        // ── Bloom timer ──────────────────────────────────────────────────
        if (isBloomActive) {
            bloomTimer += deltaTime
            if (bloomTimer >= GameConstants.BLOOM_DURATION_S) {
                isBloomActive = false
                bloomTimer    = 0f
            }
        }
    }

    // -----------------------------------------------------------------------
    // Seed collection
    // -----------------------------------------------------------------------

    /** Call when the player collects a Seed orb. */
    fun collectSeed() {
        seedsThisRun++
        lifetimeSeeds++
        bloomMeter++
        if (bloomMeter >= GameConstants.BLOOM_SEED_COUNT) {
            bloomMeter   = 0
            isBloomActive = true
            bloomTimer   = 0f
        }
    }

    // -----------------------------------------------------------------------
    // Run lifecycle
    // -----------------------------------------------------------------------

    /** Reset all per-run state (called at the start of a new run). */
    fun resetRun() {
        distanceMetres = 0f
        scrollSpeed    = GameConstants.BASE_SCROLL_SPEED
        score          = 0
        scoreMultiplier = 1f
        seedsThisRun   = 0
        bloomMeter     = 0
        isBloomActive  = false
        bloomTimer     = 0f
        isNewHighScore = false
    }

    /** Persist high score and lifetime seeds to SharedPreferences. */
    fun save() {
        prefs.edit().apply {
            putInt("high_score",     highScore)
            putInt("lifetime_seeds", lifetimeSeeds)
            apply()
        }
    }
}
