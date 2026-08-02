package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReturnMomentsArithmeticIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `rough run streak saturates instead of overflowing`() {
        SaveManager.saveReturnMomentState(
            context,
            ReturnMomentState(
                lastActiveAtMs = 10L,
                lastGardenGreetingDay = -1L,
                roughRunStreak = SafeProgressionArithmetic.DEFAULT_COUNTER_MAX
            )
        )

        ReturnMomentsSystem.recordRunOutcome(
            context,
            roughSummary(),
            nowMs = 20L
        )

        assertEquals(
            SafeProgressionArithmetic.DEFAULT_COUNTER_MAX,
            SaveManager.loadReturnMomentState(context).roughRunStreak
        )
    }

    @Test
    fun `rough run increments and gentle run resets`() {
        SaveManager.saveReturnMomentState(
            context,
            ReturnMomentState(
                lastActiveAtMs = 10L,
                lastGardenGreetingDay = -1L,
                roughRunStreak = 4
            )
        )

        ReturnMomentsSystem.recordRunOutcome(context, roughSummary(), nowMs = 20L)
        assertEquals(5, SaveManager.loadReturnMomentState(context).roughRunStreak)

        ReturnMomentsSystem.recordRunOutcome(context, gentleSummary(), nowMs = 30L)
        assertEquals(0, SaveManager.loadReturnMomentState(context).roughRunStreak)
    }

    @Test
    fun `negative rolled back clock cannot become long absence through overflow`() {
        SaveManager.saveReturnMomentState(
            context,
            ReturnMomentState(
                lastActiveAtMs = Long.MAX_VALUE,
                lastGardenGreetingDay = -1L,
                roughRunStreak = 0
            )
        )
        val pathologicalNow = Long.MIN_VALUE + 200_000_000L

        val moment = ReturnMomentsSystem.previewGardenMoment(
            context,
            summary = null,
            nowMs = pathologicalNow
        )

        assertNotEquals("Welcome Back", moment?.title)
        assertEquals("Good To See You", moment?.title)
    }

    private fun roughSummary(): RunSummary = RunSummary(
        score = 100,
        distanceM = 200f,
        isNewHighScore = false,
        highScore = 500,
        mercyHearts = 0,
        mercyMisses = 0,
        kindnessChain = 0,
        cleanPasses = 0,
        sparedCount = 0,
        hitsTaken = 2,
        seedsCollected = 0,
        bloomConversions = 0,
        lastKiller = null,
        restQuote = "Breathe.",
        forestMood = ForestMood.FEARFUL
    )

    private fun gentleSummary(): RunSummary = RunSummary(
        score = 400,
        distanceM = 800f,
        isNewHighScore = false,
        highScore = 500,
        mercyHearts = 4,
        mercyMisses = 4,
        kindnessChain = 6,
        cleanPasses = 12,
        sparedCount = 2,
        hitsTaken = 0,
        seedsCollected = 8,
        bloomConversions = 1,
        lastKiller = null,
        restQuote = "Gently.",
        forestMood = ForestMood.GENTLE,
        pacifistRouteTier = PacifistRouteTier.MERCIFUL
    )
}
