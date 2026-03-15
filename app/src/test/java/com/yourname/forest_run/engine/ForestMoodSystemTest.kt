package com.yourname.forest_run.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.entities.EntityType
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
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `classifies spare-heavy runs as gentle`() {
        val summary = RunSummary(
            score = 840,
            distanceM = 540f,
            isNewHighScore = false,
            highScore = 1_200,
            mercyHearts = 5,
            mercyMisses = 5,
            kindnessChain = 4,
            cleanPasses = 6,
            sparedCount = 2,
            hitsTaken = 0,
            seedsCollected = 6,
            bloomConversions = 0,
            lastKiller = null,
            restQuote = "Quiet feet.",
            forestMood = ForestMood.STEADY
        )

        assertEquals(ForestMood.GENTLE, ForestMoodSystem.classifyRun(summary))
    }

    @Test
    fun `classifies rough short runs as fearful`() {
        val summary = RunSummary(
            score = 320,
            distanceM = 280f,
            isNewHighScore = false,
            highScore = 900,
            mercyHearts = 0,
            mercyMisses = 0,
            kindnessChain = 0,
            cleanPasses = 1,
            sparedCount = 0,
            hitsTaken = 2,
            seedsCollected = 2,
            bloomConversions = 0,
            lastKiller = EntityType.WOLF,
            restQuote = "Not today.",
            forestMood = ForestMood.STEADY
        )

        assertEquals(ForestMood.FEARFUL, ForestMoodSystem.classifyRun(summary))
    }

    @Test
    fun `records streaking mood state across runs`() {
        val first = RunSummary(
            score = 960,
            distanceM = 710f,
            isNewHighScore = false,
            highScore = 960,
            mercyHearts = 4,
            mercyMisses = 4,
            kindnessChain = 5,
            cleanPasses = 5,
            sparedCount = 1,
            hitsTaken = 0,
            seedsCollected = 7,
            bloomConversions = 0,
            lastKiller = null,
            restQuote = "Softly.",
            forestMood = ForestMood.GENTLE
        )
        val second = first.copy(score = 1_120, highScore = 1_120, forestMood = ForestMood.GENTLE)

        val initial = ForestMoodSystem.recordRun(context, first)
        val repeated = ForestMoodSystem.recordRun(context, second)

        assertEquals(ForestMood.GENTLE, initial.currentMood)
        assertEquals(1, initial.moodStreak)
        assertEquals(2, repeated.moodStreak)
        assertEquals(2, repeated.gentleRuns)
        assertEquals(ForestMood.GENTLE, SaveManager.loadForestMoodState(context).dominantMood)
    }
}
