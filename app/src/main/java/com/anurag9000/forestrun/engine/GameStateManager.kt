package com.anurag9000.forestrun.engine

import android.content.Context
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.utils.MathUtils

/**
 * Single source of truth for mutable per-run state.
 *
 * Lifetime seed currency is persisted transactionally through SaveManager. The
 * in-memory value is only a cache and is refreshed before every mutation so a
 * Garden purchase can never be overwritten by a stale GameStateManager.
 */
class GameStateManager(context: Context) {
    private val appContext = context.applicationContext
    private val pacifistTracker = PacifistTracker()
    private val mercySystem = MercySystem()
    private var openingInputState = OpeningInputState()

    var scrollSpeed: Float = GameConstants.BASE_SCROLL_SPEED
        private set

    var distanceMetres: Float = 0f
        private set

    var runTimeSeconds: Float = 0f
        private set

    var score: Int = 0
        private set

    private var exactScore: Float = 0f

    var scoreMultiplier: Float = 1f

    var highScore: Int = SaveManager.loadHighScore(appContext)
        private set

    var isNewHighScore: Boolean = false
        private set

    private var lastMilestone: Int = 0
    private var milestoneReady: Boolean = false

    fun consumeMilestone(): Boolean {
        if (milestoneReady) {
            milestoneReady = false
            return true
        }
        return false
    }

    var seedsThisRun: Int = 0
        private set

    var lifetimeSeeds: Int = SaveManager.loadLifetimeSeeds(appContext)
        private set

    var bloomMeter: Int = 0
        private set

    var isBloomActive: Boolean = false
        private set

    private var bloomTimer: Float = 0f

    val bloomSecondsRemaining: Float
        get() = (GameConstants.BLOOM_DURATION_S - bloomTimer).coerceAtLeast(0f)

    val bloomTimeFractionRemaining: Float
        get() = if (!isBloomActive) 0f else {
            (bloomSecondsRemaining / GameConstants.BLOOM_DURATION_S).coerceIn(0f, 1f)
        }

    val bloomSeedTarget: Int
        get() = GameConstants.BLOOM_SEED_COUNT

    val bloomMeterFraction: Float
        get() = (bloomMeter / GameConstants.BLOOM_SEED_COUNT.toFloat()).coerceIn(0f, 1f)

    var bloomConversionsThisRun: Int = 0
        private set

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

    val pacifistRouteTier: PacifistRouteTier
        get() = pacifistTracker.currentRouteTier(mercyHearts, kindnessChain)

    val openingGuidanceCue: OpeningGuidanceCue?
        get() = OpeningReadabilityGuide.cueFor(
            runTimeSeconds = runTimeSeconds,
            inputState = openingInputState,
            routeTier = pacifistRouteTier,
            mercyHearts = mercyHearts,
            kindnessChain = kindnessChain
        )

    var speedDebuffMultiplier: Float = 1f
        private set

    private var speedDebuffTimer: Float = 0f

    fun update(deltaTime: Float) {
        if (!deltaTime.isFinite() || deltaTime <= 0f) return

        runTimeSeconds = finiteSaturatingAdd(runTimeSeconds, deltaTime)

        // Capture one speed for both distance and score so acceleration cannot
        // introduce a one-frame drift between the two quantities.
        val speedThisFrame = scrollSpeed.takeIf { it.isFinite() && it >= 0f }
            ?: GameConstants.BASE_SCROLL_SPEED
        scrollSpeed = speedThisFrame
        val distanceDelta = safeNonNegativeProduct(speedThisFrame / 1000f, deltaTime)
        distanceMetres = finiteSaturatingAdd(distanceMetres, distanceDelta)

        val baseSpeed = MathUtils.clamp(
            GameConstants.BASE_SCROLL_SPEED + distanceMetres * GameConstants.SPEED_PER_METRE,
            GameConstants.BASE_SCROLL_SPEED,
            GameConstants.MAX_SCROLL_SPEED
        )
        val safeDebuff = speedDebuffMultiplier.takeIf { it.isFinite() && it > 0f }
            ?.coerceAtMost(1f)
            ?: 1f
        speedDebuffMultiplier = safeDebuff
        scrollSpeed = safeNonNegativeProduct(baseSpeed, safeDebuff)
            .coerceAtMost(GameConstants.MAX_SCROLL_SPEED)

        if (speedDebuffTimer > 0f) {
            speedDebuffTimer = (speedDebuffTimer - deltaTime).coerceAtLeast(0f)
            if (speedDebuffTimer <= 0f) {
                speedDebuffMultiplier = 1f
            }
        }

        val safeScoreMultiplier = normalizedScoreMultiplier()
        val scoreDelta = safeNonNegativeProduct(
            GameConstants.POINTS_PER_METRE * safeScoreMultiplier,
            distanceDelta
        )
        exactScore = finiteSaturatingAdd(exactScore, scoreDelta)
        val deltaInt = exactScore.coerceAtMost(Int.MAX_VALUE.toFloat()).toInt()
        if (deltaInt > 0) {
            score = saturatingAdd(score, deltaInt)
            exactScore = (exactScore - deltaInt).coerceAtLeast(0f)
            updateHighScore()
        }

        val milestone = score / 1000
        if (milestone > lastMilestone) {
            lastMilestone = milestone
            milestoneReady = true
        }

        if (isBloomActive) {
            bloomTimer = finiteSaturatingAdd(bloomTimer, deltaTime)
            if (bloomTimer >= GameConstants.BLOOM_DURATION_S) {
                isBloomActive = false
                bloomTimer = 0f
            }
        }
    }

    /**
     * Adds one seed to the run and the persistent Garden balance.
     *
     * Seeds earned during Bloom still count as run/currency rewards, but they
     * do not refill or restart the active Bloom clock. The next meter begins
     * only after the current power window has ended.
     */
    fun collectSeed() {
        addSeeds(1)
    }

    fun addBonus(points: Int = 0, seeds: Int = 0, multiplierBoost: Float = 0f) {
        val safePoints = points.coerceAtLeast(0)
        if (safePoints > 0) {
            val weightedPoints = safePoints.toDouble() * normalizedScoreMultiplier().toDouble()
            val scoreDelta = weightedPoints
                .coerceAtMost(Int.MAX_VALUE.toDouble())
                .toInt()
            score = saturatingAdd(score, scoreDelta)
            updateHighScore()
        }

        addSeeds(seeds)

        if (multiplierBoost.isFinite() && multiplierBoost > 0f) {
            scoreMultiplier = multiplierBoost
        }
    }

    fun recordBloomConversion() {
        bloomConversionsThisRun = saturatingIncrement(bloomConversionsThisRun)
        addBonus(points = 140, seeds = 1)
    }

    fun debugPrimeBloomMeter(seedCount: Int) {
        bloomMeter = seedCount.coerceIn(0, GameConstants.BLOOM_SEED_COUNT - 1)
        isBloomActive = false
        bloomTimer = 0f
    }

    fun debugActivateBloom() {
        bloomMeter = 0
        isBloomActive = true
        bloomTimer = 0f
    }

    fun buildRunSummary(lastKiller: EntityType?, restQuote: String = ""): RunSummary =
        RunSummary(
            score = score,
            distanceM = distanceMetres,
            isNewHighScore = isNewHighScore,
            highScore = highScore,
            mercyHearts = mercyHearts,
            mercyMisses = mercyMissesThisRun,
            kindnessChain = kindnessChain,
            cleanPasses = cleanPassesThisRun,
            sparedCount = sparedThisRun,
            hitsTaken = hitsThisRun,
            seedsCollected = seedsThisRun,
            bloomConversions = bloomConversionsThisRun,
            lastKiller = lastKiller,
            restQuote = restQuote,
            forestMood = ForestMoodSystem.classifyRun(
                score = score,
                distanceM = distanceMetres,
                mercyHearts = mercyHearts,
                kindnessChain = kindnessChain,
                cleanPasses = cleanPassesThisRun,
                sparedCount = sparedThisRun,
                hitsTaken = hitsThisRun,
                seedsCollected = seedsThisRun,
                bloomConversions = bloomConversionsThisRun
            ),
            pacifistRouteTier = pacifistRouteTier
        )

    fun applySpeedDebuff(multiplier: Float, durationMs: Int) {
        if (!multiplier.isFinite() || multiplier <= 0f || durationMs <= 0) return
        speedDebuffMultiplier = multiplier.coerceAtMost(1f)
        speedDebuffTimer = durationMs / 1000f
    }

    fun addMercyHeart() {
        mercySystem.recordMercyMiss()
        pacifistTracker.updateRouteReward(mercyHearts, kindnessChain)
    }

    fun updatePacifistBiome(biome: Biome) {
        pacifistTracker.updateBiome(biome)
    }

    fun recordCleanPass() {
        mercySystem.recordCleanPass()
        pacifistTracker.recordCleanPass()
        pacifistTracker.updateRouteReward(mercyHearts, kindnessChain)
    }

    fun recordSpare() {
        mercySystem.recordSpare()
        pacifistTracker.recordSpare()
        pacifistTracker.updateRouteReward(mercyHearts, kindnessChain)
    }

    fun recordHit() {
        mercySystem.recordHit()
        pacifistTracker.recordHit()
        pacifistTracker.updateRouteReward(mercyHearts, kindnessChain)
    }

    fun consumePacifistReward(): PacifistReward? = pacifistTracker.consumeReward()

    fun shouldLockRandomOpeningSpawns(): Boolean =
        OpeningReadabilityGuide.isRandomSpawnLocked(runTimeSeconds)

    fun openingSpawnInterval(defaultInterval: Float): Float =
        OpeningReadabilityGuide.adjustedSpawnInterval(runTimeSeconds, defaultInterval)

    fun openingSpawnPool(defaultPool: List<EntityType>): List<EntityType> =
        OpeningReadabilityGuide.spawnPoolFor(runTimeSeconds, defaultPool)

    fun recordJumpInput() {
        openingInputState = openingInputState.copy(jumpSeen = true)
    }

    fun recordJumpHold(holdSeconds: Float) {
        if (holdSeconds >= OpeningReadabilityGuide.HOLD_DISCOVERY_THRESHOLD_SEC) {
            openingInputState = openingInputState.copy(holdSeen = true)
        }
    }

    fun recordDuckInput() {
        openingInputState = openingInputState.copy(duckSeen = true)
    }

    fun resetRun() {
        runTimeSeconds = 0f
        distanceMetres = 0f
        scrollSpeed = GameConstants.BASE_SCROLL_SPEED
        score = 0
        exactScore = 0f
        scoreMultiplier = 1f
        seedsThisRun = 0
        lifetimeSeeds = SaveManager.loadLifetimeSeeds(appContext)
        bloomMeter = 0
        isBloomActive = false
        bloomTimer = 0f
        bloomConversionsThisRun = 0
        isNewHighScore = false
        mercySystem.reset()
        speedDebuffMultiplier = 1f
        speedDebuffTimer = 0f
        lastMilestone = 0
        milestoneReady = false
        pacifistTracker.reset()
        openingInputState = OpeningInputState()
    }

    /** Persist score without ever overwriting externally spent Garden seeds. */
    fun save() {
        SaveManager.saveHighScore(appContext, highScore)
        lifetimeSeeds = SaveManager.loadLifetimeSeeds(appContext)
    }

    private fun addSeeds(seedCount: Int) {
        val safeSeedCount = seedCount.coerceAtLeast(0)
        if (safeSeedCount == 0) return

        seedsThisRun = saturatingAdd(seedsThisRun, safeSeedCount)

        // Reload once before the atomic-sized award: Garden may have spent
        // seeds while this long-lived manager was inactive. Persisting once
        // avoids O(n) disk writes for large bonuses.
        lifetimeSeeds = saturatingAdd(
            SaveManager.loadLifetimeSeeds(appContext),
            safeSeedCount
        )
        SaveManager.saveLifetimeSeeds(appContext, lifetimeSeeds)

        if (isBloomActive) return

        val safeMeter = bloomMeter.coerceIn(0, GameConstants.BLOOM_SEED_COUNT - 1)
        val seedsUntilBloom = GameConstants.BLOOM_SEED_COUNT - safeMeter
        if (safeSeedCount >= seedsUntilBloom) {
            bloomMeter = 0
            isBloomActive = true
            bloomTimer = 0f
        } else {
            bloomMeter = safeMeter + safeSeedCount
        }
    }

    private fun updateHighScore() {
        if (score > highScore) {
            highScore = score
            isNewHighScore = true
        }
    }

    private fun normalizedScoreMultiplier(): Float {
        val safeMultiplier = scoreMultiplier.takeIf { it.isFinite() && it > 0f } ?: 1f
        scoreMultiplier = safeMultiplier
        return safeMultiplier
    }

    private fun saturatingIncrement(value: Int): Int =
        if (value >= Int.MAX_VALUE) Int.MAX_VALUE else value + 1

    private fun saturatingAdd(value: Int, delta: Int): Int {
        if (delta <= 0) return value.coerceAtLeast(0)
        return if (value >= Int.MAX_VALUE - delta) Int.MAX_VALUE else value + delta
    }

    private fun finiteSaturatingAdd(value: Float, delta: Float): Float {
        if (!value.isFinite() || value < 0f) return 0f
        if (!delta.isFinite() || delta <= 0f) return value
        val sum = value.toDouble() + delta.toDouble()
        return sum.coerceAtMost(Float.MAX_VALUE.toDouble()).toFloat()
    }

    private fun safeNonNegativeProduct(first: Float, second: Float): Float {
        if (!first.isFinite() || !second.isFinite() || first <= 0f || second <= 0f) return 0f
        val product = first.toDouble() * second.toDouble()
        return product.coerceAtMost(Float.MAX_VALUE.toDouble()).toFloat()
    }
}
