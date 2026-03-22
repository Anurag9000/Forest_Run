package com.yourname.forest_run.engine

import android.content.Context
import com.yourname.forest_run.entities.CostumeStyle
import com.yourname.forest_run.entities.EntityType

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
    val homePresenceLine: String
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
                homePresenceLine = "The cat has started leaving a shared quiet behind, like home already expected both of you."
            )
            EntityType.FOX -> RelationshipMilestoneReward(
                type = type,
                label = "Trail Ribbon",
                summary = "The fox leaves a bright trail that now feels meant for you.",
                traceLabel = "Trail Ribbon",
                costumeReward = CostumeStyle.VINE_SCARF,
                homePresenceLabel = "Quick Path",
                homePresenceLine = "The fox has turned one line through the garden into a path that already knows your rhythm."
            )
            EntityType.WOLF -> RelationshipMilestoneReward(
                type = type,
                label = "Watch Stone",
                summary = "The wolf's silence now feels like a guard post instead of a warning.",
                traceLabel = "Watch Stone",
                costumeReward = CostumeStyle.MOON_CAPE,
                homePresenceLabel = "Kept Watch",
                homePresenceLine = "The wolf has left a steadier kind of watch behind, so the garden feels guarded instead of judged."
            )
            EntityType.DOG -> RelationshipMilestoneReward(
                type = type,
                label = "Welcome Bell",
                summary = "The dog's joy has turned into something the whole garden keeps.",
                traceLabel = "Welcome Bell",
                costumeReward = CostumeStyle.BELL_CHARM,
                homePresenceLabel = "Open Gate",
                homePresenceLine = "The dog has made the whole entrance feel eager for you, like home learned how to rush forward first."
            )
            EntityType.OWL -> RelationshipMilestoneReward(
                type = type,
                label = "Lantern Branch",
                summary = "The owl has made the dark edge of home feel watched over.",
                traceLabel = "Lantern Branch",
                costumeReward = CostumeStyle.LANTERN_PIN,
                homePresenceLabel = "Night Watch",
                homePresenceLine = "The owl has left a calmer kind of night behind, so the dark edge feels kept instead of merely quiet."
            )
            EntityType.EAGLE -> RelationshipMilestoneReward(
                type = type,
                label = "Sky Thread",
                summary = "The eagle has left a stern but welcome line through the sky above home.",
                traceLabel = "Sky Thread",
                costumeReward = CostumeStyle.SKY_SASH,
                homePresenceLabel = "High Thread",
                homePresenceLine = "The eagle has taught the sky above home to feel held together instead of left too wide."
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
        return when (type) {
            EntityType.CAT -> when (stage) {
                RelationshipStage.FIRST_IMPRESSION -> "The cat keeps one eye on the path."
                RelationshipStage.RECOGNITION -> "The cat pretends not to expect you."
                RelationshipStage.TRUST -> when (tone) {
                    RelationshipTone.WARM -> "The cat has stopped leaving when you arrive."
                    RelationshipTone.CAUTIOUS -> "The cat waits, but with the distance your rushed returns taught it."
                    RelationshipTone.NEUTRAL -> "The cat waits, but not too close."
                }
                RelationshipStage.MILESTONE -> if (tone == RelationshipTone.CAUTIOUS) {
                    "The cat behaves like the place is shared, but your nerves still owe it patience."
                } else {
                    "The cat behaves like this was always your shared place."
                }
            }
            EntityType.FOX -> when (stage) {
                RelationshipStage.FIRST_IMPRESSION -> "A clever pause lingers near the path."
                RelationshipStage.RECOGNITION -> "The fox still treats every return like a challenge."
                RelationshipStage.TRUST -> when (tone) {
                    RelationshipTone.WARM -> "The fox leaves room for your answer now."
                    RelationshipTone.CAUTIOUS -> "The fox watches for the same flinch before it watches for your answer."
                    RelationshipTone.NEUTRAL -> "The fox watches to see if you still remember the rhythm."
                }
                RelationshipStage.MILESTONE -> if (tone == RelationshipTone.CAUTIOUS) {
                    "The fox no longer looks surprised when you return, only careful."
                } else {
                    "The fox no longer looks surprised when you keep up."
                }
            }
            EntityType.WOLF -> when (stage) {
                RelationshipStage.FIRST_IMPRESSION -> "The grove still remembers the howl first."
                RelationshipStage.RECOGNITION -> "The wolf feels nearer, but less distant than before."
                RelationshipStage.TRUST -> when (tone) {
                    RelationshipTone.WARM -> "The wolf's silence feels earned."
                    RelationshipTone.CAUTIOUS -> "The wolf keeps testing whether your calm will break where it always does."
                    RelationshipTone.NEUTRAL -> "The wolf keeps testing whether your calm will hold."
                }
                RelationshipStage.MILESTONE -> if (tone == RelationshipTone.CAUTIOUS) {
                    "The grove remembers the respect, but not without remembering the fear too."
                } else {
                    "The grove rests easier when the wolf chooses not to bare its teeth."
                }
            }
            EntityType.DOG -> when (stage) {
                RelationshipStage.FIRST_IMPRESSION -> "The garden still echoes with a bark."
                RelationshipStage.RECOGNITION -> "The dog seems to think every return is an invitation."
                RelationshipStage.TRUST -> when (tone) {
                    RelationshipTone.WARM -> "The dog acts like you were only gone for a minute."
                    RelationshipTone.CAUTIOUS -> "The dog is still glad, but it braces for your nerves before you do."
                    RelationshipTone.NEUTRAL -> "The dog is ready to forgive your nerves faster than you are."
                }
                RelationshipStage.MILESTONE -> if (tone == RelationshipTone.CAUTIOUS) {
                    "The dog still thinks you belong here, and still worries you might forget it mid-run."
                } else {
                    "The dog has fully decided you belong here."
                }
            }
            EntityType.OWL -> when (stage) {
                RelationshipStage.FIRST_IMPRESSION -> "The dark edge keeps a patient shape."
                RelationshipStage.RECOGNITION -> "The owl no longer startles the garden quite as much."
                RelationshipStage.TRUST -> when (tone) {
                    RelationshipTone.WARM -> "The owl watches like a witness, not a warning."
                    RelationshipTone.CAUTIOUS -> "The owl still asks the night to remember the same jump."
                    RelationshipTone.NEUTRAL -> "The owl still asks the night to judge your timing."
                }
                RelationshipStage.MILESTONE -> if (tone == RelationshipTone.CAUTIOUS) {
                    "The night has made room for the owl, but it still keeps a careful eye on you."
                } else {
                    "The night has made room for the owl and still feels welcoming."
                }
            }
            EntityType.EAGLE -> when (stage) {
                RelationshipStage.FIRST_IMPRESSION -> "The sky feels too large to fully trust."
                RelationshipStage.RECOGNITION -> "Even at rest, the shadow crosses your thoughts."
                RelationshipStage.TRUST -> when (tone) {
                    RelationshipTone.WARM -> "The sky feels less hostile when the eagle chooses distance."
                    RelationshipTone.CAUTIOUS -> "The eagle still reminds you exactly where fear keeps answering first."
                    RelationshipTone.NEUTRAL -> "The eagle still reminds you how small a mistake can look from above."
                }
                RelationshipStage.MILESTONE -> if (tone == RelationshipTone.CAUTIOUS) {
                    "The eagle's shadow feels like recognition, but not yet forgiveness."
                } else {
                    "The eagle's shadow feels more like recognition than threat now."
                }
            }
            else -> null
        }
    }

    fun repeatFriendLine(context: Context, type: EntityType): String {
        val stage = stageFor(context.applicationContext, type)
        val tone = toneFor(context.applicationContext, type)
        return when (type) {
            EntityType.CAT -> when (stage) {
                RelationshipStage.TRUST -> if (tone == RelationshipTone.WARM) {
                    "The cat has started treating your returns like part of its routine."
                } else {
                    "The cat no longer acts like your return is a surprise."
                }
                RelationshipStage.MILESTONE -> "The cat moves like the two of you already agreed this is shared ground."
                else -> "The cat has started meeting your softer timing halfway."
            }
            EntityType.FOX -> when (stage) {
                RelationshipStage.TRUST -> if (tone == RelationshipTone.WARM) {
                    "The fox has stopped pretending your rhythm is only a challenge."
                } else {
                    "The fox is beginning to expect your answer instead of only testing it."
                }
                RelationshipStage.MILESTONE -> "The fox now treats your returns like a game both of you already know."
                else -> "The fox keeps leaving room for your answer now."
            }
            EntityType.WOLF -> when (stage) {
                RelationshipStage.TRUST -> if (tone == RelationshipTone.WARM) {
                    "The wolf's respect has started to feel less borrowed and more shared."
                } else {
                    "The wolf no longer sounds surprised when your calm holds."
                }
                RelationshipStage.MILESTONE -> "The grove carries the wolf's respect like something you earned together."
                else -> "The wolf has started recognizing your steadier courage."
            }
            EntityType.DOG -> when (stage) {
                RelationshipStage.TRUST -> if (tone == RelationshipTone.WARM) {
                    "The dog greets you like this joy has already become a habit."
                } else {
                    "The dog keeps acting like your return was always going to happen."
                }
                RelationshipStage.MILESTONE -> "The dog's excitement now feels less like a surprise and more like belonging."
                else -> "The dog has started keeping your return in mind."
            }
            EntityType.OWL -> when (stage) {
                RelationshipStage.TRUST -> if (tone == RelationshipTone.WARM) {
                    "The owl has started watching like a witness that already knows you."
                } else {
                    "The owl keeps the night open for you a little longer now."
                }
                RelationshipStage.MILESTONE -> "The owl has made the dark edge feel like a place you both remember."
                else -> "The owl has started letting familiarity into the night."
            }
            EntityType.EAGLE -> when (stage) {
                RelationshipStage.TRUST -> if (tone == RelationshipTone.WARM) {
                    "The eagle has started leaving more sky between you and fear."
                } else {
                    "The eagle no longer turns every return into a warning."
                }
                RelationshipStage.MILESTONE -> "Even the eagle's shadow now feels more like recognition than judgment."
                else -> "The eagle has started recognizing your return before your fear does."
            }
            else -> lineFor(context, type, Event.RETURN)
        }
    }

    fun strainedBondLine(context: Context, type: EntityType): String {
        val appContext = context.applicationContext
        val stage = stageFor(appContext, type)
        return when (type) {
            EntityType.CAT -> when (stage) {
                RelationshipStage.MILESTONE -> "The cat still keeps your place, but not without remembering how abruptly you keep reaching for it."
                RelationshipStage.TRUST -> "The cat stays nearby, but with the kind of distance you taught it."
                else -> "The cat has started expecting your nerves before your kindness."
            }
            EntityType.FOX -> when (stage) {
                RelationshipStage.MILESTONE -> "The fox still answers you, but with the sharper kind of patience that follows repeated flinching."
                RelationshipStage.TRUST -> "The fox has started watching for the same hesitation before it watches for you."
                else -> "The fox keeps testing the place where your timing keeps giving way."
            }
            EntityType.WOLF -> when (stage) {
                RelationshipStage.MILESTONE -> "The wolf still knows you, but now it measures whether your calm will fail in the same old place."
                RelationshipStage.TRUST -> "The wolf's respect has gone careful, like it remembers exactly where you keep breaking."
                else -> "The wolf has started treating your fear like something it already recognizes."
            }
            EntityType.DOG -> when (stage) {
                RelationshipStage.MILESTONE -> "The dog still comes close, but even that joy has started bracing for the same hurt."
                RelationshipStage.TRUST -> "The dog forgives quickly, but not quickly enough to forget the bark-line you keep missing."
                else -> "The dog's excitement has started carrying a little caution in it."
            }
            EntityType.OWL -> when (stage) {
                RelationshipStage.MILESTONE -> "The owl still keeps the dark edge for you, but the night has started sounding more watchful than welcoming."
                RelationshipStage.TRUST -> "The owl has started meeting you with caution instead of only recognition."
                else -> "The owl has started expecting the same mistake before it expects your return."
            }
            EntityType.EAGLE -> when (stage) {
                RelationshipStage.MILESTONE -> "The eagle still recognizes you, but the sky now holds that recognition like a warning."
                RelationshipStage.TRUST -> "The eagle's shadow has started feeling like a test you keep failing in the same place."
                else -> "The eagle has started expecting your fear before your steadiness."
            }
            else -> lineFor(appContext, type, Event.RETURN)
        }
    }

    fun encounterCueLine(context: Context, type: EntityType, cue: EncounterCue): String {
        val stage = stageFor(context, type)
        val tone = toneFor(context, type)
        return when (type) {
            EntityType.CAT -> catCueLine(stage, tone, cue)
            EntityType.FOX -> foxCueLine(stage, tone, cue)
            EntityType.WOLF -> wolfCueLine(stage, tone, cue)
            EntityType.OWL -> owlCueLine(stage, tone, cue)
            EntityType.EAGLE -> eagleCueLine(stage, tone, cue)
            EntityType.DOG -> dogCueLine(stage, tone, cue)
            else -> lineFor(context, type, Event.THREAT)
        }
    }

    fun dogBuddyDialogue(context: Context): List<String> = listOf(
        encounterCueLine(context, EntityType.DOG, EncounterCue.DOG_GREETING),
        encounterCueLine(context, EntityType.DOG, EncounterCue.DOG_MIDDLE),
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
            EntityType.CAT -> catLine(stage, tone, event)
            EntityType.FOX -> foxLine(stage, tone, event)
            EntityType.WOLF -> wolfLine(stage, tone, event)
            EntityType.DOG -> dogLine(stage, tone, event)
            EntityType.OWL -> owlLine(stage, tone, event)
            EntityType.EAGLE -> eagleLine(stage, tone, event)
            else -> ""
        }
    }

    private fun computeStage(type: EntityType, encounters: Int, spared: Int, hits: Int): RelationshipStage {
        val config = thresholds.getValue(type)
        val score = encounters + spared * 2 + maxOf(0, spared - hits)
        return when {
            score >= config.milestoneScore -> RelationshipStage.MILESTONE
            score >= config.trustScore -> RelationshipStage.TRUST
            score >= config.recognitionScore -> RelationshipStage.RECOGNITION
            else -> RelationshipStage.FIRST_IMPRESSION
        }
    }

    private fun affinityScore(context: Context, type: EntityType): Int {
        val appContext = context.applicationContext
        val encounters = SaveManager.loadEncounterCount(appContext, type)
        val spared = SaveManager.loadSparedCount(appContext, type)
        val hits = SaveManager.loadHitCount(appContext, type)
        return encounters + spared * 3 - hits
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

    private fun formatName(type: EntityType): String =
        type.name.lowercase().split("_").joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

    private fun catLine(stage: RelationshipStage, tone: RelationshipTone, event: Event): String = when (event) {
        Event.PASS -> when (stage) {
            RelationshipStage.FIRST_IMPRESSION -> "Meow?"
            RelationshipStage.RECOGNITION -> if (tone == RelationshipTone.WARM) "You again." else "Soft steps."
            RelationshipStage.TRUST -> "Stayed a while?"
            RelationshipStage.MILESTONE -> "Home?"
        }
        Event.SPARE -> when (stage) {
            RelationshipStage.MILESTONE -> "See you soon."
            RelationshipStage.TRUST -> "I'll stay."
            else -> "Friend?"
        }
        Event.THREAT -> if (tone == RelationshipTone.CAUTIOUS) "Too sudden again." else "Hiss!"
        Event.RETURN -> when (stage) {
            RelationshipStage.TRUST, RelationshipStage.MILESTONE -> "A cat has already claimed this place."
            else -> "A cat watches from the path."
        }
    }

    private fun foxLine(stage: RelationshipStage, tone: RelationshipTone, event: Event): String = when (event) {
        Event.PASS -> when (stage) {
            RelationshipStage.FIRST_IMPRESSION -> "Heh."
            RelationshipStage.RECOGNITION -> "Same jump?"
            RelationshipStage.TRUST -> if (tone == RelationshipTone.WARM) "Still with me?" else "Caught that."
            RelationshipStage.MILESTONE -> "Knew you would."
        }
        Event.SPARE -> when (stage) {
            RelationshipStage.MILESTONE -> "Till next time."
            RelationshipStage.TRUST -> "Fine. Go on."
            else -> "Fine."
        }
        Event.THREAT -> if (tone == RelationshipTone.CAUTIOUS) "Same hesitation." else "Next time..."
        Event.RETURN -> when (stage) {
            RelationshipStage.TRUST, RelationshipStage.MILESTONE -> "A fox lingers like it expected you."
            else -> "Something clever moved through the garden."
        }
    }

    private fun wolfLine(stage: RelationshipStage, tone: RelationshipTone, event: Event): String = when (event) {
        Event.PASS -> when (stage) {
            RelationshipStage.FIRST_IMPRESSION -> "You made it."
            RelationshipStage.RECOGNITION -> "Again."
            RelationshipStage.TRUST -> if (tone == RelationshipTone.WARM) "You held steady." else "Still standing."
            RelationshipStage.MILESTONE -> "You know the howl now."
        }
        Event.SPARE -> when (stage) {
            RelationshipStage.MILESTONE -> "Then pass in peace."
            RelationshipStage.TRUST -> "Go on."
            else -> "Not today."
        }
        Event.THREAT -> if (tone == RelationshipTone.CAUTIOUS) "I remember where you break." else "GRRR..."
        Event.RETURN -> when (stage) {
            RelationshipStage.TRUST, RelationshipStage.MILESTONE -> "The grove feels watched, not threatened."
            else -> "A distant howl still belongs to the path."
        }
    }

    private fun dogLine(stage: RelationshipStage, tone: RelationshipTone, event: Event): String = when (event) {
        Event.PASS -> when (stage) {
            RelationshipStage.FIRST_IMPRESSION -> "Good hop!"
            RelationshipStage.RECOGNITION -> "Hi!!"
            RelationshipStage.TRUST -> if (tone == RelationshipTone.WARM) "Still here!" else "Nice one!"
            RelationshipStage.MILESTONE -> "Best friend!"
        }
        Event.SPARE -> "Best friend!"
        Event.THREAT -> if (tone == RelationshipTone.CAUTIOUS) "Easy. Not that line again." else "Hi!!"
        Event.RETURN -> when (stage) {
            RelationshipStage.TRUST, RelationshipStage.MILESTONE -> "The garden still feels wagged awake."
            else -> "A bark seems closer than before."
        }
    }

    private fun catCueLine(stage: RelationshipStage, tone: RelationshipTone, cue: EncounterCue): String = when (cue) {
        EncounterCue.MERCY -> when {
            stage == RelationshipStage.MILESTONE && tone == RelationshipTone.WARM -> "Easy. I know you."
            stage.ordinal >= RelationshipStage.TRUST.ordinal && tone == RelationshipTone.WARM -> "Close, friend."
            tone == RelationshipTone.CAUTIOUS -> "Too sudden again?"
            stage.ordinal >= RelationshipStage.RECOGNITION.ordinal -> "Softly."
            else -> "Close one."
        }
        else -> catLine(stage, tone, Event.THREAT)
    }

    private fun foxCueLine(stage: RelationshipStage, tone: RelationshipTone, cue: EncounterCue): String = when (cue) {
        EncounterCue.FOX_LANDING -> when {
            stage == RelationshipStage.MILESTONE && tone == RelationshipTone.WARM -> "Knew you'd stay with me."
            stage.ordinal >= RelationshipStage.TRUST.ordinal && tone == RelationshipTone.WARM -> "You remembered."
            tone == RelationshipTone.CAUTIOUS -> "Still flinching at the same step?"
            stage.ordinal >= RelationshipStage.RECOGNITION.ordinal -> "Caught that."
            else -> "Next time..."
        }
        else -> foxLine(stage, tone, Event.THREAT)
    }

    private fun wolfCueLine(stage: RelationshipStage, tone: RelationshipTone, cue: EncounterCue): String = when (cue) {
        EncounterCue.WOLF_CHARGE -> when {
            stage == RelationshipStage.MILESTONE && tone == RelationshipTone.WARM -> "Hold steady."
            stage.ordinal >= RelationshipStage.TRUST.ordinal && tone == RelationshipTone.WARM -> "Stand your ground."
            tone == RelationshipTone.CAUTIOUS -> "I remember where you break."
            stage.ordinal >= RelationshipStage.RECOGNITION.ordinal -> "Keep your feet."
            else -> "Here it comes."
        }
        else -> wolfLine(stage, tone, Event.THREAT)
    }

    private fun dogCueLine(stage: RelationshipStage, tone: RelationshipTone, cue: EncounterCue): String = when (cue) {
        EncounterCue.DOG_GREETING -> when {
            stage == RelationshipStage.MILESTONE && tone == RelationshipTone.WARM -> "You came back!"
            stage.ordinal >= RelationshipStage.TRUST.ordinal && tone == RelationshipTone.WARM -> "Run with me!"
            tone == RelationshipTone.CAUTIOUS -> "Easy. Not that line."
            stage.ordinal >= RelationshipStage.RECOGNITION.ordinal -> "Hi again!"
            else -> "BORF!"
        }
        EncounterCue.DOG_MIDDLE -> when {
            stage == RelationshipStage.MILESTONE && tone == RelationshipTone.WARM -> "Still beside me!"
            stage.ordinal >= RelationshipStage.TRUST.ordinal && tone == RelationshipTone.WARM -> "Knew you'd keep up!"
            tone == RelationshipTone.CAUTIOUS -> "Still with me?"
            stage.ordinal >= RelationshipStage.RECOGNITION.ordinal -> "Nice pace!"
            else -> "Hi!!"
        }
        EncounterCue.DOG_FAREWELL -> when {
            stage == RelationshipStage.MILESTONE && tone == RelationshipTone.WARM -> "See you home!"
            stage.ordinal >= RelationshipStage.TRUST.ordinal && tone == RelationshipTone.WARM -> "Come back soon!"
            tone == RelationshipTone.CAUTIOUS -> "Back soon?"
            stage.ordinal >= RelationshipStage.RECOGNITION.ordinal -> "See ya!"
            else -> "Bye!!"
        }
        else -> dogLine(stage, tone, Event.PASS)
    }

    private fun owlCueLine(stage: RelationshipStage, tone: RelationshipTone, cue: EncounterCue): String = when (cue) {
        EncounterCue.OWL_ALERT -> when {
            stage == RelationshipStage.MILESTONE && tone == RelationshipTone.WARM -> "I know your timing."
            stage.ordinal >= RelationshipStage.TRUST.ordinal && tone == RelationshipTone.WARM -> "Not prey."
            tone == RelationshipTone.CAUTIOUS -> "The night remembers."
            stage.ordinal >= RelationshipStage.RECOGNITION.ordinal -> "Still jumping?"
            else -> "...hoo?"
        }
        else -> owlLine(stage, tone, Event.THREAT)
    }

    private fun eagleCueLine(stage: RelationshipStage, tone: RelationshipTone, cue: EncounterCue): String = when (cue) {
        EncounterCue.EAGLE_LOCK -> when {
            stage == RelationshipStage.MILESTONE && tone == RelationshipTone.WARM -> "Hold the mark."
            stage.ordinal >= RelationshipStage.TRUST.ordinal && tone == RelationshipTone.WARM -> "Stay true."
            tone == RelationshipTone.CAUTIOUS -> "Marked where you waver."
            stage.ordinal >= RelationshipStage.RECOGNITION.ordinal -> "Still marked."
            else -> "Marked."
        }
        else -> eagleLine(stage, tone, Event.THREAT)
    }

    private fun owlLine(stage: RelationshipStage, tone: RelationshipTone, event: Event): String = when (event) {
        Event.PASS -> when (stage) {
            RelationshipStage.FIRST_IMPRESSION -> "Silent pass."
            RelationshipStage.RECOGNITION -> "Still awake."
            RelationshipStage.TRUST -> if (tone == RelationshipTone.WARM) "Not prey." else "Too slow."
            RelationshipStage.MILESTONE -> "The night knows you."
        }
        Event.SPARE -> "The branch stays yours."
        Event.THREAT -> if (tone == RelationshipTone.CAUTIOUS) "The night remembers." else "...hoo?"
        Event.RETURN -> when (stage) {
            RelationshipStage.TRUST, RelationshipStage.MILESTONE -> "Night holds a familiar pair of eyes."
            else -> "Something patient watches the dark edge."
        }
    }

    private fun eagleLine(stage: RelationshipStage, tone: RelationshipTone, event: Event): String = when (event) {
        Event.PASS -> when (stage) {
            RelationshipStage.FIRST_IMPRESSION -> "Outran the mark."
            RelationshipStage.RECOGNITION -> "Still marked."
            RelationshipStage.TRUST -> if (tone == RelationshipTone.WARM) "You held the line." else "Missed again."
            RelationshipStage.MILESTONE -> "You know the shadow."
        }
        Event.SPARE -> "The sky lets you pass."
        Event.THREAT -> if (tone == RelationshipTone.CAUTIOUS) "Marked where you waver." else "Marked."
        Event.RETURN -> when (stage) {
            RelationshipStage.TRUST, RelationshipStage.MILESTONE -> "The sky feels vast, but no longer empty."
            else -> "A shadow crossed the garden earlier."
        }
    }
}
