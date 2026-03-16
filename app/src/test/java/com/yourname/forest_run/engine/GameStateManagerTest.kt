package com.yourname.forest_run.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.entities.EntityType
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
    fun `pacifist rewards flow through game state`() {
        val state = GameStateManager(context)

        repeat(5) { state.recordCleanPass() }
        val streakReward = state.consumePacifistReward()
        assertEquals("Kindness carries", streakReward?.message)

        state.updatePacifistBiome(Biome.MEADOW)
        repeat(3) { state.recordCleanPass() }
        state.updatePacifistBiome(Biome.ORCHARD)
        val biomeReward = state.consumePacifistReward()
        assertEquals("Meadow at peace", biomeReward?.message)

        state.recordSpare()
        state.recordSpare()
        val spareReward = state.consumePacifistReward()
        assertEquals("Mercy kept", spareReward?.message)
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
