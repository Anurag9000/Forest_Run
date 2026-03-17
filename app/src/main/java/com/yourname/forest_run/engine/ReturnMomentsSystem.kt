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
    ): ReturnMoment? =
        buildGardenMoment(context.applicationContext, summary, nowMs, persist = true)

    fun previewGardenMoment(
        context: Context,
        summary: RunSummary?,
        nowMs: Long = System.currentTimeMillis()
    ): ReturnMoment? =
        buildGardenMoment(context.applicationContext, summary, nowMs, persist = false)

    private fun buildGardenMoment(
        appContext: Context,
        summary: RunSummary?,
        nowMs: Long,
        persist: Boolean
    ): ReturnMoment? {
        val previous = SaveManager.loadReturnMomentState(appContext)
        val dayId = nowMs / DAY_MS
        val alreadyGreetedToday = previous.lastGardenGreetingDay == dayId
        val bondedVisitor = RelationshipArcSystem.preferredGardenVisitor(appContext)
        val milestoneBond = RelationshipArcSystem.preferredGardenVisitor(appContext, RelationshipStage.MILESTONE)
        val milestoneReward = RelationshipArcSystem.featuredMilestoneReward(appContext)
        val repeatFriend = RelationshipArcSystem.featuredRepeatFriend(appContext)
        val strainedBond = RelationshipArcSystem.featuredStrainedBond(appContext)
        val repeatedKiller = PersistentMemoryManager.featuredRepeatKiller(appContext)
        val repeatedHarmCreature = PersistentMemoryManager.featuredTenderCreature(appContext)
            ?: (summary?.lastKiller ?: PersistentMemoryManager.getLastKiller(appContext))?.takeIf {
                PersistentMemoryManager.getHitCount(appContext, it) >= 2
            }
        val repeatedKindnessCreature = PersistentMemoryManager.featuredWarmCreature(appContext)

        val moment = when {
            previous.lastActiveAtMs > 0L && nowMs - previous.lastActiveAtMs >= LONG_ABSENCE_MS ->
                if (repeatFriend != null) {
                    ReturnMoment(
                        "Still Here",
                        longAbsenceRepeatFriendLine(appContext, repeatFriend),
                        repeatFriend
                    )
                } else if (milestoneReward != null) {
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
            repeatedKiller != null && repeatedKiller == repeatedHarmCreature &&
                (summary?.hitsTaken ?: 0) > 0 &&
                PersistentMemoryManager.getHitCount(appContext, repeatedKiller) >= 3 ->
                ReturnMoment(
                    "Same Shadow",
                    repeatedKillerLine(repeatedKiller),
                    if (RelationshipArcSystem.isTracked(repeatedKiller)) repeatedKiller else bondedVisitor
                )
            strainedBond != null &&
                (summary?.hitsTaken ?: 0) > 0 &&
                (summary?.lastKiller == strainedBond || previous.roughRunStreak >= 1) ->
                ReturnMoment(
                    "Held At A Distance",
                    RelationshipArcSystem.strainedBondLine(appContext, strainedBond),
                    strainedBond
                )
            repeatedHarmCreature != null && ((summary?.hitsTaken ?: 0) > 0 || previous.roughRunStreak >= 2) ->
                ReturnMoment(
                    "Still Tender",
                    repeatedHarmLine(repeatedHarmCreature),
                    if (RelationshipArcSystem.isTracked(repeatedHarmCreature)) repeatedHarmCreature else bondedVisitor
                )
            summary != null &&
                summary.pacifistRouteTier == PacifistRouteTier.PEACEFUL &&
                summary.hitsTaken == 0 &&
                summary.bloomConversions >= 2 ->
                ReturnMoment(
                    "Peace Held",
                    peacefulBloomLine(milestoneReward?.type ?: bondedVisitor),
                    milestoneReward?.type ?: bondedVisitor ?: EntityType.OWL
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
            summary != null &&
                summary.pacifistRouteTier == PacifistRouteTier.KIND &&
                (summary.sparedCount > 0 || summary.kindnessChain >= 4) ->
                ReturnMoment(
                    "Kindness Stayed",
                    kindRouteLine(repeatedKindnessCreature ?: bondedVisitor),
                    repeatedKindnessCreature ?: bondedVisitor ?: EntityType.CAT
                )
            summary?.pacifistRouteTier == PacifistRouteTier.PEACEFUL ->
                ReturnMoment(
                    "Peace Kept",
                    peacefulRouteLine(milestoneReward?.type ?: bondedVisitor),
                    milestoneReward?.type ?: bondedVisitor ?: EntityType.CAT
                )
            summary != null &&
                summary.pacifistRouteTier == PacifistRouteTier.MERCIFUL &&
                summary.hitsTaken == 0 &&
                summary.sparedCount > 0 &&
                repeatFriend != null ->
                ReturnMoment(
                    "Mercy Was Noticed",
                    mercifulFriendLine(repeatFriend),
                    repeatFriend
                )
            summary?.pacifistRouteTier == PacifistRouteTier.MERCIFUL && summary.hitsTaken == 0 ->
                ReturnMoment(
                    "Mercy Stayed",
                    mercifulRouteLine(bondedVisitor),
                    bondedVisitor ?: EntityType.CAT
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
            repeatFriend != null && (summary?.hitsTaken ?: 0) == 0 && (summary?.cleanPasses ?: 0) >= 12 ->
                ReturnMoment(
                    "Easy Company",
                    cleanFriendLine(appContext, repeatFriend),
                    repeatFriend
                )
            repeatFriend != null && (summary?.hitsTaken ?: 0) == 0 &&
                ((summary?.cleanPasses ?: 0) >= 8 || (summary?.sparedCount ?: 0) > 0) ->
                ReturnMoment(
                    "Kept Finding You",
                    RelationshipArcSystem.repeatFriendLine(appContext, repeatFriend),
                    repeatFriend
                )
            repeatedKindnessCreature != null &&
                ((summary?.sparedCount ?: 0) > 0 || (summary?.kindnessChain ?: 0) >= 4) ->
                ReturnMoment(
                    "Stayed Gentle",
                    kindnessLine(repeatedKindnessCreature),
                    if (RelationshipArcSystem.isTracked(repeatedKindnessCreature)) repeatedKindnessCreature else bondedVisitor
                )
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

        if (persist) {
            SaveManager.saveReturnMomentState(
                appContext,
                previous.copy(
                    lastActiveAtMs = nowMs,
                    lastGardenGreetingDay = dayId
                )
            )
        }
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

    private fun longAbsenceRepeatFriendLine(context: Context, type: EntityType): String = when (type) {
        EntityType.CAT -> "The cat behaved like your quiet patch had only been borrowed, not lost."
        EntityType.FOX -> "The fox left the brighter trail waiting like it trusted you to answer eventually."
        EntityType.WOLF -> "The grove kept its steadier courage in place until you came back for it."
        EntityType.DOG -> "The dog's welcome feels like excitement that refused to go stale."
        EntityType.OWL -> "The owl kept the dark edge open like it knew absence was not the end of the pattern."
        EntityType.EAGLE -> "Even the sky feels like it kept recognizing your place in it."
        else -> RelationshipArcSystem.lineFor(context, type, RelationshipArcSystem.Event.RETURN)
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

    private fun repeatedKillerLine(type: EntityType): String = when (type) {
        EntityType.CAT -> "The cat has started to feel like the same lesson arriving in the same place."
        EntityType.FOX -> "The fox keeps finding the same hesitation in you and turning it into a pattern."
        EntityType.WOLF -> "The grove knows the shape of that same howl reaching you again."
        EntityType.DOG -> "The same bark-line keeps coming home with you, even after the run ends."
        EntityType.HEDGEHOG -> "The thorns have started to feel less like accidents and more like a habit the path remembers."
        EntityType.DUCK -> "The duck keeps returning to the same low surprise your body still has not forgiven."
        EntityType.TIT, EntityType.CHICKADEE -> "The air keeps circling back to the same place your timing gives way."
        EntityType.OWL -> "The owl has started to feel like the same shadow finding you twice."
        EntityType.EAGLE -> "The eagle's mark has started to feel like a memory, not a single mistake."
        EntityType.CACTUS, EntityType.LILY_OF_VALLEY, EntityType.HYACINTH, EntityType.EUCALYPTUS,
        EntityType.VANILLA_ORCHID, EntityType.WEEPING_WILLOW, EntityType.JACARANDA, EntityType.BAMBOO,
        EntityType.CHERRY_BLOSSOM -> "The forest keeps returning to the same shape of trouble until you answer it differently."
    }

    private fun kindRouteLine(type: EntityType?): String = when (type) {
        EntityType.CAT -> "The cat treated your kinder return like something it had been waiting to believe."
        EntityType.FOX -> "The fox left you a brighter trail after a run that stayed kind."
        EntityType.WOLF -> "The grove kept the gentler courage of that run instead of the fear."
        EntityType.DOG -> "The dog's welcome sounds like it noticed the kindness before the score did."
        EntityType.OWL -> "Even the owl lets the dark edge rest a little after a kinder run."
        EntityType.EAGLE -> "The sky kept more softness than severity after that return."
        else -> "Kindness stayed in the garden long enough to count as part of home."
    }

    private fun peacefulRouteLine(type: EntityType?): String = when (type) {
        EntityType.CAT -> "The cat kept the whole garden quieter after how peacefully you crossed the path."
        EntityType.FOX -> "Even the fox's trail looks gentler after a run that stayed peaceful."
        EntityType.WOLF -> "The grove sounds almost restful after a run that never needed to bare its teeth."
        EntityType.DOG -> "The dog's joy somehow managed to come home quietly with you."
        EntityType.OWL -> "The owl left the night calm instead of severe after that run."
        EntityType.EAGLE -> "Even the sky looks less stern after a run that carried so much peace."
        else -> "The whole garden keeps the hush of the run you carried home peacefully."
    }

    private fun peacefulBloomLine(type: EntityType?): String = when (type) {
        EntityType.CAT -> "The cat kept even Bloom from feeling loud after a run that peaceful."
        EntityType.FOX -> "Even Bloom came home looking more graceful than wild after that run."
        EntityType.WOLF -> "The grove held Bloom and peace together without letting either of them break."
        EntityType.DOG -> "The dog's joy somehow made room for Bloom without breaking the hush."
        EntityType.OWL -> "The owl left the branches glowing softly instead of severely after that peaceful Bloom."
        EntityType.EAGLE -> "Even the charged sky looked calm after a peaceful Bloom return."
        else -> "Bloom stayed bright without breaking the hush you carried home."
    }

    private fun mercifulRouteLine(type: EntityType?): String = when (type) {
        EntityType.CAT -> "The cat seems to trust the quiet shape mercy left behind."
        EntityType.FOX -> "The fox leaves more room in the trail after a merciful return."
        EntityType.WOLF -> "The grove remembers when calm held longer than fear."
        EntityType.DOG -> "The dog's welcome sounds softer when the run comes home full of mercy."
        EntityType.OWL -> "The owl lets the dark edge feel lighter after a merciful run."
        EntityType.EAGLE -> "The sky feels less punishing when mercy keeps making it home."
        else -> "Mercy stayed in the garden longer than the run itself."
    }

    private fun mercifulFriendLine(type: EntityType): String = when (type) {
        EntityType.CAT -> "The cat treated that merciful return like proof your softer timing was real."
        EntityType.FOX -> "The fox looked less interested in testing you than in seeing if mercy would last."
        EntityType.WOLF -> "The grove remembered that you chose mercy and answered with less edge."
        EntityType.DOG -> "The dog's welcome sounds like it noticed mercy before anything else."
        EntityType.OWL -> "The owl kept the night open a little longer after a merciful return."
        EntityType.EAGLE -> "Even the eagle's shadow feels less severe when mercy keeps making it home."
        else -> "Something familiar noticed the mercy before the rest of the garden did."
    }

    private fun cleanFriendLine(context: Context, type: EntityType): String = when (type) {
        EntityType.CAT -> "The cat made your clean return feel like the evening had settled exactly where it meant to."
        EntityType.FOX -> "The fox seemed almost pleased that your timing stayed graceful all the way through."
        EntityType.WOLF -> "The grove carried your calm back in beside the wolf's respect."
        EntityType.DOG -> "The dog's joy feels even brighter when the whole run stayed clean."
        EntityType.OWL -> "The owl watched that clean return like it had finally become familiar."
        EntityType.EAGLE -> "Even the eagle's sky seemed to admit that your calm held the whole way."
        else -> RelationshipArcSystem.repeatFriendLine(context, type)
    }
}
