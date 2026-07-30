package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ForestMoodSystemTest {

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
    fun `classifies spare-heavy runs as gentle`() {
        val summary = summary(
            forestMood = ForestMood.STEADY,
            mercyHearts = 5,
            kindnessChain = 4,
            cleanPasses = 6,
            sparedCount = 2,
            hitsTaken = 0
        )

        assertEquals(ForestMood.GENTLE, ForestMoodSystem.classifyRun(summary))
    }

    @Test
    fun `classifies rough short runs as fearful`() {
        val summary = summary(
            forestMood = ForestMood.STEADY,
            score = 320,
            distanceM = 280f,
            mercyHearts = 0,
            kindnessChain = 0,
            cleanPasses = 1,
            hitsTaken = 2,
            seedsCollected = 2,
            lastKiller = EntityType.WOLF
        )

        assertEquals(ForestMood.FEARFUL, ForestMoodSystem.classifyRun(summary))
    }

    @Test
    fun `records streaking mood state across runs`() {
        val first = summary(
            forestMood = ForestMood.GENTLE,
            score = 960,
            distanceM = 710f,
            mercyHearts = 4,
            kindnessChain = 5,
            cleanPasses = 5,
            sparedCount = 1,
            hitsTaken = 0,
            seedsCollected = 7
        )
        val second = first.copy(score = 1_120, highScore = 1_120)

        val initial = ForestMoodSystem.recordRun(context, first)
        val repeated = ForestMoodSystem.recordRun(context, second)

        assertEquals(ForestMood.GENTLE, initial.currentMood)
        assertEquals(1, initial.moodStreak)
        assertEquals(2, repeated.moodStreak)
        assertEquals(2, repeated.gentleRuns)
        assertEquals(ForestMood.GENTLE, SaveManager.loadForestMoodState(context).dominantMood)
    }

    @Test
    fun `saturated mood counters never wrap negative`() {
        SaveManager.saveForestMoodState(
            context,
            ForestMoodState(
                currentMood = ForestMood.GENTLE,
                moodStreak = Int.MAX_VALUE,
                totalRuns = Int.MAX_VALUE,
                gentleRuns = Int.MAX_VALUE,
                recklessRuns = Int.MAX_VALUE,
                fearfulRuns = Int.MAX_VALUE,
                steadyRuns = Int.MAX_VALUE
            )
        )

        val updated = ForestMoodSystem.recordRun(
            context,
            summary(forestMood = ForestMood.GENTLE)
        )

        assertEquals(Int.MAX_VALUE, updated.moodStreak)
        assertEquals(Int.MAX_VALUE, updated.totalRuns)
        assertEquals(Int.MAX_VALUE, updated.gentleRuns)
        assertEquals(Int.MAX_VALUE, SaveManager.loadForestMoodState(context).gentleRuns)
    }

    @Test
    fun `changing mood resets only the streak while total history saturates`() {
        SaveManager.saveForestMoodState(
            context,
            ForestMoodState(
                currentMood = ForestMood.FEARFUL,
                moodStreak = Int.MAX_VALUE,
                totalRuns = Int.MAX_VALUE,
                fearfulRuns = Int.MAX_VALUE
            )
        )

        val updated = ForestMoodSystem.recordRun(
            context,
            summary(forestMood = ForestMood.STEADY)
        )

        assertEquals(ForestMood.STEADY, updated.currentMood)
        assertEquals(1, updated.moodStreak)
        assertEquals(Int.MAX_VALUE, updated.totalRuns)
        assertEquals(1, updated.steadyRuns)
        assertEquals(Int.MAX_VALUE, updated.fearfulRuns)
    }

    private fun summary(
        forestMood: ForestMood,
        score: Int = 840,
        distanceM: Float = 540f,
        mercyHearts: Int = 2,
        kindnessChain: Int = 2,
        cleanPasses: Int = 3,
        sparedCount: Int = 0,
        hitsTaken: Int = 0,
        seedsCollected: Int = 6,
        lastKiller: EntityType? = null
    ): RunSummary = RunSummary(
        score = score,
        distanceM = distanceM,
        isNewHighScore = false,
        highScore = score.coerceAtLeast(0),
        mercyHearts = mercyHearts,
        mercyMisses = mercyHearts,
        kindnessChain = kindnessChain,
        cleanPasses = cleanPasses,
        sparedCount = sparedCount,
        hitsTaken = hitsTaken,
        seedsCollected = seedsCollected,
        bloomConversions = 0,
        lastKiller = lastKiller,
        restQuote = "Quiet feet.",
        forestMood = forestMood
    )
}
