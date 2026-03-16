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
        val milestoneReward = RelationshipArcSystem.featuredMilestoneReward(appContext)
        val repeatedHarmCreature = PersistentMemoryManager.featuredTenderCreature(appContext)
            ?: (summary?.lastKiller ?: PersistentMemoryManager.getLastKiller(appContext))?.takeIf {
                PersistentMemoryManager.getHitCount(appContext, it) >= 2
            }
        val repeatedKindnessCreature = PersistentMemoryManager.featuredWarmCreature(appContext)

        val moment = when {
            previous.lastActiveAtMs > 0L && nowMs - previous.lastActiveAtMs >= LONG_ABSENCE_MS ->
                if (milestoneReward != null) {
                    ReturnMoment(
                        "You Were Missed",
                        missedLine(milestoneReward.type),
                        milestoneReward.type
                    )
                } else {
                    bondedVisitor?.let {
                        ReturnMoment("Welcome Back", RelationshipArcSystem.lineFor(appContext, it, RelationshipArcSystem.Event.RETURN), it)
                    } ?: ReturnMoment("Welcome Back", "The willow kept your place.", EntityType.CAT)
                }
            repeatedHarmCreature != null && ((summary?.hitsTaken ?: 0) > 0 || previous.roughRunStreak >= 2) ->
                ReturnMoment(
                    "Still Tender",
                    repeatedHarmLine(repeatedHarmCreature),
                    if (RelationshipArcSystem.isTracked(repeatedHarmCreature)) repeatedHarmCreature else bondedVisitor
                )
            milestoneReward != null && (summary?.kindnessChain ?: 0) >= 5 && (summary?.sparedCount ?: 0) >= 1 ->
                ReturnMoment(
                    "Kept Company",
                    milestoneWarmthLine(milestoneReward.type),
                    milestoneReward.type
                )
            milestoneReward != null && (summary?.cleanPasses ?: 0) >= 12 && (summary?.hitsTaken ?: 0) == 0 ->
                ReturnMoment(
                    "Stayed With You",
                    steadyMilestoneLine(milestoneReward.type),
                    milestoneReward.type
                )
            repeatedKindnessCreature != null &&
                ((summary?.sparedCount ?: 0) > 0 || (summary?.kindnessChain ?: 0) >= 4) ->
                ReturnMoment(
                    "Stayed Gentle",
                    kindnessLine(repeatedKindnessCreature),
                    if (RelationshipArcSystem.isTracked(repeatedKindnessCreature)) repeatedKindnessCreature else bondedVisitor
                )
            previous.roughRunStreak >= 3 ->
                when {
                    bondedVisitor == EntityType.DOG || bondedVisitor == EntityType.CAT ->
                        ReturnMoment("Take A Breath", RelationshipArcSystem.lineFor(appContext, bondedVisitor, RelationshipArcSystem.Event.RETURN), bondedVisitor)
                    else -> ReturnMoment("Take A Breath", "Even the wind has softened for you.", EntityType.DOG)
                }
            (summary?.bloomConversions ?: 0) >= 4 ->
                when (milestoneReward?.type ?: milestoneBond) {
                    EntityType.OWL, EntityType.EAGLE -> ReturnMoment(
                        "Bloom Still Clings",
                        bloomLine((milestoneReward?.type ?: milestoneBond)!!),
                        milestoneReward?.type ?: milestoneBond
                    )
                    else -> ReturnMoment("Bloom Still Clings", "The garden is still lit by what followed you home from Bloom.", EntityType.OWL)
                }
            (summary?.kindnessChain ?: 0) >= 6 || (summary?.sparedCount ?: 0) >= 2 ->
                bondedVisitor?.let {
                    ReturnMoment("Gentle Footsteps", RelationshipArcSystem.lineFor(appContext, it, RelationshipArcSystem.Event.RETURN), it)
                } ?: ReturnMoment("Gentle Footsteps", "The path stayed kind long enough to remember you.", EntityType.CAT)
            (summary?.cleanPasses ?: 0) >= 10 && summary?.hitsTaken == 0 ->
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

    private fun missedLine(type: EntityType): String = when (type) {
        EntityType.CAT -> "The cat never really gave up your patch of quiet grass."
        EntityType.FOX -> "The fox kept the brighter trail waiting for your answer."
        EntityType.WOLF -> "The grove held its watch as if expecting your return."
        EntityType.DOG -> "The garden feels like it has been waiting excitedly all day."
        EntityType.OWL -> "The dark edge stayed open, like the owl expected you back."
        EntityType.EAGLE -> "Even the sky feels like it noticed how long you were gone."
        else -> "Something here held your place."
    }

    private fun milestoneWarmthLine(type: EntityType): String = when (type) {
        EntityType.CAT -> "The cat has started treating your gentler runs like part of home."
        EntityType.FOX -> "The fox leaves you a brighter answer when you come back kind."
        EntityType.WOLF -> "The wolf's watch softens when you keep choosing calm."
        EntityType.DOG -> "The dog's joy feels steadier when you come home kind."
        EntityType.OWL -> "The owl keeps the night lighter when your steps stay gentle."
        EntityType.EAGLE -> "Even the eagle's shadow feels less severe after a kinder run."
        else -> "Something familiar stayed close to the kindness you carried home."
    }

    private fun steadyMilestoneLine(type: EntityType): String = when (type) {
        EntityType.CAT -> "The cat trusts the quiet shape you leave behind after a clean run."
        EntityType.FOX -> "The fox seems pleased you finally made the whole trail look easy."
        EntityType.WOLF -> "The grove remembers that you kept your calm all the way through."
        EntityType.DOG -> "The dog treats a clean run like a celebration it can barely contain."
        EntityType.OWL -> "The owl watches a steady return like it was the whole lesson."
        EntityType.EAGLE -> "The eagle leaves the sky stern, but approving, after a run like that."
        else -> "Something here stayed with the steadiness of that run."
    }

    private fun bloomLine(type: EntityType): String = when (type) {
        EntityType.OWL -> "The owl left a little of Bloom hanging in the branches."
        EntityType.EAGLE -> "The eagle's sky still feels charged with the Bloom you carried home."
        else -> "Bloom left more light behind than the garden knows what to do with."
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

    private fun kindnessLine(type: EntityType): String = when (type) {
        EntityType.CAT -> "The cat keeps showing up like your gentleness finally made sense to it."
        EntityType.FOX -> "The fox leaves space in the trail now, as if kindness taught it your rhythm."
        EntityType.WOLF -> "The grove feels calmer where the wolf keeps deciding not to press harder."
        EntityType.DOG -> "The dog's joy has started sounding like recognition instead of interruption."
        EntityType.OWL -> "The owl lets the night feel more welcoming when you keep returning gently."
        EntityType.EAGLE -> "Even the eagle's shadow feels less severe when your runs keep ending in mercy."
        EntityType.HEDGEHOG -> "The path seems grateful that you stopped meeting every thorn with haste."
        EntityType.DUCK -> "The duck's lane feels more like a lesson remembered than a surprise waiting."
        EntityType.TIT, EntityType.CHICKADEE -> "The air feels friendlier when you stop treating every flutter like a threat."
        EntityType.CACTUS, EntityType.LILY_OF_VALLEY, EntityType.HYACINTH, EntityType.EUCALYPTUS,
        EntityType.VANILLA_ORCHID, EntityType.WEEPING_WILLOW, EntityType.JACARANDA, EntityType.BAMBOO,
        EntityType.CHERRY_BLOSSOM -> "The forest seems to notice when your gentleness starts lasting longer than a single run."
    }
}
