package com.anurag9000.forestrun.engine

import android.content.Context
import com.anurag9000.forestrun.entities.EntityType

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
        val dayId = localCalendarDayId(nowMs)
        val alreadyGreetedToday = previous.lastGardenGreetingDay == dayId
        val bondedVisitor = RelationshipArcSystem.preferredGardenVisitor(appContext)
        val milestoneBond = RelationshipArcSystem.preferredGardenVisitor(appContext, RelationshipStage.MILESTONE)
        val milestoneReward = RelationshipArcSystem.featuredMilestoneReward(appContext)
        val repeatFriend = RelationshipArcSystem.featuredRepeatFriend(appContext)
        val strainedBond = RelationshipArcSystem.featuredStrainedBond(appContext)
        val peacefulBiome = PersistentMemoryManager.featuredPeaceBiome(appContext)
        val repeatedKiller = PersistentMemoryManager.featuredRepeatKiller(appContext)
        val repeatedHarmCreature = PersistentMemoryManager.featuredTenderCreature(appContext)
            ?: (summary?.lastKiller ?: PersistentMemoryManager.getLastKiller(appContext))?.takeIf {
                PersistentMemoryManager.getHitCount(appContext, it) >= 2
            }
        val repeatedKindnessCreature = PersistentMemoryManager.featuredWarmCreature(appContext)
        val longAbsence = previous.lastActiveAtMs > 0L && nowMs - previous.lastActiveAtMs >= LONG_ABSENCE_MS

        val moment = when {
            longAbsence &&
                summary != null &&
                milestoneReward != null &&
                summary.hitsTaken == 0 &&
                (summary.pacifistRouteTier == PacifistRouteTier.PEACEFUL ||
                    summary.pacifistRouteTier == PacifistRouteTier.MERCIFUL ||
                    summary.forestMood == ForestMood.GENTLE) ->
                ReturnMoment(
                    "Nothing Closed",
                    longAbsenceMilestoneLine(milestoneReward.type),
                    milestoneReward.type
                )
            longAbsence &&
                summary != null &&
                repeatFriend != null &&
                summary.hitsTaken == 0 &&
                (summary.forestMood == ForestMood.GENTLE || summary.kindnessChain >= 4 || summary.sparedCount >= 1) ->
                ReturnMoment(
                    "Still Gentle Here",
                    longAbsenceGentleFriendLine(repeatFriend),
                    repeatFriend
                )
            longAbsence &&
                summary != null &&
                bondedVisitor != null &&
                (summary.forestMood == ForestMood.FEARFUL || summary.hitsTaken > 0 || previous.roughRunStreak >= 1) ->
                ReturnMoment(
                    "You Came Back Worn",
                    longAbsenceComfortLine(bondedVisitor),
                    bondedVisitor
                )
            longAbsence ->
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
            repeatedKiller != null &&
                repeatedKiller == repeatedHarmCreature &&
                (summary?.hitsTaken ?: 0) > 0 &&
                previous.roughRunStreak >= 2 &&
                PersistentMemoryManager.getHitCount(appContext, repeatedKiller) >= 4 ->
                ReturnMoment(
                    "You Were Already Bracing",
                    repeatedKillerPressureLine(repeatedKiller),
                    if (RelationshipArcSystem.isTracked(repeatedKiller)) repeatedKiller else bondedVisitor
                )
            repeatedKiller != null && repeatedKiller == repeatedHarmCreature &&
                (summary?.hitsTaken ?: 0) > 0 &&
                PersistentMemoryManager.getHitCount(appContext, repeatedKiller) >= 3 ->
                ReturnMoment(
                    "Same Shadow",
                    repeatedKillerLine(repeatedKiller),
                    if (RelationshipArcSystem.isTracked(repeatedKiller)) repeatedKiller else bondedVisitor
                )
            strainedBond != null &&
                previous.roughRunStreak >= 2 &&
                ((summary?.hitsTaken ?: 0) > 0 || summary?.lastKiller == strainedBond) ->
                ReturnMoment(
                    "Nothing In You Unclenched",
                    strainedBondPressureLine(strainedBond),
                    strainedBond
                )
            strainedBond != null &&
                (summary?.hitsTaken ?: 0) > 0 &&
                (summary?.lastKiller == strainedBond || previous.roughRunStreak >= 1) ->
                ReturnMoment(
                    "Held At A Distance",
                    RelationshipArcSystem.strainedBondLine(appContext, strainedBond),
                    strainedBond
                )
            repeatedHarmCreature != null &&
                previous.roughRunStreak >= 3 &&
                ((summary?.hitsTaken ?: 0) > 0 || summary?.forestMood == ForestMood.FEARFUL) ->
                ReturnMoment(
                    "It Came Home With You",
                    repeatedHarmWeightLine(repeatedHarmCreature),
                    if (RelationshipArcSystem.isTracked(repeatedHarmCreature)) repeatedHarmCreature else bondedVisitor
                )
            repeatedHarmCreature != null && ((summary?.hitsTaken ?: 0) > 0 || previous.roughRunStreak >= 2) ->
                ReturnMoment(
                    "Still Tender",
                    repeatedHarmLine(repeatedHarmCreature),
                    if (RelationshipArcSystem.isTracked(repeatedHarmCreature)) repeatedHarmCreature else bondedVisitor
                )
            milestoneReward != null &&
                summary != null &&
                summary.pacifistRouteTier == PacifistRouteTier.PEACEFUL &&
                summary.hitsTaken == 0 ->
                ReturnMoment(
                    "Peace Shared",
                    milestonePeaceLine(milestoneReward.type),
                    milestoneReward.type
                )
            summary != null &&
                summary.pacifistRouteTier == PacifistRouteTier.PEACEFUL &&
                summary.hitsTaken == 0 &&
                summary.bloomConversions >= 2 ->
                ReturnMoment(
                    "Peace Held",
                    peacefulBloomLine(peacefulBiome?.biome, milestoneReward?.type ?: bondedVisitor),
                    milestoneReward?.type ?: bondedVisitor ?: EntityType.OWL
                )
            milestoneReward != null &&
                (summary?.kindnessChain ?: 0) >= 5 &&
                (summary?.sparedCount ?: 0) >= 1 &&
                !(
                    summary?.pacifistRouteTier == PacifistRouteTier.MERCIFUL &&
                        (summary.mercyHearts) >= 5 &&
                        summary.sparedCount >= 2 &&
                        summary.hitsTaken == 0
                    ) &&
                !(
                    (summary?.hitsTaken ?: 0) == 0 &&
                        (
                            ((summary?.cleanPasses ?: 0) >= 15 && (summary?.bloomConversions ?: 0) >= 3) ||
                                ((summary?.cleanPasses ?: 0) >= 14 && (summary?.distanceM ?: 0f) >= 900f)
                            )
                    ) ->
                ReturnMoment(
                    "Kept Company",
                    milestoneWarmthLine(milestoneReward.type),
                    milestoneReward.type
                )
            milestoneReward != null &&
                (summary?.cleanPasses ?: 0) >= 12 &&
                (summary?.hitsTaken ?: 0) == 0 &&
                !(
                    ((summary?.cleanPasses ?: 0) >= 15 && (summary?.bloomConversions ?: 0) >= 3) ||
                        ((summary?.cleanPasses ?: 0) >= 14 && (summary?.distanceM ?: 0f) >= 900f)
                    ) ->
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
                    kindRouteLine(peacefulBiome?.biome, repeatedKindnessCreature ?: bondedVisitor),
                    repeatedKindnessCreature ?: bondedVisitor ?: EntityType.CAT
                )
            summary?.pacifistRouteTier == PacifistRouteTier.PEACEFUL ->
                ReturnMoment(
                    "Peace Kept",
                    peacefulRouteLine(peacefulBiome?.biome, milestoneReward?.type ?: bondedVisitor),
                    milestoneReward?.type ?: bondedVisitor ?: EntityType.CAT
                )
            milestoneReward != null &&
                summary != null &&
                summary.pacifistRouteTier == PacifistRouteTier.MERCIFUL &&
                summary.hitsTaken == 0 &&
                summary.mercyHearts >= 5 &&
                summary.sparedCount >= 2 ->
                ReturnMoment(
                    "Bond Kept Open",
                    milestoneMercyLine(milestoneReward.type),
                    milestoneReward.type
                )
            summary != null &&
                summary.pacifistRouteTier == PacifistRouteTier.MERCIFUL &&
                summary.hitsTaken == 0 &&
                summary.mercyHearts >= 6 &&
                summary.bloomConversions >= 2 ->
                ReturnMoment(
                    "Mercy Kept The Light",
                    mercyBloomLine(peacefulBiome?.biome, repeatFriend ?: bondedVisitor),
                    repeatFriend ?: bondedVisitor ?: EntityType.OWL
                )
            summary != null &&
                summary.pacifistRouteTier == PacifistRouteTier.MERCIFUL &&
                summary.hitsTaken == 0 &&
                summary.mercyHearts >= 6 &&
                summary.sparedCount >= 2 &&
                repeatFriend != null ->
                ReturnMoment(
                    "Mercy Learned You",
                    deepMercifulFriendLine(repeatFriend),
                    repeatFriend
                )
            summary != null &&
                summary.pacifistRouteTier == PacifistRouteTier.MERCIFUL &&
                summary.hitsTaken == 0 &&
                summary.mercyHearts >= 6 &&
                summary.sparedCount >= 2 ->
                ReturnMoment(
                    "Mercy Stayed Open",
                    broadMercyLine(peacefulBiome?.biome, bondedVisitor),
                    bondedVisitor ?: EntityType.CAT
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
                    mercifulRouteLine(peacefulBiome?.biome, bondedVisitor),
                    bondedVisitor ?: EntityType.CAT
                )
            previous.roughRunStreak >= 3 ->
                when {
                    bondedVisitor == EntityType.DOG || bondedVisitor == EntityType.CAT ->
                        ReturnMoment("Take A Breath", RelationshipArcSystem.lineFor(appContext, bondedVisitor, RelationshipArcSystem.Event.RETURN), bondedVisitor)
                    else -> ReturnMoment("Take A Breath", "Even the wind has softened for you.", EntityType.DOG)
                }
            (summary?.cleanPasses ?: 0) >= 15 &&
                (summary?.hitsTaken ?: 0) == 0 &&
                (summary?.bloomConversions ?: 0) >= 3 ->
                when (milestoneReward?.type ?: milestoneBond) {
                    EntityType.OWL, EntityType.EAGLE -> ReturnMoment(
                        "Nothing Disturbed",
                        clearBloomLine((milestoneReward?.type ?: milestoneBond)!!),
                        milestoneReward?.type ?: milestoneBond
                    )
                    else -> ReturnMoment(
                        "Nothing Disturbed",
                        "Even Bloom came home without breaking the clean shape of the run.",
                        bondedVisitor ?: EntityType.OWL
                    )
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
                    if ((summary?.cleanPasses ?: 0) >= 14 && (summary?.distanceM ?: 0f) >= 900f) {
                        "Clear Company"
                    } else {
                        "Easy Company"
                    },
                    if ((summary?.cleanPasses ?: 0) >= 14 && (summary?.distanceM ?: 0f) >= 900f) {
                        deepCleanFriendLine(repeatFriend)
                    } else {
                        cleanFriendLine(appContext, repeatFriend)
                    },
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
                ((summary?.sparedCount ?: 0) > 0 || (summary?.kindnessChain ?: 0) >= 4) &&
                !(
                    (summary?.hitsTaken ?: 0) == 0 &&
                        (
                            ((summary?.cleanPasses ?: 0) >= 15 && (summary?.bloomConversions ?: 0) >= 3) ||
                                ((summary?.cleanPasses ?: 0) >= 14 && (summary?.distanceM ?: 0f) >= 900f)
                            )
                    ) ->
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
                if ((summary.cleanPasses) >= 14 && summary.distanceM >= 900f) {
                    bondedVisitor?.let {
                        ReturnMoment("Clear All The Way", deepCleanLine(it), it)
                    } ?: ReturnMoment("Clear All The Way", "Nothing in the run managed to break the clear shape you held onto.")
                } else {
                    bondedVisitor?.let {
                        ReturnMoment("Steady Hands", RelationshipArcSystem.lineFor(appContext, it, RelationshipArcSystem.Event.RETURN), it)
                    } ?: ReturnMoment("Steady Hands", "The garden feels calmer after a run with no panic in it.")
                }
            milestoneReward != null && summary?.isNewHighScore == true ->
                ReturnMoment(
                    "Known Height",
                    milestoneHighScoreLine(milestoneReward.type),
                    milestoneReward.type
                )
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

    private fun longAbsenceComfortLine(type: EntityType): String = when (type) {
        EntityType.CAT -> "The cat did not ask anything of your return except that you let the quiet hold you for a while."
        EntityType.FOX -> "The fox left the trail unsharp this time, like it could tell you came back tired instead of ready to be tested."
        EntityType.WOLF -> "The wolf kept watch without pressure, as if it understood that making it back mattered more than proving anything."
        EntityType.DOG -> "The dog met you with joy that softened into company the moment it noticed how worn you were."
        EntityType.OWL -> "The owl kept the dark edge open without severity, like it knew you needed the night to hold still."
        EntityType.EAGLE -> "Even the eagle let the sky feel steadier than stern, as if the return itself deserved the mercy."
        else -> "Something familiar let the return be tired without making it lonely."
    }

    private fun longAbsenceGentleFriendLine(type: EntityType): String = when (type) {
        EntityType.CAT -> "The cat behaved like the long gap had only made the shared quiet easier to recognize."
        EntityType.FOX -> "The fox answered the long absence with a brighter trail, like it trusted your gentler timing to find it again."
        EntityType.WOLF -> "The wolf let the old watch turn warm, as if the absence had only made the calm more meaningful."
        EntityType.DOG -> "The dog greeted the long return like joy had been waiting patiently instead of fading."
        EntityType.OWL -> "The owl kept the night familiar enough that the long distance back did not feel like a break in the pattern."
        EntityType.EAGLE -> "Even the eagle's sky felt like it had kept your line in place the whole time."
        else -> "Something gentle here made the long return feel recognized instead of resumed."
    }

    private fun longAbsenceMilestoneLine(type: EntityType): String = when (type) {
        EntityType.CAT -> "The cat left the shared pause exactly where you both had made it, like the bond never treated absence as closure."
        EntityType.FOX -> "The fox kept the answered trail open through the whole absence, like it never mistook distance for the end of the bond."
        EntityType.WOLF -> "The wolf held the watch through the long gap, as if respect had already decided not to close behind you."
        EntityType.DOG -> "The dog kept meeting the idea of your return halfway, like the bond had stayed in motion the whole time you were gone."
        EntityType.OWL -> "The owl kept the known shadow ready for you, as if the bond had been waiting with the night itself."
        EntityType.EAGLE -> "The eagle left the higher line in place, like the bond understood absence without mistaking it for loss."
        else -> "A stronger bond kept something open for you all the way through the absence."
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

    private fun milestonePeaceLine(type: EntityType): String = when (type) {
        EntityType.CAT -> "The cat kept the whole return inside the shared pause the two of you have learned to leave open."
        EntityType.FOX -> "The fox let the bright line stay playful instead of sharp, like peace now belongs to both sides of the trick."
        EntityType.WOLF -> "The wolf held the watch without pressure, like peace has become part of the pact instead of a lucky exception."
        EntityType.DOG -> "The dog met the return with joy that never had to break the hush."
        EntityType.OWL -> "The owl kept the dark edge soft enough for peace to feel practiced, not accidental."
        EntityType.EAGLE -> "The eagle held the sky in a calmer line, like peace had finally earned recognition."
        else -> "A stronger bond helped peace make it all the way home."
    }

    private fun milestoneMercyLine(type: EntityType): String = when (type) {
        EntityType.CAT -> "The cat answered your mercy by leaving the shared pause open instead of slipping back into distance."
        EntityType.FOX -> "The fox treated mercy like an answered trick this time, not a brief surprise."
        EntityType.WOLF -> "The wolf lowered the warning before you even reached home, like mercy has become something it now expects from you."
        EntityType.DOG -> "The dog met your mercy halfway, turning the return into something shared instead of only celebrated."
        EntityType.OWL -> "The owl let mercy belong to the dark edge instead of merely survive it."
        EntityType.EAGLE -> "The eagle marked mercy like a line worth holding, not a softness it had to test."
        else -> "A stronger bond kept mercy open all the way home."
    }

    private fun milestoneHighScoreLine(type: EntityType): String = when (type) {
        EntityType.CAT -> "The cat watched that height like it confirmed the quieter pace the two of you already trust."
        EntityType.FOX -> "The fox looked pleased that the brighter trail finally rose to meet you instead of catching you."
        EntityType.WOLF -> "The wolf treated that height like proof your calm has become something real."
        EntityType.DOG -> "The dog met the new height like it had been waiting to celebrate it with you."
        EntityType.OWL -> "The owl held above the run like that height already belonged to the shape it knows in you."
        EntityType.EAGLE -> "The eagle recognized the new height as a line you were finally ready to keep."
        else -> "A stronger bond recognized what that run managed to become."
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

    private fun repeatedKillerPressureLine(type: EntityType): String = when (type) {
        EntityType.CAT -> "The cat had you bracing for the same quiet mistake before it even reached you."
        EntityType.FOX -> "The fox now feels like a test your body starts failing before the trick fully arrives."
        EntityType.WOLF -> "The same howl had you tightening early, like the grove reached your nerves before it reached your path."
        EntityType.DOG -> "The bark-line came back already inside your shoulders, like you knew the rush before it happened."
        EntityType.HEDGEHOG -> "The thorns felt familiar enough that you were already protecting the old wound instead of meeting the new hop."
        EntityType.DUCK -> "The duck's low surprise had you preparing for the old miss before the lane even answered."
        EntityType.TIT, EntityType.CHICKADEE -> "The flock reached you as a memory first and a motion second."
        EntityType.OWL -> "The owl's shadow had you bracing long before the dive finished choosing you."
        EntityType.EAGLE -> "The mark felt like it landed in memory before it landed in the sky."
        EntityType.CACTUS, EntityType.LILY_OF_VALLEY, EntityType.HYACINTH, EntityType.EUCALYPTUS,
        EntityType.VANILLA_ORCHID, EntityType.WEEPING_WILLOW, EntityType.JACARANDA, EntityType.BAMBOO,
        EntityType.CHERRY_BLOSSOM -> "The forest shape that kept hurting you was already in your body before it was back on the path."
    }

    private fun strainedBondPressureLine(type: EntityType): String = when (type) {
        EntityType.CAT -> "The cat kept even its nearness edged enough that you came home without ever really unclenching."
        EntityType.FOX -> "The fox left the whole return feeling like a bright line you still did not trust yourself to answer."
        EntityType.WOLF -> "The wolf made the watch feel so tense you brought the bracing all the way home with you."
        EntityType.DOG -> "Even the dog's noise felt like something you had to survive before you could hear it as welcome again."
        EntityType.OWL -> "The owl left the dark edge so watchful that nothing in you softened back down on the way home."
        EntityType.EAGLE -> "The eagle held the sky tense enough that the return never fully left the marked place."
        else -> "The bond stayed strained enough that the whole return came home braced."
    }

    private fun repeatedHarmWeightLine(type: EntityType): String = when (type) {
        EntityType.CAT -> "The cat's lesson followed you home like the quiet itself had turned careful."
        EntityType.FOX -> "The fox left your timing carrying more apology than ease."
        EntityType.WOLF -> "The grove came back with you still sounding like it expected you to flinch."
        EntityType.DOG -> "The dog's rush still lived in your shoulders after the path had already ended."
        EntityType.HEDGEHOG -> "The thorns followed you home as caution instead of only pain."
        EntityType.DUCK -> "The duck's low lane stayed in your body longer than the run did."
        EntityType.TIT, EntityType.CHICKADEE -> "The flock never really left your nerves when the run ended."
        EntityType.OWL -> "The owl's shadow came home with you and kept the night tighter than it needed to be."
        EntityType.EAGLE -> "The eagle's mark lingered past the sky and into the way home felt."
        EntityType.CACTUS, EntityType.LILY_OF_VALLEY, EntityType.HYACINTH, EntityType.EUCALYPTUS,
        EntityType.VANILLA_ORCHID, EntityType.WEEPING_WILLOW, EntityType.JACARANDA, EntityType.BAMBOO,
        EntityType.CHERRY_BLOSSOM -> "The forest carried the same hurt home with you instead of leaving it on the path."
    }

    private fun kindRouteLine(biome: Biome?, type: EntityType?): String = when {
        biome != null -> "${biome.displayName} kept the kinder shape of your return close instead of letting it fade."
        type == EntityType.CAT -> "The cat treated your kinder return like something it had been waiting to believe."
        type == EntityType.FOX -> "The fox left you a brighter trail after a run that stayed kind."
        type == EntityType.WOLF -> "The grove kept the gentler courage of that run instead of the fear."
        type == EntityType.DOG -> "The dog's welcome sounds like it noticed the kindness before the score did."
        type == EntityType.OWL -> "Even the owl lets the dark edge rest a little after a kinder run."
        type == EntityType.EAGLE -> "The sky kept more softness than severity after that return."
        else -> "Kindness stayed in the garden long enough to count as part of home."
    }

    private fun peacefulRouteLine(biome: Biome?, type: EntityType?): String = when {
        biome != null -> "${biome.displayName} still feels at peace with the way you crossed it."
        type == EntityType.CAT -> "The cat kept the whole garden quieter after how peacefully you crossed the path."
        type == EntityType.FOX -> "Even the fox's trail looks gentler after a run that stayed peaceful."
        type == EntityType.WOLF -> "The grove sounds almost restful after a run that never needed to bare its teeth."
        type == EntityType.DOG -> "The dog's joy somehow managed to come home quietly with you."
        type == EntityType.OWL -> "The owl left the night calm instead of severe after that run."
        type == EntityType.EAGLE -> "Even the sky looks less stern after a run that carried so much peace."
        else -> "The whole garden keeps the hush of the run you carried home peacefully."
    }

    private fun peacefulBloomLine(biome: Biome?, type: EntityType?): String = when {
        biome != null -> "${biome.displayName} held both Bloom and peace without letting either of them turn harsh."
        type == EntityType.CAT -> "The cat kept even Bloom from feeling loud after a run that peaceful."
        type == EntityType.FOX -> "Even Bloom came home looking more graceful than wild after that run."
        type == EntityType.WOLF -> "The grove held Bloom and peace together without letting either of them break."
        type == EntityType.DOG -> "The dog's joy somehow made room for Bloom without breaking the hush."
        type == EntityType.OWL -> "The owl left the branches glowing softly instead of severely after that peaceful Bloom."
        type == EntityType.EAGLE -> "Even the charged sky looked calm after a peaceful Bloom return."
        else -> "Bloom stayed bright without breaking the hush you carried home."
    }

    private fun mercifulRouteLine(biome: Biome?, type: EntityType?): String = when {
        biome != null -> "${biome.displayName} feels less guarded now that mercy keeps finding it."
        type == EntityType.CAT -> "The cat seems to trust the quiet shape mercy left behind."
        type == EntityType.FOX -> "The fox leaves more room in the trail after a merciful return."
        type == EntityType.WOLF -> "The grove remembers when calm held longer than fear."
        type == EntityType.DOG -> "The dog's welcome sounds softer when the run comes home full of mercy."
        type == EntityType.OWL -> "The owl lets the dark edge feel lighter after a merciful run."
        type == EntityType.EAGLE -> "The sky feels less punishing when mercy keeps making it home."
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

    private fun mercyBloomLine(biome: Biome?, type: EntityType?): String = when {
        biome != null -> "${biome.displayName} held mercy and Bloom together without letting either turn harsh."
        type == EntityType.CAT -> "The cat watched mercy carry its own light home instead of letting Bloom overwhelm it."
        type == EntityType.FOX -> "The fox left the brighter trail alone long enough for mercy to keep the light."
        type == EntityType.WOLF -> "The grove held mercy and light together without bracing for harm."
        type == EntityType.DOG -> "Even the dog's joy made room for mercy before it made room for the light."
        type == EntityType.OWL -> "The owl left the branches lit by mercy instead of alarm."
        type == EntityType.EAGLE -> "Even the eagle's sky kept the gentler light this time."
        else -> "Mercy carried its own light all the way home."
    }

    private fun deepMercifulFriendLine(type: EntityType): String = when (type) {
        EntityType.CAT -> "The cat has started answering your mercy like it knows your gentler shape by heart."
        EntityType.FOX -> "The fox now treats mercy like a pattern it can recognize from you, not a lucky exception."
        EntityType.WOLF -> "The grove has started reading mercy in you before the wolf even tests it."
        EntityType.DOG -> "The dog answers your mercy like it was something it already expected from your return."
        EntityType.OWL -> "The owl has started meeting mercy like it recognizes your way through the dark."
        EntityType.EAGLE -> "Even the eagle answers your mercy like it has learned your line through the sky."
        else -> "Something familiar has started recognizing your mercy before the run is even over."
    }

    private fun broadMercyLine(biome: Biome?, type: EntityType?): String = when {
        biome != null -> "${biome.displayName} stayed softer because mercy never closed back up on the way home."
        type == EntityType.CAT -> "Mercy stayed open long enough for the cat to stop treating it like a surprise."
        type == EntityType.FOX -> "Mercy stayed open long enough for the fox to leave the trail bright instead of sharp."
        type == EntityType.WOLF -> "Mercy stayed open long enough for the grove to answer without bracing first."
        type == EntityType.DOG -> "Mercy stayed open long enough for even the dog's noise to sound welcoming."
        type == EntityType.OWL -> "Mercy stayed open long enough for the night to feel less guarded."
        type == EntityType.EAGLE -> "Mercy stayed open long enough for the sky to stop sounding severe."
        else -> "Mercy stayed open long enough to change the way home answered you."
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

    private fun deepCleanFriendLine(type: EntityType): String = when (type) {
        EntityType.CAT -> "The cat treated the whole return like nothing in it ever needed to break."
        EntityType.FOX -> "The fox let the full trail stay graceful, as if it knew your timing would hold all the way through."
        EntityType.WOLF -> "The grove answered your clean return like calm had finally stopped being temporary."
        EntityType.DOG -> "The dog's joy sounds almost proud when nothing in the run manages to shake loose."
        EntityType.OWL -> "The owl kept the whole night clear, as if it expected your line to hold from start to finish."
        EntityType.EAGLE -> "Even the eagle let the sky stay clean-edged when your line never faltered."
        else -> "Something familiar stayed beside the whole clean line of the run."
    }

    private fun deepCleanLine(type: EntityType): String = when (type) {
        EntityType.CAT -> "The cat watched the whole run stay clear enough to feel like trust."
        EntityType.FOX -> "The fox let the full trail stay bright, as if it knew your timing would hold."
        EntityType.WOLF -> "The grove kept the whole return clean instead of only calm."
        EntityType.DOG -> "The dog's welcome sounds like nothing in that run ever slipped out of your hands."
        EntityType.OWL -> "The owl kept the dark edge clear the whole way through."
        EntityType.EAGLE -> "Even the eagle's sky stayed clean and exact all the way home."
        else -> "The whole run stayed clear enough for home to keep its exact shape."
    }

    private fun clearBloomLine(type: EntityType): String = when (type) {
        EntityType.OWL -> "The owl let Bloom glow without letting it disturb the clear line you held."
        EntityType.EAGLE -> "Even the eagle's charged sky stayed exact while Bloom followed you home."
        else -> "Bloom came home bright without breaking the clean line of the run."
    }
}
