package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class GameStateManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `collecting seeds activates bloom and persists lifetime seeds`() {
        val state = GameStateManager(context)

        repeat(GameConstants.BLOOM_SEED_COUNT) {
            state.collectSeed()
        }
        state.addBonus(points = 250)
        state.save()

        assertTrue(state.isBloomActive)
        assertEquals(0, state.bloomMeter)
        assertEquals(GameConstants.BLOOM_SEED_COUNT, state.seedsThisRun)
        assertEquals(GameConstants.BLOOM_SEED_COUNT, state.lifetimeSeeds)

        val reloaded = GameStateManager(context)
        assertEquals(GameConstants.BLOOM_SEED_COUNT, reloaded.lifetimeSeeds)
        assertEquals(250, reloaded.highScore)
    }

    @Test
    fun `bulk seed bonus crosses the meter once and ignores surplus during active bloom`() {
        val state = GameStateManager(context)
        state.debugPrimeBloomMeter(GameConstants.BLOOM_SEED_COUNT - 2)

        state.addBonus(seeds = 3)

        assertTrue(state.isBloomActive)
        assertEquals(0, state.bloomMeter)
        assertEquals(3, state.seedsThisRun)
        assertEquals(3, state.lifetimeSeeds)
        assertEquals(3, SaveManager.loadLifetimeSeeds(context))
    }

    @Test
    fun `extreme bulk seed bonus saturates without iterative work or wraparound`() {
        SaveManager.saveLifetimeSeeds(context, Int.MAX_VALUE - 5)
        val state = GameStateManager(context)

        state.addBonus(seeds = Int.MAX_VALUE)

        assertEquals(Int.MAX_VALUE, state.seedsThisRun)
        assertEquals(Int.MAX_VALUE, state.lifetimeSeeds)
        assertEquals(Int.MAX_VALUE, SaveManager.loadLifetimeSeeds(context))
        assertTrue(state.isBloomActive)
        assertEquals(0, state.bloomMeter)
    }

    @Test
    fun `bulk seeds earned during bloom do not refill or restart its clock`() {
        val state = GameStateManager(context)
        state.debugActivateBloom()
        state.update(GameConstants.BLOOM_DURATION_S / 2f)
        val secondsRemaining = state.bloomSecondsRemaining

        state.addBonus(seeds = Int.MAX_VALUE)

        assertTrue(state.isBloomActive)
        assertEquals(0, state.bloomMeter)
        assertEquals(secondsRemaining, state.bloomSecondsRemaining, 0.0001f)
        assertEquals(Int.MAX_VALUE, state.seedsThisRun)
    }

    @Test
    fun `invalid frame deltas cannot corrupt or rewind run state`() {
        val state = GameStateManager(context)
        state.update(1f)
        val time = state.runTimeSeconds
        val distance = state.distanceMetres
        val score = state.score
        val speed = state.scrollSpeed

        state.update(-1f)
        state.update(Float.NaN)
        state.update(Float.POSITIVE_INFINITY)

        assertEquals(time, state.runTimeSeconds, 0f)
        assertEquals(distance, state.distanceMetres, 0f)
        assertEquals(score, state.score)
        assertEquals(speed, state.scrollSpeed, 0f)
    }

    @Test
    fun `score additions saturate instead of wrapping negative`() {
        val state = GameStateManager(context)

        state.addBonus(points = Int.MAX_VALUE)
        state.addBonus(points = Int.MAX_VALUE)

        assertEquals(Int.MAX_VALUE, state.score)
        assertEquals(Int.MAX_VALUE, state.highScore)
        assertTrue(state.isNewHighScore)
    }

    @Test
    fun `lifetime seed currency saturates instead of wrapping negative`() {
        SaveManager.saveLifetimeSeeds(context, Int.MAX_VALUE)
        val state = GameStateManager(context)

        state.collectSeed()

        assertEquals(1, state.seedsThisRun)
        assertEquals(Int.MAX_VALUE, state.lifetimeSeeds)
        assertEquals(Int.MAX_VALUE, SaveManager.loadLifetimeSeeds(context))
    }

    @Test
    fun `non finite score multiplier normalizes before rewards`() {
        val state = GameStateManager(context)
        state.scoreMultiplier = Float.POSITIVE_INFINITY

        state.addBonus(points = 100)

        assertEquals(100, state.score)
        assertEquals(1f, state.scoreMultiplier, 0f)
    }

    @Test
    fun `invalid speed debuffs are ignored and boosts are capped`() {
        val state = GameStateManager(context)

        state.applySpeedDebuff(Float.NaN, 1_000)
        state.applySpeedDebuff(-1f, 1_000)
        state.applySpeedDebuff(0.5f, 0)
        assertEquals(1f, state.speedDebuffMultiplier, 0f)

        state.applySpeedDebuff(3f, 1_000)
        assertEquals(1f, state.speedDebuffMultiplier, 0f)

        state.applySpeedDebuff(0.5f, 1_000)
        assertEquals(0.5f, state.speedDebuffMultiplier, 0f)
        state.update(1f)
        assertEquals(1f, state.speedDebuffMultiplier, 0f)
    }

    @Test
    fun `reset run clears transient state and milestone carryover`() {
        val state = GameStateManager(context)

        state.update(1_100f)
        assertTrue(state.consumeMilestone())

        state.resetRun()

        assertEquals(0, state.score)
        assertEquals(0, state.seedsThisRun)
        assertEquals(0, state.bloomMeter)
        assertFalse(state.isBloomActive)
        assertFalse(state.consumeMilestone())
    }

    @Test
    fun `reset run clears opening guidance state`() {
        val state = GameStateManager(context)

        state.recordJumpInput()
        state.recordJumpHold(OpeningReadabilityGuide.HOLD_DISCOVERY_THRESHOLD_SEC)
        state.recordDuckInput()
        state.update(12f)
        assertTrue(state.openingGuidanceCue?.chips?.all { it.isComplete } == true)

        state.resetRun()

        assertEquals(0f, state.runTimeSeconds, 0.0001f)
        assertEquals("Find The Stride", state.openingGuidanceCue?.title)
        assertTrue(state.openingGuidanceCue?.chips?.none { it.isComplete } == true)
    }

    @Test
    fun `pacifist rewards flow through game state in FIFO order`() {
        val state = GameStateManager(context)

        repeat(5) { state.recordCleanPass() }
        assertEquals("Mercy noticed", state.consumePacifistReward()?.message)
        assertEquals("Kindness carries", state.consumePacifistReward()?.message)

        state.updatePacifistBiome(Biome.MEADOW)
        repeat(3) { state.recordCleanPass() }
        state.updatePacifistBiome(Biome.ORCHARD)
        assertEquals("Meadow at peace", state.consumePacifistReward()?.message)

        state.recordSpare()
        state.recordSpare()
        assertEquals("Mercy kept", state.consumePacifistReward()?.message)
        assertEquals("Merciful route", state.consumePacifistReward()?.message)
    }

    @Test
    fun `mercy system data flows through game state`() {
        val state = GameStateManager(context)

        repeat(3) { state.addMercyHeart() }
        state.recordCleanPass()
        state.recordSpare()
        state.recordHit()

        assertEquals(3, state.mercyHearts)
        assertEquals(3, state.mercyMissesThisRun)
        assertEquals(0, state.kindnessChain)
    }

    @Test
    fun `bloom conversion grants reward during active bloom`() {
        val state = GameStateManager(context)
        repeat(GameConstants.BLOOM_SEED_COUNT) { state.collectSeed() }

        state.recordBloomConversion()

        assertTrue(state.isBloomActive)
        assertEquals(1, state.bloomConversionsThisRun)
        assertEquals(GameConstants.BLOOM_SEED_COUNT + 1, state.seedsThisRun)
        assertTrue(state.score >= 140)
    }

    @Test
    fun `debug bloom helpers allow deterministic showcase setup`() {
        val state = GameStateManager(context)

        state.debugPrimeBloomMeter(GameConstants.BLOOM_SEED_COUNT - 1)
        assertEquals(GameConstants.BLOOM_SEED_COUNT - 1, state.bloomMeter)
        assertFalse(state.isBloomActive)

        state.debugActivateBloom()
        assertTrue(state.isBloomActive)
        assertEquals(0, state.bloomMeter)
    }

    @Test
    fun `bloom fractions reflect both meter build and active window`() {
        val state = GameStateManager(context)

        repeat(GameConstants.BLOOM_SEED_COUNT / 2) { state.collectSeed() }
        assertEquals(0.5f, state.bloomMeterFraction, 0.001f)
        assertEquals(0f, state.bloomTimeFractionRemaining, 0.001f)

        repeat(GameConstants.BLOOM_SEED_COUNT / 2) { state.collectSeed() }
        assertTrue(state.isBloomActive)
        assertEquals(1f, state.bloomTimeFractionRemaining, 0.001f)

        state.update(GameConstants.BLOOM_DURATION_S / 2f)
        assertTrue(state.bloomTimeFractionRemaining in 0.45f..0.55f)
    }

    @Test
    fun `run summary captures current run state`() {
        val state = GameStateManager(context)
        repeat(3) { state.collectSeed() }
        repeat(2) { state.addMercyHeart() }
        repeat(4) { state.recordCleanPass() }
        state.recordSpare()
        state.recordBloomConversion()

        val summary = state.buildRunSummary(
            restQuote = "The forest remembers.",
            lastKiller = EntityType.WOLF
        )

        assertEquals(state.score, summary.score)
        assertEquals(4, summary.cleanPasses)
        assertEquals(1, summary.sparedCount)
        assertEquals(2, summary.mercyHearts)
        assertEquals(1, summary.bloomConversions)
        assertEquals(EntityType.WOLF, summary.lastKiller)
        assertEquals(ForestMood.GENTLE, summary.forestMood)
        assertEquals(PacifistRouteTier.KIND, summary.pacifistRouteTier)
    }
}
