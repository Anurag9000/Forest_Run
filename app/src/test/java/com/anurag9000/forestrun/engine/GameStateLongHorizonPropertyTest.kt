package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameStateLongHorizonPropertyTest {
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
    fun `seeded mixed operations preserve long-horizon run invariants`() {
        val random = Random(0xF0E57)
        val state = GameStateManager(context)
        var previousHighScore = state.highScore

        repeat(8_192) { step ->
            when (random.nextInt(10)) {
                0, 1, 2 -> {
                    val beforeTime = state.runTimeSeconds
                    val beforeDistance = state.distanceMetres
                    val beforeScore = state.score
                    val delta = random.nextDouble(0.001, 0.12).toFloat()

                    state.update(delta)

                    assertTrue("time regressed at step $step", state.runTimeSeconds >= beforeTime)
                    assertTrue("distance regressed at step $step", state.distanceMetres >= beforeDistance)
                    assertTrue("score regressed at step $step", state.score >= beforeScore)
                }

                3 -> state.collectSeed()

                4 -> {
                    val wasActive = state.isBloomActive
                    val remaining = state.bloomSecondsRemaining
                    state.recordBloomConversion()
                    if (wasActive) {
                        assertTrue(state.isBloomActive)
                        assertEquals(0, state.bloomMeter)
                        assertEquals(remaining, state.bloomSecondsRemaining, 0.0001f)
                    }
                }

                5 -> when (random.nextInt(4)) {
                    0 -> state.addMercyHeart()
                    1 -> state.recordCleanPass()
                    2 -> state.recordSpare()
                    else -> state.recordHit()
                }

                6 -> {
                    val multiplier = random.nextDouble(0.2, 1.0).toFloat()
                    val durationMs = random.nextInt(1, 3_001)
                    state.applySpeedDebuff(multiplier, durationMs)
                }

                7 -> {
                    val beforeTime = state.runTimeSeconds
                    val beforeDistance = state.distanceMetres
                    val beforeScore = state.score
                    val malformed = when (random.nextInt(5)) {
                        0 -> 0f
                        1 -> -random.nextDouble(0.001, 10.0).toFloat()
                        2 -> Float.NaN
                        3 -> Float.POSITIVE_INFINITY
                        else -> Float.NEGATIVE_INFINITY
                    }
                    state.update(malformed)
                    assertEquals(beforeTime, state.runTimeSeconds)
                    assertEquals(beforeDistance, state.distanceMetres)
                    assertEquals(beforeScore, state.score)
                }

                8 -> state.addBonus(
                    points = random.nextInt(0, 501),
                    seeds = random.nextInt(0, 4),
                    multiplierBoost = if (random.nextBoolean()) {
                        random.nextDouble(0.5, 3.0).toFloat()
                    } else {
                        0f
                    }
                )

                else -> {
                    if (step % 29 == 0) {
                        val persistentSeeds = SaveManager.loadLifetimeSeeds(context)
                        val highScoreBeforeReset = state.highScore

                        state.resetRun()

                        assertEquals(0f, state.runTimeSeconds)
                        assertEquals(0f, state.distanceMetres)
                        assertEquals(0, state.score)
                        assertEquals(GameConstants.BASE_SCROLL_SPEED, state.scrollSpeed)
                        assertEquals(0, state.seedsThisRun)
                        assertEquals(persistentSeeds, state.lifetimeSeeds)
                        assertEquals(0, state.bloomMeter)
                        assertFalse(state.isBloomActive)
                        assertEquals(0, state.bloomConversionsThisRun)
                        assertEquals(0, state.mercyHearts)
                        assertEquals(0, state.mercyMissesThisRun)
                        assertEquals(0, state.kindnessChain)
                        assertEquals(0, state.cleanPassesThisRun)
                        assertEquals(0, state.sparedThisRun)
                        assertEquals(0, state.hitsThisRun)
                        assertEquals(1f, state.speedDebuffMultiplier)
                        assertEquals(highScoreBeforeReset, state.highScore)
                    }
                }
            }

            assertTrue("run time must remain finite", state.runTimeSeconds.isFinite())
            assertTrue("run time must remain non-negative", state.runTimeSeconds >= 0f)
            assertTrue("distance must remain finite", state.distanceMetres.isFinite())
            assertTrue("distance must remain non-negative", state.distanceMetres >= 0f)
            assertTrue("scroll speed must remain finite", state.scrollSpeed.isFinite())
            assertTrue(state.scrollSpeed >= 0f)
            assertTrue(state.scrollSpeed <= GameConstants.MAX_SCROLL_SPEED)
            assertTrue(state.score >= 0)
            assertTrue(state.highScore >= previousHighScore)
            previousHighScore = state.highScore

            assertTrue(state.seedsThisRun >= 0)
            assertTrue(state.lifetimeSeeds >= 0)
            assertEquals(SaveManager.loadLifetimeSeeds(context), state.lifetimeSeeds)
            assertTrue(state.bloomMeter in 0 until GameConstants.BLOOM_SEED_COUNT)
            assertTrue(state.bloomSecondsRemaining.isFinite())
            assertTrue(state.bloomSecondsRemaining in 0f..GameConstants.BLOOM_DURATION_S)
            if (state.isBloomActive) {
                assertEquals(0, state.bloomMeter)
            }

            assertTrue(state.bloomConversionsThisRun >= 0)
            assertTrue(state.mercyHearts >= 0)
            assertTrue(state.mercyMissesThisRun >= 0)
            assertTrue(state.kindnessChain >= 0)
            assertTrue(state.cleanPassesThisRun >= 0)
            assertTrue(state.sparedThisRun >= 0)
            assertTrue(state.hitsThisRun >= 0)
            assertTrue(state.speedDebuffMultiplier.isFinite())
            assertTrue(state.speedDebuffMultiplier > 0f)
            assertTrue(state.speedDebuffMultiplier <= 1f)
        }
    }

    @Test
    fun `active Bloom ignores reward refills until its one clock expires`() {
        val state = GameStateManager(context)
        repeat(GameConstants.BLOOM_SEED_COUNT) { state.collectSeed() }
        assertTrue(state.isBloomActive)

        state.update(1.75f)
        val remaining = state.bloomSecondsRemaining
        repeat(2_048) {
            state.recordBloomConversion()
            assertTrue(state.isBloomActive)
            assertEquals(0, state.bloomMeter)
            assertEquals(remaining, state.bloomSecondsRemaining, 0.0001f)
        }

        state.update(remaining + 0.001f)
        assertFalse(state.isBloomActive)
        assertEquals(0, state.bloomMeter)

        state.collectSeed()
        assertFalse(state.isBloomActive)
        assertEquals(1, state.bloomMeter)
    }
}
