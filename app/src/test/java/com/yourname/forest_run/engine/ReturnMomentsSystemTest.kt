package com.yourname.forest_run.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReturnMomentsSystemTest {

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
    fun `first garden open of a day gets greeting`() {
        val nowMs = 2 * 24L * 60L * 60L * 1_000L

        val moment = ReturnMomentsSystem.resolveGardenMoment(context, null, nowMs)

        assertEquals("Good To See You", moment?.title)
        assertNull(ReturnMomentsSystem.resolveGardenMoment(context, null, nowMs + 1_000L))
    }

    @Test
    fun `long absence return overrides normal daily greeting`() {
        SaveManager.saveReturnMomentState(
            context,
            ReturnMomentState(lastActiveAtMs = 1L, lastGardenGreetingDay = -1L, roughRunStreak = 0)
        )

        val nowMs = 3 * 24L * 60L * 60L * 1_000L
        val moment = ReturnMomentsSystem.resolveGardenMoment(context, null, nowMs)

        assertNotNull(moment)
        assertEquals("Welcome Back", moment?.title)
    }

    @Test
    fun `rough run streak creates comfort return moment`() {
        val summary = RunSummary(
            score = 220,
            distanceM = 210f,
            isNewHighScore = false,
            highScore = 800,
            mercyHearts = 0,
            mercyMisses = 0,
            kindnessChain = 0,
            cleanPasses = 1,
            sparedCount = 0,
            hitsTaken = 2,
            seedsCollected = 1,
            bloomConversions = 0,
            lastKiller = null,
            restQuote = "Not yet.",
            forestMood = ForestMood.FEARFUL
        )

        repeat(3) { index ->
            ReturnMomentsSystem.recordRunOutcome(context, summary, nowMs = 1_000L + index)
        }

        val moment = ReturnMomentsSystem.resolveGardenMoment(context, summary, nowMs = 4_000L)
        assertEquals("Take A Breath", moment?.title)
    }
}
