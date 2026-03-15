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
        val bondedVisitor = RelationshipArcSystem.preferredGardenVisitor(appContext)
        val milestoneBond = RelationshipArcSystem.preferredGardenVisitor(appContext, RelationshipStage.MILESTONE)
        val repeatedHarmCreature = (summary?.lastKiller ?: PersistentMemoryManager.getLastKiller(appContext))?.takeIf {
            PersistentMemoryManager.getHitCount(appContext, it) >= 2
        }

        val moment = when {
            previous.lastActiveAtMs > 0L && nowMs - previous.lastActiveAtMs >= LONG_ABSENCE_MS ->
                bondedVisitor?.let {
                    ReturnMoment("Welcome Back", RelationshipArcSystem.lineFor(appContext, it, RelationshipArcSystem.Event.RETURN), it)
                } ?: ReturnMoment("Welcome Back", "The willow kept your place.", EntityType.CAT)
            repeatedHarmCreature != null && ((summary?.hitsTaken ?: 0) > 0 || previous.roughRunStreak >= 2) ->
                ReturnMoment(
                    "Still Tender",
                    repeatedHarmLine(repeatedHarmCreature),
                    if (RelationshipArcSystem.isTracked(repeatedHarmCreature)) repeatedHarmCreature else bondedVisitor
                )
            previous.roughRunStreak >= 3 ->
                when {
                    bondedVisitor == EntityType.DOG || bondedVisitor == EntityType.CAT ->
                        ReturnMoment("Take A Breath", RelationshipArcSystem.lineFor(appContext, bondedVisitor, RelationshipArcSystem.Event.RETURN), bondedVisitor)
                    else -> ReturnMoment("Take A Breath", "Even the wind has softened for you.", EntityType.DOG)
                }
            (summary?.kindnessChain ?: 0) >= 6 || (summary?.sparedCount ?: 0) >= 2 ->
                bondedVisitor?.let {
                    ReturnMoment("Gentle Footsteps", RelationshipArcSystem.lineFor(appContext, it, RelationshipArcSystem.Event.RETURN), it)
                } ?: ReturnMoment("Gentle Footsteps", "The path stayed kind long enough to remember you.", EntityType.CAT)
            summary?.cleanPasses ?: 0 >= 10 && summary?.hitsTaken == 0 ->
                bondedVisitor?.let {
                    ReturnMoment("Steady Hands", RelationshipArcSystem.lineFor(appContext, it, RelationshipArcSystem.Event.RETURN), it)
                } ?: ReturnMoment("Steady Hands", "The garden feels calmer after a run with no panic in it.")
            summary?.isNewHighScore == true ->
                bondedVisitor?.let {
                    ReturnMoment("That Run Lingers", RelationshipArcSystem.lineFor(appContext, it, RelationshipArcSystem.Event.RETURN), it)
                } ?: ReturnMoment("That Run Lingers", "A fox waits near the path, still impressed.", EntityType.FOX)
            (summary?.sparedCount ?: 0) > 0 ->
                milestoneBond?.let {
                    ReturnMoment("Quiet Company", RelationshipArcSystem.lineFor(appContext, it, RelationshipArcSystem.Event.RETURN), it)
                } ?: ReturnMoment("Quiet Company", "Something small and warm has stayed behind.", EntityType.CAT)
            (summary?.bloomConversions ?: 0) >= 2 ->
                when (milestoneBond) {
                    EntityType.OWL, EntityType.EAGLE -> ReturnMoment("Afterglow", RelationshipArcSystem.lineFor(appContext, milestoneBond, RelationshipArcSystem.Event.RETURN), milestoneBond)
                    else -> ReturnMoment("Afterglow", "The night keeps a little of your Bloom.", EntityType.OWL)
                }
            !alreadyGreetedToday ->
                bondedVisitor?.let {
                    ReturnMoment("Good To See You", RelationshipArcSystem.lineFor(appContext, it, RelationshipArcSystem.Event.RETURN), it)
                } ?: ReturnMoment("Good To See You", "The garden wakes a little when you do.")
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

    private fun repeatedHarmLine(type: EntityType): String = when (type) {
        EntityType.CAT -> "The garden stays careful around the place the cat keeps catching you."
        EntityType.FOX -> "The fox still leaves you thinking about the jump you missed."
        EntityType.WOLF -> "The grove remembers the same howl reaching you more than once."
        EntityType.DOG -> "Even home can still hear the bark line you never quite escaped."
        EntityType.HEDGEHOG -> "The path is trying to teach gentleness where the thorns kept winning."
        EntityType.DUCK -> "The lane still feels low where the duck kept surprising you."
        EntityType.TIT, EntityType.CHICKADEE -> "The air has not quite forgotten the flock that kept rushing you."
        EntityType.OWL -> "Night has gone watchful around the place the owl keeps finding."
        EntityType.EAGLE -> "The sky still feels marked where the eagle kept choosing you."
        EntityType.CACTUS, EntityType.LILY_OF_VALLEY, EntityType.HYACINTH, EntityType.EUCALYPTUS,
        EntityType.VANILLA_ORCHID, EntityType.WEEPING_WILLOW, EntityType.JACARANDA, EntityType.BAMBOO,
        EntityType.CHERRY_BLOSSOM -> "The garden remembers which shape of forest kept brushing against your nerves."
    }
}
