package com.yourname.forest_run.engine

import android.content.Context
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
    private val appContext = context.applicationContext
    private val pacifistTracker = PacifistTracker()
    private val mercySystem = MercySystem()

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

    /** Exact fractional score accumulated per frame to prevent integer rounding loss. */
    private var exactScore: Float = 0f

    /** Current score multiplier (1× base, boosted by Kindness Bonus etc.). */
    var scoreMultiplier: Float = 1f

    /** High score loaded from SharedPreferences. */
    var highScore: Int = SaveManager.loadHighScore(appContext)
        private set

    /** Whether the current run has beaten the high score yet. */
    var isNewHighScore: Boolean = false
        private set

    /** Score milestone last fired (every 1000 pts). Consumed by GameView for haptic. */
    private var lastMilestone: Int = 0
    private var milestoneReady: Boolean = false

    /** Returns true once per milestone crossing — GameView consumes it for haptics/shake. */
    fun consumeMilestone(): Boolean {
        if (milestoneReady) { milestoneReady = false; return true }
        return false
    }

    // -----------------------------------------------------------------------
    // Seeds & Bloom Meter
    // -----------------------------------------------------------------------

    /** Seeds collected this run (resets each run). */
    var seedsThisRun: Int = 0
        private set

    /** Lifetime seeds (persists across sessions – used for Garden meta-loop). */
    var lifetimeSeeds: Int = SaveManager.loadLifetimeSeeds(appContext)
        private set

    /**
     * Bloom meter fill 0–[GameConstants.BLOOM_SEED_COUNT].
     * When it reaches the max value, Bloom activates and this resets to 0.
     */
    var bloomMeter: Int = 0
        private set

    /** True during the canonical 6-second Bloom invincibility window. */
    var isBloomActive: Boolean = false
        private set

    private var bloomTimer: Float = 0f
    val bloomSecondsRemaining: Float
        get() = (GameConstants.BLOOM_DURATION_S - bloomTimer).coerceAtLeast(0f)
    val bloomSeedTarget: Int
        get() = GameConstants.BLOOM_SEED_COUNT

    // -----------------------------------------------------------------------
    // Mercy System (Phase 17 turns this into MercySystem.kt, for now lives here)
    // -----------------------------------------------------------------------

    /** Hearts earned this run by close calls. Resets on REST. */
    val mercyHearts: Int
        get() = mercySystem.mercyHearts
    val mercyMissesThisRun: Int
        get() = mercySystem.nearMisses
    val kindnessChain: Int
        get() = mercySystem.kindnessChain
    val cleanPassesThisRun: Int
        get() = pacifistTracker.cleanPassesThisRun
    val sparedThisRun: Int
        get() = pacifistTracker.sparedThisRun
    val hitsThisRun: Int
        get() = pacifistTracker.hitsThisRun

    // -----------------------------------------------------------------------
    // Speed Debuff (applied by Hedgehog / Hyacinth brush)
    // -----------------------------------------------------------------------

    /** Current speed multiplier (1.0 = normal, 0.5 = Hedgehog debuffed). */
    var speedDebuffMultiplier: Float = 1f
        private set

    private var speedDebuffTimer: Float = 0f

    // -----------------------------------------------------------------------
    // Update (called every frame by GameView)
    // -----------------------------------------------------------------------

    fun update(deltaTime: Float) {
        // ── Scroll speed ramp ────────────────────────────────────────────
        distanceMetres += scrollSpeed / 1000f * deltaTime

        val baseSpeed = MathUtils.clamp(
            GameConstants.BASE_SCROLL_SPEED + distanceMetres * GameConstants.SPEED_PER_METRE,
            GameConstants.BASE_SCROLL_SPEED,
            GameConstants.MAX_SCROLL_SPEED
        )
        scrollSpeed = baseSpeed * speedDebuffMultiplier

        // ── Speed debuff timer ───────────────────────────────────────────
        if (speedDebuffTimer > 0f) {
            speedDebuffTimer -= deltaTime
            if (speedDebuffTimer <= 0f) {
                speedDebuffTimer       = 0f
                speedDebuffMultiplier = 1f
            }
        }

        // ── Score ────────────────────────────────────────────────────────
        val distanceDelta = scrollSpeed / 1000f * deltaTime
        exactScore += GameConstants.POINTS_PER_METRE * scoreMultiplier * distanceDelta
        val deltaInt = exactScore.toInt()
        if (deltaInt > 0) {
            score += deltaInt
            exactScore -= deltaInt
            
            if (score > highScore) {
                highScore = score
                isNewHighScore = true
            }
        }
        // Milestone every 1000 pts — Phase 21
        val milestone = score / 1000
        if (milestone > lastMilestone) {
            lastMilestone  = milestone
            milestoneReady = true
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

    /**
     * Award bonus points and seeds (e.g. Cat kindness pass, Mercy spare).
     * @param points     Raw points added before multiplier.
     * @param seeds      Seeds to collect (full bloom-credit each).
     * @param multiplierBoost Temporary bump to scoreMultiplier (e.g. 2.0f for double; 0 = no change).
     * @param durationSec How long the multiplier boost lasts (0 = permanent for this call).
     */
    fun addBonus(points: Int = 0, seeds: Int = 0, multiplierBoost: Float = 0f) {
        val finalPoints = (points * scoreMultiplier).toInt()
        score += finalPoints
        if (score > highScore) {
            highScore = score
            isNewHighScore = true
        }
        
        repeat(seeds) { collectSeed() }
        
        if (multiplierBoost > 0f) {
            scoreMultiplier = multiplierBoost
        }
    }

    /**
     * Temporarily reduce scroll speed (Hedgehog collision, Hyacinth brush).
     * @param multiplier e.g. 0.5f = half speed.
     * @param durationMs Duration in milliseconds.
     */
    fun applySpeedDebuff(multiplier: Float, durationMs: Int) {
        speedDebuffMultiplier = multiplier
        speedDebuffTimer      = durationMs / 1000f
    }

    /** Increment mercy hearts (called by collision system on MERCY_MISS). */
    fun addMercyHeart() {
        mercySystem.recordMercyMiss()
    }

    fun updatePacifistBiome(biome: Biome) {
        pacifistTracker.updateBiome(biome)
    }

    fun recordCleanPass() {
        mercySystem.recordCleanPass()
        pacifistTracker.recordCleanPass()
    }

    fun recordSpare() {
        mercySystem.recordSpare()
        pacifistTracker.recordSpare()
    }

    fun recordHit() {
        mercySystem.recordHit()
        pacifistTracker.recordHit()
    }

    fun consumePacifistReward(): PacifistReward? = pacifistTracker.consumeReward()

    // -----------------------------------------------------------------------
    // Run lifecycle
    // -----------------------------------------------------------------------

    /** Reset all per-run state (called at the start of a new run). */
    fun resetRun() {
        distanceMetres        = 0f
        scrollSpeed           = GameConstants.BASE_SCROLL_SPEED
        score                 = 0
        exactScore            = 0f
        scoreMultiplier       = 1f
        seedsThisRun          = 0
        bloomMeter            = 0
        isBloomActive         = false
        bloomTimer            = 0f
        isNewHighScore        = false
        mercySystem.reset()
        speedDebuffMultiplier = 1f
        speedDebuffTimer      = 0f
        lastMilestone         = 0
        milestoneReady        = false
        pacifistTracker.reset()
    }

    /** Persist high score and lifetime seeds to SharedPreferences. */
    fun save() {
        SaveManager.saveHighScore(appContext, highScore)
        SaveManager.saveLifetimeSeeds(appContext, lifetimeSeeds)
    }
}
