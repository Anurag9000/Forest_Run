package com.yourname.forest_run.engine

import android.content.Context
import com.yourname.forest_run.entities.EntityType

data class ReturnMoment(
    val title: String,
    val line: String,
    val visitor: EntityType? = null
)

data class ReturnMomentState(
    val lastActiveAtMs: Long = 0L,
    val lastGardenGreetingDay: Long = -1L,
    val roughRunStreak: Int = 0
)

object ReturnMomentsSystem {
    private const val DAY_MS = 24L * 60L * 60L * 1_000L
    private const val LONG_ABSENCE_MS = 36L * 60L * 60L * 1_000L

    fun recordRunOutcome(context: Context, summary: RunSummary, nowMs: Long = System.currentTimeMillis()) {
        val previous = SaveManager.loadReturnMomentState(context.applicationContext)
        val roughRun = summary.forestMood == ForestMood.FEARFUL ||
            (summary.hitsTaken >= 2 && summary.distanceM < 650f) ||
            (summary.hitsTaken > 0 && summary.kindnessChain == 0 && summary.seedsCollected < 4)
        SaveManager.saveReturnMomentState(
            context.applicationContext,
            previous.copy(
                lastActiveAtMs = nowMs,
                roughRunStreak = if (roughRun) previous.roughRunStreak + 1 else 0
            )
        )
    }

    fun resolveGardenMoment(
        context: Context,
        summary: RunSummary?,
        nowMs: Long = System.currentTimeMillis()
    ): ReturnMoment? {
        val appContext = context.applicationContext
        val previous = SaveManager.loadReturnMomentState(appContext)
        val dayId = nowMs / DAY_MS
        val alreadyGreetedToday = previous.lastGardenGreetingDay == dayId

        val moment = when {
            previous.lastActiveAtMs > 0L && nowMs - previous.lastActiveAtMs >= LONG_ABSENCE_MS ->
                ReturnMoment("Welcome Back", "The willow kept your place.", EntityType.CAT)
            previous.roughRunStreak >= 3 ->
                ReturnMoment("Take A Breath", "Even the wind has softened for you.", EntityType.DOG)
            summary?.isNewHighScore == true ->
                ReturnMoment("That Run Lingers", "A fox waits near the path, still impressed.", EntityType.FOX)
            (summary?.sparedCount ?: 0) > 0 ->
                ReturnMoment("Quiet Company", "Something small and warm has stayed behind.", EntityType.CAT)
            (summary?.bloomConversions ?: 0) >= 2 ->
                ReturnMoment("Afterglow", "The night keeps a little of your Bloom.", EntityType.OWL)
            !alreadyGreetedToday ->
                ReturnMoment("Good To See You", "The garden wakes a little when you do.")
            else -> null
        }

        SaveManager.saveReturnMomentState(
            appContext,
            previous.copy(
                lastActiveAtMs = nowMs,
                lastGardenGreetingDay = dayId
            )
        )
        return moment
    }
}
