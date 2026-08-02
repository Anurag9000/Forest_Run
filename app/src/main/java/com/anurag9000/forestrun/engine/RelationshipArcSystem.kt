package com.anurag9000.forestrun.engine

import android.content.Context
import com.anurag9000.forestrun.entities.CostumeStyle
import com.anurag9000.forestrun.entities.EntityType

enum class RelationshipStage(val displayName: String) {
    FIRST_IMPRESSION("First"),
    RECOGNITION("Known"),
    TRUST("Trust"),
    MILESTONE("Bond")
}

private data class RelationshipThresholds(
    val recognitionScore: Int,
    val trustScore: Int,
    val milestoneScore: Int
)

private enum class RelationshipTone {
    WARM,
    NEUTRAL,
    CAUTIOUS
}

private enum class FamiliarityWarmth {
    NONE,
    GENTLE,
    PERSONAL,
    BONDED
}

private enum class StrainedConsequence {
    WARY,
    DISAPPOINTED,
    TENSE,
    FEARFUL
}

data class RelationshipEncounterTuning(
    val passBonusPoints: Int = 0,
    val passBonusSeeds: Int = 0,
    val mercyPaddingBonusPx: Float = 0f,
    val telegraphMultiplier: Float = 1f,
    val aggressionMultiplier: Float = 1f,
    val detectionMultiplier: Float = 1f,
    val buddyChanceBonus: Float = 0f
)

data class RelationshipMilestoneReward(
    val type: EntityType,
    val label: String,
    val summary: String,
    val traceLabel: String,
    val costumeReward: CostumeStyle? = null,
    val homePresenceLabel: String,
    val homePresenceLine: String,
    val bondRitualLabel: String,
    val bondRitualLine: String,
    val milestoneBubbleText: String,
    val milestoneFlavorText: String,
    val gardenReactionTitle: String,
    val gardenReactionLine: String
)

object RelationshipArcSystem {

    private val trackedTypes = setOf(
        EntityType.CAT,
        EntityType.FOX,
        EntityType.WOLF,
        EntityType.DOG,
        EntityType.OWL,
        EntityType.EAGLE
    )

    private val thresholds = mapOf(
        EntityType.CAT to RelationshipThresholds(2, 5, 9),
        EntityType.FOX to RelationshipThresholds(2, 6, 10),
        EntityType.WOLF to RelationshipThresholds(3, 7, 11),
        EntityType.DOG to RelationshipThresholds(2, 5, 8),
        EntityType.OWL to RelationshipThresholds(2, 5, 9),
        EntityType.EAGLE to RelationshipThresholds(2, 6, 10)
    )

    enum class Event {
        PASS,
        THREAT,
        SPARE,
        RETURN
    }

    enum class EncounterCue {
        MERCY,
        FOX_LANDING,
        WOLF_CHARGE,
        OWL_ALERT,
        EAGLE_LOCK,
        DOG_GREETING,
        DOG_MIDDLE,
        DOG_FAREWELL
    }

    fun isTracked(type: EntityType): Boolean = type in trackedTypes

    fun refreshStage(context: Context, type: EntityType): RelationshipStage {
        if (!isTracked(type)) return RelationshipStage.FIRST_IMPRESSION
        val stage = computeStage(
            type = type,
            encounters = SaveManager.loadEncounterCount(context.applicationContext, type),
            cleanPasses = SaveManager.loadCleanPassCount(context.applicationContext, type),
            spared = SaveManager.loadSparedCount(context.applicationContext, type),
            hits = SaveManager.loadHitCount(context.applicationContext, type)
        )
        SaveManager.saveRelationshipStage(context.applicationContext, type, stage)
        if (stage == RelationshipStage.MILESTONE) {
            unlockMilestone(context.applicationContext, type)
        }
        return stage
    }

    fun stageFor(context: Context, type: EntityType): RelationshipStage {
        if (!isTracked(type)) return RelationshipStage.FIRST_IMPRESSION
        val saved = SaveManager.loadRelationshipStage(context.applicationContext, type)
        return saved ?: refreshStage(context, type)
    }

    fun strongestRelationshipLabel(context: Context): String? {
        val strongest = strongestRelationship(context) ?: return null
        return "${formatName(strongest.first)} ${strongest.second.displayName}"
    }

    fun strongestRelationship(context: Context): Pair<EntityType, RelationshipStage>? {
        val strongest = trackedTypes.maxWithOrNull(
            compareBy<EntityType> { stageFor(context, it).ordinal }
                .thenBy { affinityScore(context, it) }
        ) ?: return null
        val stage = stageFor(context, strongest)
        if (stage == RelationshipStage.FIRST_IMPRESSION &&
            SaveManager.loadEncounterCount(context.applicationContext, strongest) == 0
        ) {
            return null
        }
        return strongest to stage
    }

    fun preferredGardenVisitor(
        context: Context,
        minimumStage: RelationshipStage = RelationshipStage.TRUST
    ): EntityType? {
        val strongest = strongestRelationship(context) ?: return null
        return strongest.first.takeIf { strongest.second.ordinal >= minimumStage.ordinal }
    }

    fun featuredRepeatFriend(
        context: Context,
        minimumStage: RelationshipStage = RelationshipStage.TRUST
    ): EntityType? {
        val appContext = context.applicationContext
        return relationshipsAtOrAbove(appContext, minimumStage)
            .filter { (type, _) ->
                isWarmBond(appContext, type) &&
                    SaveManager.loadSparedCount(appContext, type) >= 1
            }
            .maxWithOrNull(
                compareBy<Pair<EntityType, RelationshipStage>>(
                    { SaveManager.loadKindnessStreak(appContext, it.first) },
                    { it.second.ordinal },
                    { affinityScore(appContext, it.first) }
                )
            )
            ?.first
    }

    fun featuredStrainedBond(
        context: Context,
        minimumStage: RelationshipStage = RelationshipStage.RECOGNITION
    ): EntityType? {
        val appContext = context.applicationContext
        return relationshipsAtOrAbove(appContext, minimumStage)
            .filter { (type, _) -> isStrainedBond(appContext, type) }
            .maxWithOrNull(
                compareBy<Pair<EntityType, RelationshipStage>>(
                    { SaveManager.loadTenderStreak(appContext, it.first) },
                    { SaveManager.loadHitCount(appContext, it.first) },
                    { it.second.ordinal }
                )
            )
            ?.first
    }

    fun relationshipsAtOrAbove(
        context: Context,
        minimumStage: RelationshipStage = RelationshipStage.TRUST
    ): List<Pair<EntityType, RelationshipStage>> {
        val appContext = context.applicationContext
        return trackedTypes.mapNotNull { type ->
            val stage = stageFor(appContext, type)
            val encounters = SaveManager.loadEncounterCount(appContext, type)
            if (encounters == 0 || stage.ordinal < minimumStage.ordinal) {
                null
            } else {
                type to stage
            }
        }.sortedWith(
            compareByDescending<Pair<EntityType, RelationshipStage>> { it.second.ordinal }
                .thenByDescending { affinityScore(appContext, it.first) }
        )
    }

    fun isWarmBond(context: Context, type: EntityType): Boolean =
        isTracked(type) && toneFor(context.applicationContext, type) == RelationshipTone.WARM

    fun isStrainedBond(context: Context, type: EntityType): Boolean =
        isTracked(type) &&
            toneFor(context.applicationContext, type) == RelationshipTone.CAUTIOUS &&
            stageFor(context.applicationContext, type).ordinal >= RelationshipStage.RECOGNITION.ordinal

    fun hasUnlockedMilestone(context: Context, type: EntityType): Boolean =
        type in SaveManager.loadUnlockedRelationshipMilestones(context.applicationContext)

    fun unlockedMilestoneTypes(context: Context): List<EntityType> =
        SaveManager.loadUnlockedRelationshipMilestones(context.applicationContext)
            .sortedByDescending { affinityScore(context.applicationContext, it) }

    fun milestoneRewardFor(context: Context, type: EntityType): RelationshipMilestoneReward? {
        if (!isTracked(type) || !hasUnlockedMilestone(context.applicationContext, type)) return null
        return when (type) {
            EntityType.CAT -> RelationshipMilestoneReward(
                type = type,
                label = "Napping Patch",
                summary = "The cat has made a quiet patch of home for both of you.",
                traceLabel = "Napping Patch",
                costumeReward = CostumeStyle.FLOWER_CROWN,
                homePresenceLabel = "Shared Rest",
                homePresenceLine = "The cat has started leaving a shared quiet behind, like home already expected both of you.",
                bondRitualLabel = "Shared Pause",
                bondRitualLine = "When you slow down, the cat now leaves the pause open for both of you instead of slipping away from it.",
                milestoneBubbleText = "Shared rest",
                milestoneFlavorText = "The cat kept your pace",
                gardenReactionTitle = "Waiting Cat",
                gardenReactionLine = "The cat stays close enough to make the whole garden feel quieter, as if this pause belonged to both of you."
            )
            EntityType.FOX -> RelationshipMilestoneReward(
                type = type,
                label = "Trail Ribbon",
                summary = "The fox leaves a bright trail that now feels meant for you.",
                traceLabel = "Trail Ribbon",
                costumeReward = CostumeStyle.VINE_SCARF,
                homePresenceLabel = "Quick Path",
                homePresenceLine = "The fox has turned one line through the garden into a path that already knows your rhythm.",
                bondRitualLabel = "Answered Trick",
                bondRitualLine = "The fox now throws a line expecting your answer, not your stumble, like the trick belongs to both of you now.",
                milestoneBubbleText = "Trail kept",
                milestoneFlavorText = "The fox recognized the rhythm",
                gardenReactionTitle = "Known Trail",
                gardenReactionLine = "The fox lingers like it already knew which line through home would still feel like yours."
            )
            EntityType.WOLF -> RelationshipMilestoneReward(
                type = type,
                label = "Watch Stone",
                summary = "The wolf's silence now feels like a guard post instead of a warning.",
                traceLabel = "Watch Stone",
                costumeReward = CostumeStyle.MOON_CAPE,
                homePresenceLabel = "Kept Watch",
                homePresenceLine = "The wolf has left a steadier kind of watch behind, so the garden feels guarded instead of judged.",
                bondRitualLabel = "Lowered Guard",
                bondRitualLine = "When your calm holds, the wolf now lowers the warning instead of pressing it, like respect has become something practiced.",
                milestoneBubbleText = "Watch kept",
                milestoneFlavorText = "The wolf stayed with your calm",
                gardenReactionTitle = "Quiet Guard",
                gardenReactionLine = "The wolf holds the edge of home without baring its teeth, like the warning has finally turned into watchfulness."
            )
            EntityType.DOG -> RelationshipMilestoneReward(
                type = type,
                label = "Welcome Bell",
                summary = "The dog's joy has turned into something the whole garden keeps.",
                traceLabel = "Welcome Bell",
                costumeReward = CostumeStyle.BELL_CHARM,
                homePresenceLabel = "Open Gate",
                homePresenceLine = "The dog has made the whole entrance feel eager for you, like home learned how to rush forward first.",
                bondRitualLabel = "Meeting Run",
                bondRitualLine = "The dog now meets your return halfway, like joy has turned into something the two of you do together.",
                milestoneBubbleText = "Gate open",
                milestoneFlavorText = "The dog ran to meet it",
                gardenReactionTitle = "Glad Return",
                gardenReactionLine = "The dog waits like your return is the happiest part of the garden remembering itself."
            )
            EntityType.OWL -> RelationshipMilestoneReward(
                type = type,
                label = "Lantern Branch",
                summary = "The owl has made the dark edge of home feel watched over.",
                traceLabel = "Lantern Branch",
                costumeReward = CostumeStyle.LANTERN_PIN,
                homePresenceLabel = "Night Watch",
                homePresenceLine = "The owl has left a calmer kind of night behind, so the dark edge feels kept instead of merely quiet.",
                bondRitualLabel = "Known Shadow",
                bondRitualLine = "The owl now keeps a shadow your timing can belong to instead of only survive, like the dark edge has learned your shape.",
                milestoneBubbleText = "Night kept",
                milestoneFlavorText = "The owl stayed above it",
                gardenReactionTitle = "Lantern Owl",
                gardenReactionLine = "The owl waits on the dark edge like it already knows this return deserves a gentler kind of night."
            )
            EntityType.EAGLE -> RelationshipMilestoneReward(
                type = type,
                label = "Sky Thread",
                summary = "The eagle has left a stern but welcome line through the sky above home.",
                traceLabel = "Sky Thread",
                costumeReward = CostumeStyle.SKY_SASH,
                homePresenceLabel = "High Thread",
                homePresenceLine = "The eagle has taught the sky above home to feel held together instead of left too wide.",
                bondRitualLabel = "Held Line",
                bondRitualLine = "The eagle now marks a line for you to hold instead of only a place to fail, like discipline has turned into recognition.",
                milestoneBubbleText = "Sky held",
                milestoneFlavorText = "The eagle marked it kindly",
                gardenReactionTitle = "High Witness",
                gardenReactionLine = "The eagle circles high enough to leave room for you, but low enough to make the whole return feel recognized."
            )
            else -> null
        }
    }

    fun featuredMilestoneReward(context: Context): RelationshipMilestoneReward? {
        val appContext = context.applicationContext
        val preferred = strongestRelationship(appContext)?.first
            ?.takeIf { hasUnlockedMilestone(appContext, it) }
            ?: unlockedMilestoneTypes(appContext).firstOrNull()
            ?: return null
        return milestoneRewardFor(appContext, preferred)
    }

    fun encounterTuning(context: Context, type: EntityType): RelationshipEncounterTuning {
        if (!isTracked(type)) return RelationshipEncounterTuning()
        val appContext = context.applicationContext
        val stage = stageFor(appContext, type)
        val tone = toneFor(appContext, type)
        return when (type) {
            EntityType.CAT -> when (stage) {
                RelationshipStage.FIRST_IMPRESSION -> RelationshipEncounterTuning()
                RelationshipStage.RECOGNITION -> RelationshipEncounterTuning(passBonusPoints = 20)
                RelationshipStage.TRUST -> RelationshipEncounterTuning(
                    passBonusPoints = 40,
                    passBonusSeeds = if (tone == RelationshipTone.WARM) 1 else 0,
                    mercyPaddingBonusPx = if (tone == RelationshipTone.WARM) 2f else 0f
                )
                RelationshipStage.MILESTONE -> RelationshipEncounterTuning(
                    passBonusPoints = 70,
                    passBonusSeeds = if (tone == RelationshipTone.WARM) 1 else 0,
                    mercyPaddingBonusPx = if (tone == RelationshipTone.WARM) 4f else 1f
                )
            }
            EntityType.FOX -> when (stage) {
                RelationshipStage.FIRST_IMPRESSION -> RelationshipEncounterTuning()
                RelationshipStage.RECOGNITION -> RelationshipEncounterTuning(passBonusPoints = 20, detectionMultiplier = 1.06f)
                RelationshipStage.TRUST -> RelationshipEncounterTuning(
                    passBonusPoints = 40,
                    passBonusSeeds = if (tone == RelationshipTone.WARM) 1 else 0,
                    detectionMultiplier = if (tone == RelationshipTone.WARM) 1.16f else 1.10f,
                    mercyPaddingBonusPx = if (tone == RelationshipTone.WARM) 2f else 0f
                )
                RelationshipStage.MILESTONE -> RelationshipEncounterTuning(
                    passBonusPoints = 60,
                    passBonusSeeds = 1,
                    detectionMultiplier = if (tone == RelationshipTone.WARM) 1.24f else 1.14f,
                    mercyPaddingBonusPx = if (tone == RelationshipTone.WARM) 4f else 1f
                )
            }
            EntityType.WOLF -> when (stage) {
                RelationshipStage.FIRST_IMPRESSION -> RelationshipEncounterTuning()
                RelationshipStage.RECOGNITION -> RelationshipEncounterTuning(passBonusPoints = 25, telegraphMultiplier = 1.06f)
                RelationshipStage.TRUST -> RelationshipEncounterTuning(
                    passBonusPoints = 45,
                    passBonusSeeds = if (tone == RelationshipTone.WARM) 1 else 0,
                    mercyPaddingBonusPx = if (tone == RelationshipTone.WARM) 2f else 0f,
                    telegraphMultiplier = if (tone == RelationshipTone.WARM) 1.18f else 1.10f,
                    aggressionMultiplier = if (tone == RelationshipTone.CAUTIOUS) 1.03f else 0.95f
                )
                RelationshipStage.MILESTONE -> RelationshipEncounterTuning(
                    passBonusPoints = 70,
                    passBonusSeeds = 1,
                    mercyPaddingBonusPx = if (tone == RelationshipTone.WARM) 4f else 1f,
                    telegraphMultiplier = if (tone == RelationshipTone.WARM) 1.28f else 1.16f,
                    aggressionMultiplier = if (tone == RelationshipTone.CAUTIOUS) 1.02f else 0.88f
                )
            }
            EntityType.DOG -> when (stage) {
                RelationshipStage.FIRST_IMPRESSION -> RelationshipEncounterTuning()
                RelationshipStage.RECOGNITION -> RelationshipEncounterTuning(passBonusPoints = 20, buddyChanceBonus = 0.05f)
                RelationshipStage.TRUST -> RelationshipEncounterTuning(
                    passBonusPoints = 35,
                    passBonusSeeds = if (tone == RelationshipTone.WARM) 1 else 0,
                    mercyPaddingBonusPx = if (tone == RelationshipTone.WARM) 2f else 0f,
                    buddyChanceBonus = if (tone == RelationshipTone.WARM) 0.14f else 0.09f
                )
                RelationshipStage.MILESTONE -> RelationshipEncounterTuning(
                    passBonusPoints = 55,
                    passBonusSeeds = 1,
                    mercyPaddingBonusPx = if (tone == RelationshipTone.WARM) 4f else 1f,
                    buddyChanceBonus = if (tone == RelationshipTone.WARM) 0.24f else 0.14f
                )
            }
            EntityType.OWL -> when (stage) {
                RelationshipStage.FIRST_IMPRESSION -> RelationshipEncounterTuning()
                RelationshipStage.RECOGNITION -> RelationshipEncounterTuning(passBonusPoints = 20, telegraphMultiplier = 1.06f)
                RelationshipStage.TRUST -> RelationshipEncounterTuning(
                    passBonusPoints = 35,
                    passBonusSeeds = if (tone == RelationshipTone.WARM) 1 else 0,
                    mercyPaddingBonusPx = if (tone == RelationshipTone.WARM) 2f else 0f,
                    telegraphMultiplier = if (tone == RelationshipTone.WARM) 1.18f else 1.10f,
                    aggressionMultiplier = if (tone == RelationshipTone.CAUTIOUS) 1.02f else 0.95f
                )
                RelationshipStage.MILESTONE -> RelationshipEncounterTuning(
                    passBonusPoints = 55,
                    passBonusSeeds = 1,
                    mercyPaddingBonusPx = if (tone == RelationshipTone.WARM) 4f else 1f,
                    telegraphMultiplier = if (tone == RelationshipTone.WARM) 1.28f else 1.14f,
                    aggressionMultiplier = if (tone == RelationshipTone.CAUTIOUS) 1.02f else 0.88f
                )
            }
            EntityType.EAGLE -> when (stage) {
                RelationshipStage.FIRST_IMPRESSION -> RelationshipEncounterTuning()
                RelationshipStage.RECOGNITION -> RelationshipEncounterTuning(passBonusPoints = 20, telegraphMultiplier = 1.06f)
                RelationshipStage.TRUST -> RelationshipEncounterTuning(
                    passBonusPoints = 35,
                    passBonusSeeds = if (tone == RelationshipTone.WARM) 1 else 0,
                    mercyPaddingBonusPx = if (tone == RelationshipTone.WARM) 2f else 0f,
                    telegraphMultiplier = if (tone == RelationshipTone.WARM) 1.18f else 1.10f,
                    aggressionMultiplier = if (tone == RelationshipTone.CAUTIOUS) 1.02f else 0.95f
                )
                RelationshipStage.MILESTONE -> RelationshipEncounterTuning(
                    passBonusPoints = 60,
                    passBonusSeeds = 1,
                    mercyPaddingBonusPx = if (tone == RelationshipTone.WARM) 4f else 1f,
                    telegraphMultiplier = if (tone == RelationshipTone.WARM) 1.28f else 1.14f,
                    aggressionMultiplier = if (tone == RelationshipTone.CAUTIOUS) 1.02f else 0.88f
                )
            }
            else -> RelationshipEncounterTuning()
        }
    }

    fun dogBuddyChance(context: Context): Float =
        (0.20f + encounterTuning(context.applicationContext, EntityType.DOG).buddyChanceBonus).coerceIn(0.20f, 0.55f)

    fun creatureThought(context: Context, type: EntityType): String? {
        if (!isTracked(type)) return null
        val stage = stageFor(context, type)
        val tone = toneFor(context, type)
        val warmth = familiarityWarmth(context, type, stage, tone)
        val strain = strainedConsequence(context, type, stage, tone)
        return when (type) {
            EntityType.CAT -> when (stage) {
                RelationshipStage.FIRST_IMPRESSION -> "The cat keeps one eye on the path."
                RelationshipStage.RECOGNITION -> "The cat pretends not to expect you."
                RelationshipStage.TRUST -> when (tone) {
                    RelationshipTone.WARM -> when (warmth) {
                        FamiliarityWarmth.BONDED -> "The cat has started keeping your quiet ready before you arrive."
                        FamiliarityWarmth.PERSONAL -> "The cat has stopped leaving and started expecting your step."
                        else -> "The cat has stopped leaving when you arrive."
                    }
                    RelationshipTone.CAUTIOUS -> when (strain) {
                        StrainedConsequence.DISAPPOINTED ->
                            "The cat still waits, but like your rushed returns taught it not to expect gentleness to last."
                        else -> "The cat waits, but with the distance your rushed returns taught it."
                    }
                    RelationshipTone.NEUTRAL -> "The cat waits, but not too close."
                }
                RelationshipStage.MILESTONE -> when {
                    tone == RelationshipTone.CAUTIOUS ->
                        "The cat behaves like the place is shared, but your nerves still owe it patience."
                    warmth == FamiliarityWarmth.BONDED ->
                        "The cat behaves like your shared quiet already belongs to both of you."
                    else -> "The cat behaves like this was always your shared place."
                }
            }
            EntityType.FOX -> when (stage) {
                RelationshipStage.FIRST_IMPRESSION -> "A clever pause lingers near the path."
                RelationshipStage.RECOGNITION -> "The fox still treats every return like a challenge."
                RelationshipStage.TRUST -> when (tone) {
                    RelationshipTone.WARM -> when (warmth) {
                        FamiliarityWarmth.BONDED -> "The fox leaves room like it already knows the answer you will choose."
                        FamiliarityWarmth.PERSONAL -> "The fox leaves room for your answer now and expects you to take it."
                        else -> "The fox leaves room for your answer now."
                    }
                    RelationshipTone.CAUTIOUS -> when (strain) {
                        StrainedConsequence.TENSE ->
                            "The fox watches for the same flinch with a sharper patience than before."
                        else -> "The fox watches for the same flinch before it watches for your answer."
                    }
                    RelationshipTone.NEUTRAL -> "The fox watches to see if you still remember the rhythm."
                }
                RelationshipStage.MILESTONE -> when {
                    tone == RelationshipTone.CAUTIOUS ->
                        "The fox no longer looks surprised when you return, only careful."
                    warmth == FamiliarityWarmth.BONDED ->
                        "The fox moves like your shared rhythm is already part of the path."
                    else -> "The fox no longer looks surprised when you keep up."
                }
            }
            EntityType.WOLF -> when (stage) {
                RelationshipStage.FIRST_IMPRESSION -> "The grove still remembers the howl first."
                RelationshipStage.RECOGNITION -> "The wolf feels nearer, but less distant than before."
                RelationshipStage.TRUST -> when (tone) {
                    RelationshipTone.WARM -> when (warmth) {
                        FamiliarityWarmth.BONDED -> "The wolf's silence feels like shared ground instead of borrowed mercy."
                        FamiliarityWarmth.PERSONAL -> "The wolf's silence feels more knowingly earned now."
                        else -> "The wolf's silence feels earned."
                    }
                    RelationshipTone.CAUTIOUS -> when (strain) {
                        StrainedConsequence.FEARFUL ->
                            "The wolf keeps testing the place where your fear still answers faster than your calm."
                        else -> "The wolf keeps testing whether your calm will break where it always does."
                    }
                    RelationshipTone.NEUTRAL -> "The wolf keeps testing whether your calm will hold."
                }
                RelationshipStage.MILESTONE -> when {
                    tone == RelationshipTone.CAUTIOUS ->
                        "The grove remembers the respect, but not without remembering the fear too."
                    warmth == FamiliarityWarmth.BONDED ->
                        "The grove rests like the wolf already expects your calm to hold."
                    else -> "The grove rests easier when the wolf chooses not to bare its teeth."
                }
            }
            EntityType.DOG -> when (stage) {
                RelationshipStage.FIRST_IMPRESSION -> "The garden still echoes with a bark."
                RelationshipStage.RECOGNITION -> "The dog seems to think every return is an invitation."
                RelationshipStage.TRUST -> when (tone) {
                    RelationshipTone.WARM -> when (warmth) {
                        FamiliarityWarmth.BONDED -> "The dog acts like your return was kept safe the whole time you were gone."
                        FamiliarityWarmth.PERSONAL -> "The dog acts like you were only gone for a minute and already expected back."
                        else -> "The dog acts like you were only gone for a minute."
                    }
                    RelationshipTone.CAUTIOUS -> when (strain) {
                        StrainedConsequence.DISAPPOINTED ->
                            "The dog is still glad, but the joy now carries the hurt of being let down in the same place."
                        else -> "The dog is still glad, but it braces for your nerves before you do."
                    }
                    RelationshipTone.NEUTRAL -> "The dog is ready to forgive your nerves faster than you are."
                }
                RelationshipStage.MILESTONE -> when {
                    tone == RelationshipTone.CAUTIOUS ->
                        "The dog still thinks you belong here, and still worries you might forget it mid-run."
                    warmth == FamiliarityWarmth.BONDED ->
                        "The dog has fully decided home works best when it can already hear you coming back."
                    else -> "The dog has fully decided you belong here."
                }
            }
            EntityType.OWL -> when (stage) {
                RelationshipStage.FIRST_IMPRESSION -> "The dark edge keeps a patient shape."
                RelationshipStage.RECOGNITION -> "The owl no longer startles the garden quite as much."
                RelationshipStage.TRUST -> when (tone) {
                    RelationshipTone.WARM -> when (warmth) {
                        FamiliarityWarmth.BONDED -> "The owl watches like the night already knows your outline by heart."
                        FamiliarityWarmth.PERSONAL -> "The owl watches like a witness that has started expecting your timing."
                        else -> "The owl watches like a witness, not a warning."
                    }
                    RelationshipTone.CAUTIOUS -> when (strain) {
                        StrainedConsequence.TENSE ->
                            "The owl still asks the night to remember the same jump, but with a more watchful edge."
                        else -> "The owl still asks the night to remember the same jump."
                    }
                    RelationshipTone.NEUTRAL -> "The owl still asks the night to judge your timing."
                }
                RelationshipStage.MILESTONE -> when {
                    tone == RelationshipTone.CAUTIOUS ->
                        "The night has made room for the owl, but it still keeps a careful eye on you."
                    warmth == FamiliarityWarmth.BONDED ->
                        "The night has made room for the owl and your familiar shape together."
                    else -> "The night has made room for the owl and still feels welcoming."
                }
            }
            EntityType.EAGLE -> when (stage) {
                RelationshipStage.FIRST_IMPRESSION -> "The sky feels too large to fully trust."
                RelationshipStage.RECOGNITION -> "Even at rest, the shadow crosses your thoughts."
                RelationshipStage.TRUST -> when (tone) {
                    RelationshipTone.WARM -> when (warmth) {
                        FamiliarityWarmth.BONDED -> "The sky feels steadier when the eagle holds a line that already includes you."
                        FamiliarityWarmth.PERSONAL -> "The sky feels less hostile when the eagle chooses a distance meant for you."
                        else -> "The sky feels less hostile when the eagle chooses distance."
                    }
                    RelationshipTone.CAUTIOUS -> when (strain) {
                        StrainedConsequence.FEARFUL ->
                            "The eagle still reminds you exactly where fear keeps taking the lead from you."
                        else -> "The eagle still reminds you exactly where fear keeps answering first."
                    }
                    RelationshipTone.NEUTRAL -> "The eagle still reminds you how small a mistake can look from above."
                }
                RelationshipStage.MILESTONE -> when {
                    tone == RelationshipTone.CAUTIOUS ->
                        "The eagle's shadow feels like recognition, but not yet forgiveness."
                    warmth == FamiliarityWarmth.BONDED ->
                        "The eagle's shadow feels like a held line above you, not just recognition."
                    else -> "The eagle's shadow feels more like recognition than threat now."
                }
            }
            else -> null
        }
    }

    fun repeatFriendLine(context: Context, type: EntityType): String {
        val stage = stageFor(context.applicationContext, type)
        val tone = toneFor(context.applicationContext, type)
        val warmth = familiarityWarmth(context.applicationContext, type, stage, tone)
        return when (type) {
            EntityType.CAT -> when (stage) {
                RelationshipStage.TRUST -> when {
                    warmth == FamiliarityWarmth.BONDED ->
                        "The cat has started treating your return like the quiet was kept for you."
                    tone == RelationshipTone.WARM ->
                        "The cat has started treating your returns like part of its routine."
                    else -> "The cat no longer acts like your return is a surprise."
                }
                RelationshipStage.MILESTONE -> if (warmth == FamiliarityWarmth.BONDED) {
                    "The cat moves like the two of you already agreed this quiet belongs to both of you."
                } else {
                    "The cat moves like the two of you already agreed this is shared ground."
                }
                else -> "The cat has started meeting your softer timing halfway."
            }
            EntityType.FOX -> when (stage) {
                RelationshipStage.TRUST -> when {
                    warmth == FamiliarityWarmth.BONDED ->
                        "The fox has stopped pretending your rhythm is only a challenge and started treating it like a shared trick."
                    tone == RelationshipTone.WARM ->
                        "The fox has stopped pretending your rhythm is only a challenge."
                    else -> "The fox is beginning to expect your answer instead of only testing it."
                }
                RelationshipStage.MILESTONE -> if (warmth == FamiliarityWarmth.BONDED) {
                    "The fox now treats your returns like a game both of you have already claimed."
                } else {
                    "The fox now treats your returns like a game both of you already know."
                }
                else -> "The fox keeps leaving room for your answer now."
            }
            EntityType.WOLF -> when (stage) {
                RelationshipStage.TRUST -> when {
                    warmth == FamiliarityWarmth.BONDED ->
                        "The wolf's respect has started to feel less borrowed and more deliberately shared."
                    tone == RelationshipTone.WARM ->
                        "The wolf's respect has started to feel less borrowed and more shared."
                    else -> "The wolf no longer sounds surprised when your calm holds."
                }
                RelationshipStage.MILESTONE -> if (warmth == FamiliarityWarmth.BONDED) {
                    "The grove carries the wolf's respect like something the two of you keep proving together."
                } else {
                    "The grove carries the wolf's respect like something you earned together."
                }
                else -> "The wolf has started recognizing your steadier courage."
            }
            EntityType.DOG -> when (stage) {
                RelationshipStage.TRUST -> when {
                    warmth == FamiliarityWarmth.BONDED ->
                        "The dog greets you like this joy has already become a promise it gets to keep."
                    tone == RelationshipTone.WARM ->
                        "The dog greets you like this joy has already become a habit."
                    else -> "The dog keeps acting like your return was always going to happen."
                }
                RelationshipStage.MILESTONE -> if (warmth == FamiliarityWarmth.BONDED) {
                    "The dog's excitement now feels less like a surprise and more like home answering you back."
                } else {
                    "The dog's excitement now feels less like a surprise and more like belonging."
                }
                else -> "The dog has started keeping your return in mind."
            }
            EntityType.OWL -> when (stage) {
                RelationshipStage.TRUST -> when {
                    warmth == FamiliarityWarmth.BONDED ->
                        "The owl has started watching like the night already kept your outline in mind."
                    tone == RelationshipTone.WARM ->
                        "The owl has started watching like a witness that already knows you."
                    else -> "The owl keeps the night open for you a little longer now."
                }
                RelationshipStage.MILESTONE -> if (warmth == FamiliarityWarmth.BONDED) {
                    "The owl has made the dark edge feel like a place your familiar shape already belongs."
                } else {
                    "The owl has made the dark edge feel like a place you both remember."
                }
                else -> "The owl has started letting familiarity into the night."
            }
            EntityType.EAGLE -> when (stage) {
                RelationshipStage.TRUST -> when {
                    warmth == FamiliarityWarmth.BONDED ->
                        "The eagle has started leaving a steadier sky between you and fear, like it already recognizes your line."
                    tone == RelationshipTone.WARM ->
                        "The eagle has started leaving more sky between you and fear."
                    else -> "The eagle no longer turns every return into a warning."
                }
                RelationshipStage.MILESTONE -> if (warmth == FamiliarityWarmth.BONDED) {
                    "Even the eagle's shadow now feels like a line held for you, not just recognition."
                } else {
                    "Even the eagle's shadow now feels more like recognition than judgment."
                }
                else -> "The eagle has started recognizing your return before your fear does."
            }
            else -> lineFor(context, type, Event.RETURN)
        }
    }

    fun strainedBondLine(context: Context, type: EntityType): String {
        val appContext = context.applicationContext
        val stage = stageFor(appContext, type)
        val tone = toneFor(appContext, type)
        val consequence = strainedConsequence(appContext, type, stage, tone)
        return when (type) {
            EntityType.CAT -> when (stage) {
                RelationshipStage.MILESTONE -> when (consequence) {
                    StrainedConsequence.DISAPPOINTED ->
                        "The cat still keeps your place, but like something quietly disappointed in how often you startle the shared quiet."
                    else -> "The cat still keeps your place, but not without remembering how abruptly you keep reaching for it."
                }
                RelationshipStage.TRUST -> when (consequence) {
                    StrainedConsequence.DISAPPOINTED ->
                        "The cat stays nearby, but with the withdrawn distance of something you have let down more than once."
                    else -> "The cat stays nearby, but with the kind of distance you taught it."
                }
                else -> "The cat has started expecting your nerves before your kindness."
            }
            EntityType.FOX -> when (stage) {
                RelationshipStage.MILESTONE -> when (consequence) {
                    StrainedConsequence.TENSE ->
                        "The fox still answers you, but with a taut patience that has started expecting the same break in rhythm."
                    else -> "The fox still answers you, but with the sharper kind of patience that follows repeated flinching."
                }
                RelationshipStage.TRUST -> when (consequence) {
                    StrainedConsequence.TENSE ->
                        "The fox has started treating your hesitation like a tension it can already hear arriving."
                    else -> "The fox has started watching for the same hesitation before it watches for you."
                }
                else -> "The fox keeps testing the place where your timing keeps giving way."
            }
            EntityType.WOLF -> when (stage) {
                RelationshipStage.MILESTONE -> when (consequence) {
                    StrainedConsequence.FEARFUL ->
                        "The wolf still knows you, but now it measures how quickly fear returns to the same old place in you."
                    else -> "The wolf still knows you, but now it measures whether your calm will fail in the same old place."
                }
                RelationshipStage.TRUST -> when (consequence) {
                    StrainedConsequence.FEARFUL ->
                        "The wolf's respect has gone hard-edged, like it can already smell the fear before you do."
                    else -> "The wolf's respect has gone careful, like it remembers exactly where you keep breaking."
                }
                else -> "The wolf has started treating your fear like something it already recognizes."
            }
            EntityType.DOG -> when (stage) {
                RelationshipStage.MILESTONE -> when (consequence) {
                    StrainedConsequence.DISAPPOINTED ->
                        "The dog still comes close, but the joy now carries the soft hurt of being let down in the same place."
                    else -> "The dog still comes close, but even that joy has started bracing for the same hurt."
                }
                RelationshipStage.TRUST -> when (consequence) {
                    StrainedConsequence.DISAPPOINTED ->
                        "The dog forgives quickly, but not quickly enough to hide that the same missed bark-line keeps hurting it."
                    else -> "The dog forgives quickly, but not quickly enough to forget the bark-line you keep missing."
                }
                else -> "The dog's excitement has started carrying a little caution in it."
            }
            EntityType.OWL -> when (stage) {
                RelationshipStage.MILESTONE -> when (consequence) {
                    StrainedConsequence.TENSE ->
                        "The owl still keeps the dark edge for you, but the night has started tightening around the same mistake."
                    else -> "The owl still keeps the dark edge for you, but the night has started sounding more watchful than welcoming."
                }
                RelationshipStage.TRUST -> when (consequence) {
                    StrainedConsequence.TENSE ->
                        "The owl has started meeting you with a tense watchfulness instead of simple recognition."
                    else -> "The owl has started meeting you with caution instead of only recognition."
                }
                else -> "The owl has started expecting the same mistake before it expects your return."
            }
            EntityType.EAGLE -> when (stage) {
                RelationshipStage.MILESTONE -> when (consequence) {
                    StrainedConsequence.FEARFUL ->
                        "The eagle still recognizes you, but the sky now holds that recognition around the same returning fear."
                    else -> "The eagle still recognizes you, but the sky now holds that recognition like a warning."
                }
                RelationshipStage.TRUST -> when (consequence) {
                    StrainedConsequence.FEARFUL ->
                        "The eagle's shadow has started feeling like fear arriving early enough to judge you for it."
                    else -> "The eagle's shadow has started feeling like a test you keep failing in the same place."
                }
                else -> "The eagle has started expecting your fear before your steadiness."
            }
            else -> lineFor(appContext, type, Event.RETURN)
        }
    }

    fun encounterCueLine(context: Context, type: EntityType, cue: EncounterCue): String {
        val stage = stageFor(context, type)
        val tone = toneFor(context, type)
        return when (type) {
            EntityType.CAT -> catCueLine(context, stage, tone, cue)
            EntityType.FOX -> foxCueLine(context, stage, tone, cue)
            EntityType.WOLF -> wolfCueLine(context, stage, tone, cue)
            EntityType.OWL -> owlCueLine(context, stage, tone, cue)
            EntityType.EAGLE -> eagleCueLine(context, stage, tone, cue)
            EntityType.DOG -> dogCueLine(context, stage, tone, cue)
            else -> lineFor(context, type, Event.THREAT)
        }
    }

    fun dogBuddyDialogue(context: Context): List<String> = listOf(
        encounterCueLine(context, EntityType.DOG, EncounterCue.DOG_GREETING),
        encounterCueLine(context, EntityType.DOG, EncounterCue.DOG_MIDDLE),
        lineFor(context, EntityType.DOG, Event.PASS),
        encounterCueLine(context, EntityType.DOG, EncounterCue.DOG_FAREWELL)
    )

    fun dogBuddyDurationBonusSec(context: Context): Float {
        val appContext = context.applicationContext
        return when (stageFor(appContext, EntityType.DOG)) {
            RelationshipStage.FIRST_IMPRESSION -> 0f
            RelationshipStage.RECOGNITION -> 0.25f
            RelationshipStage.TRUST -> if (toneFor(appContext, EntityType.DOG) == RelationshipTone.WARM) 0.8f else 0.45f
            RelationshipStage.MILESTONE -> if (toneFor(appContext, EntityType.DOG) == RelationshipTone.WARM) 1.25f else 0.8f
        }
    }

    fun lineFor(context: Context, type: EntityType, event: Event): String {
        val stage = stageFor(context, type)
        val tone = toneFor(context, type)
        return when (type) {
            EntityType.CAT -> catLine(context, stage, tone, event)
            EntityType.FOX -> foxLine(context, stage, tone, event)
            EntityType.WOLF -> wolfLine(context, stage, tone, event)
            EntityType.DOG -> dogLine(context, stage, tone, event)
            EntityType.OWL -> owlLine(context, stage, tone, event)
            EntityType.EAGLE -> eagleLine(context, stage, tone, event)
            else -> ""
        }
    }

    private fun computeStage(
        type: EntityType,
        encounters: Int,
        cleanPasses: Int,
        spared: Int,
        hits: Int
    ): RelationshipStage {
        val config = thresholds.getValue(type)
        if (encounters < config.recognitionScore) {
            return RelationshipStage.FIRST_IMPRESSION
        }

        // Familiarity alone is capped at Recognition. Trust and Bond must be
        // earned through positive outcomes; repeated hits delay progression.
        // A deliberate spare is rarer and more meaningful than a clean pass.
        val familiarity = minOf(encounters, config.recognitionScore)
        val positiveOutcomes = cleanPasses.coerceAtLeast(0) + spared.coerceAtLeast(0)
        val earnedScore = familiarity +
            cleanPasses.coerceAtLeast(0) +
            spared.coerceAtLeast(0) * 4 -
            hits.coerceAtLeast(0)

        return when {
            positiveOutcomes >= 3 && earnedScore >= config.milestoneScore -> RelationshipStage.MILESTONE
            positiveOutcomes > 0 && earnedScore >= config.trustScore -> RelationshipStage.TRUST
            else -> RelationshipStage.RECOGNITION
        }
    }

    private fun affinityScore(context: Context, type: EntityType): Int {
        val appContext = context.applicationContext
        val config = thresholds.getValue(type)
        // Familiarity can distinguish two already-earned bonds, but is bounded
        // and can never advance the relationship stage by itself.
        val familiarity = minOf(
            SaveManager.loadEncounterCount(appContext, type),
            config.recognitionScore + 3
        )
        val cleanPasses = SaveManager.loadCleanPassCount(appContext, type)
        val spared = SaveManager.loadSparedCount(appContext, type)
        val hits = SaveManager.loadHitCount(appContext, type)
        return familiarity + cleanPasses * 2 + spared * 4 - hits * 2
    }

    private fun unlockMilestone(context: Context, type: EntityType) {
        val unlocked = SaveManager.loadUnlockedRelationshipMilestones(context).toMutableSet()
        if (unlocked.add(type)) {
            SaveManager.saveUnlockedRelationshipMilestones(context, unlocked)
        }
    }

    private fun toneFor(context: Context, type: EntityType): RelationshipTone {
        val appContext = context.applicationContext
        val spared = SaveManager.loadSparedCount(appContext, type)
        val hits = SaveManager.loadHitCount(appContext, type)
        val kindnessStreak = SaveManager.loadKindnessStreak(appContext, type)
        val tenderStreak = SaveManager.loadTenderStreak(appContext, type)
        return when {
            kindnessStreak >= 2 || (spared > hits && spared >= 1) -> RelationshipTone.WARM
            tenderStreak >= 2 || (hits > spared && hits >= 2) -> RelationshipTone.CAUTIOUS
            else -> RelationshipTone.NEUTRAL
        }
    }

    private fun familiarityWarmth(
        context: Context,
        type: EntityType,
        stage: RelationshipStage,
        tone: RelationshipTone
    ): FamiliarityWarmth {
        if (!isTracked(type) || tone != RelationshipTone.WARM || stage == RelationshipStage.FIRST_IMPRESSION) {
            return FamiliarityWarmth.NONE
        }
        val appContext = context.applicationContext
        val passCount = PersistentMemoryManager.getPassCount(appContext, type)
        val sparedCount = PersistentMemoryManager.getSparedCount(appContext, type)
        val kindnessStreak = SaveManager.loadKindnessStreak(appContext, type)
        val encounters = SaveManager.loadEncounterCount(appContext, type)
        val score = FamiliarityWarmthScoring.score(
            stage = stage,
            passCount = passCount,
            sparedCount = sparedCount,
            kindnessStreak = kindnessStreak,
            encounters = encounters
        )
        return when (FamiliarityWarmthScoring.tierOrdinal(score)) {
            3 -> FamiliarityWarmth.BONDED
            2 -> FamiliarityWarmth.PERSONAL
            else -> FamiliarityWarmth.GENTLE
        }
    }

    private fun strainedConsequence(
        context: Context,
        type: EntityType,
        stage: RelationshipStage,
        tone: RelationshipTone
    ): StrainedConsequence? {
        if (!isTracked(type) || tone != RelationshipTone.CAUTIOUS || stage == RelationshipStage.FIRST_IMPRESSION) {
            return null
        }
        val appContext = context.applicationContext
        val hits = SaveManager.loadHitCount(appContext, type)
        val tenderStreak = SaveManager.loadTenderStreak(appContext, type)
        val severity = hits + tenderStreak + if (stage == RelationshipStage.MILESTONE) 1 else 0
        return when (type) {
            EntityType.CAT, EntityType.DOG ->
                if (severity >= 5) StrainedConsequence.DISAPPOINTED else StrainedConsequence.WARY
            EntityType.FOX, EntityType.OWL ->
                if (severity >= 5) StrainedConsequence.TENSE else StrainedConsequence.WARY
            EntityType.WOLF, EntityType.EAGLE ->
                if (severity >= 5) StrainedConsequence.FEARFUL else StrainedConsequence.TENSE
            else -> StrainedConsequence.WARY
        }
    }

    private fun formatName(type: EntityType): String =
        type.name.lowercase().split("_").joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

    private fun catLine(
        context: Context,
        stage: RelationshipStage,
        tone: RelationshipTone,
        event: Event
    ): String {
        val passCount = PersistentMemoryManager.getPassCount(context, EntityType.CAT)
        val warmth = familiarityWarmth(context, EntityType.CAT, stage, tone)
        val strain = strainedConsequence(context, EntityType.CAT, stage, tone)
        return when (event) {
        Event.PASS -> when (stage) {
            RelationshipStage.FIRST_IMPRESSION -> "Meow?"
            RelationshipStage.RECOGNITION ->
                when {
                    warmth >= FamiliarityWarmth.PERSONAL -> "You found my quiet again."
                    tone == RelationshipTone.WARM && passCount >= 3 -> "You kept the same pace."
                    tone == RelationshipTone.WARM -> "You again."
                    else -> "Soft steps."
                }
            RelationshipStage.TRUST -> when {
                warmth == FamiliarityWarmth.BONDED -> "You kept our quiet together."
                warmth == FamiliarityWarmth.PERSONAL -> "I saved the quiet for you."
                tone == RelationshipTone.WARM && passCount >= 4 -> "You kept our quiet."
                else -> "Stayed a while?"
            }
            RelationshipStage.MILESTONE ->
                when {
                    warmth == FamiliarityWarmth.BONDED -> "You came back to our quiet."
                    passCount >= 4 -> "You kept the shared quiet."
                    else -> "Home?"
                }
        }
        Event.SPARE -> when (stage) {
            RelationshipStage.MILESTONE -> when {
                warmth == FamiliarityWarmth.BONDED -> "I'll keep our quiet warm."
                passCount >= 4 -> "I'll keep your place warm."
                else -> "See you soon."
            }
            RelationshipStage.TRUST -> when {
                warmth >= FamiliarityWarmth.PERSONAL -> "I'll keep your place by the quiet."
                passCount >= 4 -> "I'll stay with the quiet."
                else -> "I'll stay."
            }
            else -> "Friend?"
        }
        Event.THREAT -> when {
            strain == StrainedConsequence.DISAPPOINTED -> "Too sudden. You know that hurts the quiet."
            tone == RelationshipTone.CAUTIOUS -> "Too sudden again."
            else -> "Hiss!"
        }
        Event.RETURN -> when (stage) {
            RelationshipStage.TRUST, RelationshipStage.MILESTONE ->
                when {
                    warmth == FamiliarityWarmth.BONDED -> "A cat already kept a little shared quiet here for you."
                    passCount >= 4 -> "A cat already left room for you here."
                    else -> "A cat has already claimed this place."
                }
            else -> "A cat watches from the path."
        }
    }
    }

    private fun foxLine(
        context: Context,
        stage: RelationshipStage,
        tone: RelationshipTone,
        event: Event
    ): String {
        val passCount = PersistentMemoryManager.getPassCount(context, EntityType.FOX)
        val warmth = familiarityWarmth(context, EntityType.FOX, stage, tone)
        val strain = strainedConsequence(context, EntityType.FOX, stage, tone)
        return when (event) {
        Event.PASS -> when (stage) {
            RelationshipStage.FIRST_IMPRESSION -> "Heh."
            RelationshipStage.RECOGNITION ->
                if (warmth >= FamiliarityWarmth.PERSONAL) "Still reading me that easily?" else if (passCount >= 3) "Still reading me?" else "Same jump?"
            RelationshipStage.TRUST -> when {
                warmth == FamiliarityWarmth.BONDED -> "You remembered our trick."
                warmth == FamiliarityWarmth.PERSONAL -> "You remembered the trick for me."
                tone == RelationshipTone.WARM && passCount >= 4 -> "You remembered the trick."
                tone == RelationshipTone.WARM -> "Still with me?"
                else -> "Caught that."
            }
            RelationshipStage.MILESTONE ->
                when {
                    warmth == FamiliarityWarmth.BONDED -> "Knew our line would hold."
                    passCount >= 4 -> "Knew you'd remember."
                    else -> "Knew you would."
                }
        }
        Event.SPARE -> when (stage) {
            RelationshipStage.MILESTONE -> when {
                warmth == FamiliarityWarmth.BONDED -> "Till the next trick we already share."
                passCount >= 4 -> "Till the next little trick."
                else -> "Till next time."
            }
            RelationshipStage.TRUST -> when {
                warmth >= FamiliarityWarmth.PERSONAL -> "Fine. You know my rhythm now."
                passCount >= 4 -> "Fine. You know it now."
                else -> "Fine. Go on."
            }
            else -> "Fine."
        }
        Event.THREAT -> when {
            strain == StrainedConsequence.TENSE -> "There it is. The same tension."
            tone == RelationshipTone.CAUTIOUS -> "Same hesitation."
            passCount >= 3 -> "You know this trick."
            else -> "Next time..."
        }
        Event.RETURN -> when (stage) {
            RelationshipStage.TRUST, RelationshipStage.MILESTONE ->
                when {
                    warmth == FamiliarityWarmth.BONDED -> "A fox lingers like the next shared trick was already waiting for you."
                    passCount >= 4 -> "A fox lingers like the next trick is already shared."
                    else -> "A fox lingers like it expected you."
                }
            else -> "Something clever moved through the garden."
        }
    }
    }

    private fun wolfLine(
        context: Context,
        stage: RelationshipStage,
        tone: RelationshipTone,
        event: Event
    ): String {
        val sparedCount = PersistentMemoryManager.getSparedCount(context, EntityType.WOLF)
        val passCount = PersistentMemoryManager.getPassCount(context, EntityType.WOLF)
        val hitCount = PersistentMemoryManager.getHitCount(context, EntityType.WOLF)
        val warmth = familiarityWarmth(context, EntityType.WOLF, stage, tone)
        val strain = strainedConsequence(context, EntityType.WOLF, stage, tone)
        return when (event) {
        Event.PASS -> when (stage) {
            RelationshipStage.FIRST_IMPRESSION -> "You made it."
            RelationshipStage.RECOGNITION ->
                if (warmth >= FamiliarityWarmth.PERSONAL) "Still standing with me." else if (passCount >= 3) "Still standing." else "Again."
            RelationshipStage.TRUST -> when {
                warmth == FamiliarityWarmth.BONDED -> "You kept the peace between us."
                warmth == FamiliarityWarmth.PERSONAL -> "You held the line I left for you."
                tone == RelationshipTone.WARM && sparedCount >= 2 -> "You kept the wolf from baring its teeth."
                tone == RelationshipTone.WARM -> "You held steady."
                else -> "Still standing."
            }
            RelationshipStage.MILESTONE -> when {
                warmth == FamiliarityWarmth.BONDED -> "You know how to leave our warning empty."
                sparedCount >= 3 -> "You know how to leave the howl empty."
                passCount >= 4 -> "You know the howl now."
                else -> "You know the howl now."
            }
        }
        Event.SPARE -> when (stage) {
            RelationshipStage.MILESTONE -> when {
                warmth == FamiliarityWarmth.BONDED -> "Then let our warning rest. Pass in peace."
                sparedCount >= 3 -> "Then let the warning rest. Pass in peace."
                tone == RelationshipTone.WARM -> "Then pass in peace."
                else -> "Pass in peace."
            }
            RelationshipStage.TRUST -> when {
                warmth >= FamiliarityWarmth.PERSONAL -> "Keep that calm. I know your ground now."
                sparedCount >= 2 -> "Keep that calm. I'll stand down."
                tone == RelationshipTone.WARM -> "Go on. Keep your ground."
                else -> "Go on."
            }
            else -> if (sparedCount >= 1) "Not today. Keep moving." else "Not today."
        }
        Event.THREAT -> when {
            strain == StrainedConsequence.FEARFUL -> "I know where fear takes you."
            tone == RelationshipTone.CAUTIOUS && hitCount >= 3 -> "I remember exactly where you break."
            tone == RelationshipTone.CAUTIOUS -> "I remember where you break."
            sparedCount >= 2 -> "Hold that calm."
            else -> "GRRR..."
        }
        Event.RETURN -> when (stage) {
            RelationshipStage.TRUST, RelationshipStage.MILESTONE -> when {
                warmth == FamiliarityWarmth.BONDED -> "The grove keeps the wolf's respect like shared watchfulness, not threat."
                sparedCount >= 3 -> "The grove keeps the wolf's respect like a watch that no longer needs teeth."
                sparedCount >= 1 -> "The grove feels watched, not threatened."
                else -> "A distant howl still belongs to the path."
            }
            else -> "A distant howl still belongs to the path."
        }
    }
    }

    private fun dogLine(context: Context, stage: RelationshipStage, tone: RelationshipTone, event: Event): String {
        val warmth = familiarityWarmth(context, EntityType.DOG, stage, tone)
        val strain = strainedConsequence(context, EntityType.DOG, stage, tone)
        return when (event) {
        Event.PASS -> when (stage) {
            RelationshipStage.FIRST_IMPRESSION -> "Good hop!"
            RelationshipStage.RECOGNITION -> "Hi!!"
            RelationshipStage.TRUST -> when {
                warmth == FamiliarityWarmth.BONDED -> "Still here with me!"
                warmth == FamiliarityWarmth.PERSONAL -> "Knew you'd be back!"
                tone == RelationshipTone.WARM -> "Still here!"
                else -> "Nice one!"
            }
            RelationshipStage.MILESTONE -> when {
                warmth == FamiliarityWarmth.BONDED -> "Best friend. Back already!"
                else -> "Best friend!"
            }
        }
        Event.SPARE -> when (stage) {
            RelationshipStage.MILESTONE -> when {
                warmth == FamiliarityWarmth.BONDED -> "Best friend. I kept home ready!"
                tone == RelationshipTone.WARM -> "Best friend. Race you home!"
                else -> "Best friend!"
            }
            RelationshipStage.TRUST -> when {
                warmth >= FamiliarityWarmth.PERSONAL -> "Come on. I saved your place!"
                tone == RelationshipTone.WARM -> "Come on. Home's this way!"
                else -> "Best friend!"
            }
            RelationshipStage.RECOGNITION -> "See you home!"
            else -> "Best friend!"
        }
        Event.THREAT -> when {
            strain == StrainedConsequence.DISAPPOINTED -> "Easy. That same line still hurts."
            tone == RelationshipTone.CAUTIOUS -> "Easy. Not that line again."
            else -> "Hi!!"
        }
        Event.RETURN -> when (stage) {
            RelationshipStage.TRUST, RelationshipStage.MILESTONE -> if (warmth == FamiliarityWarmth.BONDED) {
                "The garden still feels like a welcome the dog already kept warm for you."
            } else {
                "The garden still feels wagged awake."
            }
            else -> "A bark seems closer than before."
        }
    }
    }

    private fun catCueLine(
        context: Context,
        stage: RelationshipStage,
        tone: RelationshipTone,
        cue: EncounterCue
    ): String {
        val passCount = PersistentMemoryManager.getPassCount(context, EntityType.CAT)
        val warmth = familiarityWarmth(context, EntityType.CAT, stage, tone)
        val strain = strainedConsequence(context, EntityType.CAT, stage, tone)
        return when (cue) {
        EncounterCue.MERCY -> when {
            warmth == FamiliarityWarmth.BONDED -> "Easy. I kept our quiet for you."
            warmth == FamiliarityWarmth.PERSONAL -> "Easy. I know your step now."
            stage == RelationshipStage.MILESTONE && tone == RelationshipTone.WARM && passCount >= 4 -> "Easy. It's still us."
            stage.ordinal >= RelationshipStage.TRUST.ordinal && tone == RelationshipTone.WARM && passCount >= 3 -> "Easy. I know your pace."
            stage == RelationshipStage.MILESTONE && tone == RelationshipTone.WARM -> "Easy. I know you."
            stage.ordinal >= RelationshipStage.TRUST.ordinal && tone == RelationshipTone.WARM -> "Close, friend."
            strain == StrainedConsequence.DISAPPOINTED -> "Too sudden again? That still stings."
            tone == RelationshipTone.CAUTIOUS -> "Too sudden again?"
            stage.ordinal >= RelationshipStage.RECOGNITION.ordinal -> "Softly."
            else -> "Close one."
        }
        else -> catLine(context, stage, tone, Event.THREAT)
        }
    }

    private fun foxCueLine(
        context: Context,
        stage: RelationshipStage,
        tone: RelationshipTone,
        cue: EncounterCue
    ): String {
        val passCount = PersistentMemoryManager.getPassCount(context, EntityType.FOX)
        val warmth = familiarityWarmth(context, EntityType.FOX, stage, tone)
        val strain = strainedConsequence(context, EntityType.FOX, stage, tone)
        return when (cue) {
        EncounterCue.FOX_LANDING -> when {
            warmth == FamiliarityWarmth.BONDED -> "Knew you'd catch our line."
            warmth == FamiliarityWarmth.PERSONAL -> "You remembered the line I left you."
            stage == RelationshipStage.MILESTONE && tone == RelationshipTone.WARM && passCount >= 4 -> "Knew you'd read it."
            stage.ordinal >= RelationshipStage.TRUST.ordinal && tone == RelationshipTone.WARM && passCount >= 3 -> "You remembered the line."
            stage == RelationshipStage.MILESTONE && tone == RelationshipTone.WARM -> "Knew you'd stay with me."
            stage.ordinal >= RelationshipStage.TRUST.ordinal && tone == RelationshipTone.WARM -> "You remembered."
            strain == StrainedConsequence.TENSE -> "Still letting the same step tighten you up?"
            tone == RelationshipTone.CAUTIOUS -> "Still flinching at the same step?"
            stage.ordinal >= RelationshipStage.RECOGNITION.ordinal -> "Caught that."
            else -> "Next time..."
        }
        else -> foxLine(context, stage, tone, Event.THREAT)
        }
    }

    private fun wolfCueLine(
        context: Context,
        stage: RelationshipStage,
        tone: RelationshipTone,
        cue: EncounterCue
    ): String {
        val sparedCount = PersistentMemoryManager.getSparedCount(context, EntityType.WOLF)
        val hitCount = PersistentMemoryManager.getHitCount(context, EntityType.WOLF)
        val warmth = familiarityWarmth(context, EntityType.WOLF, stage, tone)
        val strain = strainedConsequence(context, EntityType.WOLF, stage, tone)
        return when (cue) {
        EncounterCue.WOLF_CHARGE -> when {
            warmth == FamiliarityWarmth.BONDED -> "Hold steady. I know your ground now."
            warmth == FamiliarityWarmth.PERSONAL -> "Stand your ground. I left you room."
            stage == RelationshipStage.MILESTONE && tone == RelationshipTone.WARM && sparedCount >= 3 -> "Hold steady. You know how this ends."
            stage.ordinal >= RelationshipStage.TRUST.ordinal && tone == RelationshipTone.WARM && sparedCount >= 2 -> "Stand your ground. I'll know if you keep it."
            stage.ordinal >= RelationshipStage.TRUST.ordinal && tone == RelationshipTone.WARM -> "Stand your ground."
            strain == StrainedConsequence.FEARFUL -> "I know where fear breaks you."
            tone == RelationshipTone.CAUTIOUS && hitCount >= 3 -> "I remember exactly where you break."
            tone == RelationshipTone.CAUTIOUS -> "I remember where you break."
            stage.ordinal >= RelationshipStage.RECOGNITION.ordinal -> "Keep your feet."
            else -> "Here it comes."
        }
        else -> wolfLine(context, stage, tone, Event.THREAT)
        }
    }

    private fun dogCueLine(context: Context, stage: RelationshipStage, tone: RelationshipTone, cue: EncounterCue): String {
        val warmth = familiarityWarmth(context, EntityType.DOG, stage, tone)
        val strain = strainedConsequence(context, EntityType.DOG, stage, tone)
        return when (cue) {
        EncounterCue.DOG_GREETING -> when {
            warmth == FamiliarityWarmth.BONDED -> "You came back to me!"
            warmth == FamiliarityWarmth.PERSONAL -> "Run with me again!"
            stage == RelationshipStage.MILESTONE && tone == RelationshipTone.WARM -> "You came back!"
            stage.ordinal >= RelationshipStage.TRUST.ordinal && tone == RelationshipTone.WARM -> "Run with me!"
            strain == StrainedConsequence.DISAPPOINTED -> "Easy. Don't miss me there again."
            tone == RelationshipTone.CAUTIOUS -> "Easy. Not that line."
            stage.ordinal >= RelationshipStage.RECOGNITION.ordinal -> "Hi again!"
            else -> "BORF!"
        }
        EncounterCue.DOG_MIDDLE -> when {
            warmth == FamiliarityWarmth.BONDED -> "Still beside me, just like always!"
            warmth == FamiliarityWarmth.PERSONAL -> "Knew you'd keep pace with me!"
            stage == RelationshipStage.MILESTONE && tone == RelationshipTone.WARM -> "Still beside me!"
            stage.ordinal >= RelationshipStage.TRUST.ordinal && tone == RelationshipTone.WARM -> "Knew you'd keep up!"
            strain == StrainedConsequence.DISAPPOINTED -> "Still with me this time?"
            tone == RelationshipTone.CAUTIOUS -> "Still with me?"
            stage.ordinal >= RelationshipStage.RECOGNITION.ordinal -> "Nice pace!"
            else -> "Hi!!"
        }
        EncounterCue.DOG_FAREWELL -> when {
            warmth == FamiliarityWarmth.BONDED -> "See you home. I'll keep it warm!"
            warmth == FamiliarityWarmth.PERSONAL -> "Come back. I'll be waiting!"
            stage == RelationshipStage.MILESTONE && tone == RelationshipTone.WARM -> "See you home!"
            stage.ordinal >= RelationshipStage.TRUST.ordinal && tone == RelationshipTone.WARM -> "Come back soon!"
            strain == StrainedConsequence.DISAPPOINTED -> "Back soon? Please mean it."
            tone == RelationshipTone.CAUTIOUS -> "Back soon?"
            stage.ordinal >= RelationshipStage.RECOGNITION.ordinal -> "See ya!"
            else -> "Bye!!"
        }
        else -> dogLine(context, stage, tone, Event.PASS)
    }
    }

    private fun owlCueLine(
        context: Context,
        stage: RelationshipStage,
        tone: RelationshipTone,
        cue: EncounterCue
    ): String {
        val repeatHits = PersistentMemoryManager.getHitCount(context, EntityType.OWL)
        val passCount = PersistentMemoryManager.getPassCount(context, EntityType.OWL)
        val warmth = familiarityWarmth(context, EntityType.OWL, stage, tone)
        val strain = strainedConsequence(context, EntityType.OWL, stage, tone)
        return when (cue) {
        EncounterCue.OWL_ALERT -> when {
            repeatHits >= 3 -> "Same shadow. Same jump."
            repeatHits >= 2 -> "The night remembers this jump."
            warmth == FamiliarityWarmth.BONDED -> "I know your shape in this shadow."
            warmth == FamiliarityWarmth.PERSONAL -> "You know the shadow I kept for you."
            passCount >= 4 && tone == RelationshipTone.WARM -> "You know this shadow."
            stage == RelationshipStage.MILESTONE && tone == RelationshipTone.WARM -> "I know your timing."
            stage.ordinal >= RelationshipStage.TRUST.ordinal && tone == RelationshipTone.WARM -> "Not prey."
            strain == StrainedConsequence.TENSE -> "The night remembers and tightens."
            tone == RelationshipTone.CAUTIOUS -> "The night remembers."
            stage.ordinal >= RelationshipStage.RECOGNITION.ordinal -> "Still jumping?"
            else -> "...hoo?"
        }
        else -> owlLine(context, stage, tone, Event.THREAT)
        }
    }

    private fun eagleCueLine(context: Context, stage: RelationshipStage, tone: RelationshipTone, cue: EncounterCue): String {
        val warmth = familiarityWarmth(context, EntityType.EAGLE, stage, tone)
        val strain = strainedConsequence(context, EntityType.EAGLE, stage, tone)
        return when (cue) {
        EncounterCue.EAGLE_LOCK -> when {
            warmth == FamiliarityWarmth.BONDED -> "Hold the line I left for you."
            warmth == FamiliarityWarmth.PERSONAL -> "Stay true. I know your line."
            stage == RelationshipStage.MILESTONE && tone == RelationshipTone.WARM -> "Hold the mark."
            stage.ordinal >= RelationshipStage.TRUST.ordinal && tone == RelationshipTone.WARM -> "Stay true."
            strain == StrainedConsequence.FEARFUL -> "Marked where fear takes you."
            tone == RelationshipTone.CAUTIOUS -> "Marked where you waver."
            stage.ordinal >= RelationshipStage.RECOGNITION.ordinal -> "Still marked."
            else -> "Marked."
        }
        else -> eagleLine(context, stage, tone, Event.THREAT)
    }
    }

    private fun owlLine(
        context: Context,
        stage: RelationshipStage,
        tone: RelationshipTone,
        event: Event
    ): String {
        val repeatHits = PersistentMemoryManager.getHitCount(context, EntityType.OWL)
        val passCount = PersistentMemoryManager.getPassCount(context, EntityType.OWL)
        val warmth = familiarityWarmth(context, EntityType.OWL, stage, tone)
        val strain = strainedConsequence(context, EntityType.OWL, stage, tone)
        return when (event) {
        Event.PASS -> when (stage) {
            RelationshipStage.FIRST_IMPRESSION -> if (repeatHits >= 2 && passCount >= 1) "Not the same shadow tonight." else "Silent pass."
            RelationshipStage.RECOGNITION -> if (passCount >= 3) "The dark edge remembered you kindly." else "Still awake."
            RelationshipStage.TRUST -> when {
                warmth == FamiliarityWarmth.BONDED -> "The night kept our familiar shape."
                warmth == FamiliarityWarmth.PERSONAL -> "The night held your shape for me."
                passCount >= 4 && tone == RelationshipTone.WARM -> "The night kept your shape."
                tone == RelationshipTone.WARM -> "Not prey."
                repeatHits >= 2 -> "Not the same jump tonight."
                else -> "Too slow."
            }
            RelationshipStage.MILESTONE -> if (warmth == FamiliarityWarmth.BONDED) {
                "The dark edge feels like our familiar night now."
            } else if (passCount >= 4) {
                "The dark edge feels familiar now."
            } else {
                "The night knows you."
            }
        }
        Event.SPARE -> "The branch stays yours."
        Event.THREAT -> when {
            repeatHits >= 2 -> "The same shadow remembers."
            strain == StrainedConsequence.TENSE -> "The night remembers with a tighter edge."
            tone == RelationshipTone.CAUTIOUS -> "The night remembers."
            else -> "...hoo?"
        }
        Event.RETURN -> when (stage) {
            RelationshipStage.TRUST, RelationshipStage.MILESTONE ->
                if (warmth == FamiliarityWarmth.BONDED) {
                    "Night holds a familiar pair of eyes that already expected you."
                } else if (passCount >= 4) {
                    "Night holds a familiar pair of eyes."
                } else {
                    "Something patient watches the dark edge."
                }
            else -> "Something patient watches the dark edge."
        }
    }
    }

    private fun eagleLine(context: Context, stage: RelationshipStage, tone: RelationshipTone, event: Event): String {
        val warmth = familiarityWarmth(context, EntityType.EAGLE, stage, tone)
        val strain = strainedConsequence(context, EntityType.EAGLE, stage, tone)
        return when (event) {
        Event.PASS -> when (stage) {
            RelationshipStage.FIRST_IMPRESSION -> "Outran the mark."
            RelationshipStage.RECOGNITION -> "Still marked."
            RelationshipStage.TRUST -> when {
                warmth == FamiliarityWarmth.BONDED -> "You held the line I meant for you."
                warmth == FamiliarityWarmth.PERSONAL -> "You held the line I marked."
                tone == RelationshipTone.WARM -> "You held the line."
                else -> "Missed again."
            }
            RelationshipStage.MILESTONE -> if (warmth == FamiliarityWarmth.BONDED) "You know our shadow line." else "You know the shadow."
        }
        Event.SPARE -> "The sky lets you pass."
        Event.THREAT -> when {
            strain == StrainedConsequence.FEARFUL -> "Marked where fear still answers first."
            tone == RelationshipTone.CAUTIOUS -> "Marked where you waver."
            else -> "Marked."
        }
        Event.RETURN -> when (stage) {
            RelationshipStage.TRUST, RelationshipStage.MILESTONE -> if (warmth == FamiliarityWarmth.BONDED) {
                "The sky feels vast, but already threaded for your return."
            } else {
                "The sky feels vast, but no longer empty."
            }
            else -> "A shadow crossed the garden earlier."
        }
    }
    }
}
