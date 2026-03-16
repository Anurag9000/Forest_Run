package com.yourname.forest_run.engine

import android.content.Context
import com.yourname.forest_run.entities.EntityType

enum class StoryFragmentType {
    REST,
    GARDEN_REFLECTION,
    MEMORY_PAGE
}

data class StoryFragment(
    val id: String,
    val type: StoryFragmentType,
    val text: String,
    val unlocksPageId: String? = null
)

object StoryFragmentSystem {

    fun restQuote(context: Context, biome: Biome, killer: EntityType?): String {
        val fragment = selectRestFragment(context.applicationContext, biome, killer)
        fragment.unlocksPageId?.let { unlockMemoryPage(context.applicationContext, it) }
        return fragment.text
    }

    fun gardenReflection(context: Context, summary: RunSummary?): String? {
        val appContext = context.applicationContext
        val fragment = selectGardenFragment(appContext, summary) ?: return null
        fragment.unlocksPageId?.let { unlockMemoryPage(appContext, it) }
        return fragment.text
    }

    fun creatureThought(context: Context, type: EntityType?): String? {
        val tracked = type ?: return null
        val text = RelationshipArcSystem.creatureThought(context.applicationContext, tracked) ?: return null
        val pageId = "page_thought_${tracked.name.lowercase()}"
        unlockMemoryPage(context.applicationContext, pageId)
        return text
    }

    fun weatherThought(context: Context, summary: RunSummary?): String {
        val appContext = context.applicationContext
        val mood = summary?.forestMood ?: ForestMoodSystem.currentState(appContext).currentMood
        val strongest = RelationshipArcSystem.preferredGardenVisitor(appContext, RelationshipStage.RECOGNITION)
        val repeatFriend = RelationshipArcSystem.featuredRepeatFriend(appContext)
        val milestoneReward = RelationshipArcSystem.featuredMilestoneReward(appContext)
        val repeatedHarmCreature = PersistentMemoryManager.featuredTenderCreature(appContext)
        val repeatedKindnessCreature = PersistentMemoryManager.featuredWarmCreature(appContext)
        val text = when (mood) {
            ForestMood.GENTLE -> if (summary?.pacifistRouteTier == PacifistRouteTier.PEACEFUL) {
                "The evening wind sounds like it is trying not to disturb the peace you carried home."
            } else if (repeatFriend != null) {
                "The evening wind sounds like it has started expecting the same familiar kindness to return."
            } else if (summary?.pacifistRouteTier == PacifistRouteTier.KIND) {
                "The evening wind keeps the kinder edges of the run from disappearing too quickly."
            } else if (milestoneReward != null) {
                "The evening wind moves like it has learned the shape of your better returns."
            } else if (strongest != null) {
                "The evening wind moves like it knows who has been welcomed here."
            } else {
                "The evening wind has nothing urgent left to say."
            }
            ForestMood.RECKLESS -> if (repeatedHarmCreature != null) {
                "Even the restless branches seem to know which fear keeps coming back with you."
            } else if (milestoneReward != null) {
                "Even the restless branches seem unwilling to break what trust has grown here."
            } else {
                "The branches still rustle like they are catching up to your hurry."
            }
            ForestMood.FEARFUL -> if (repeatedHarmCreature != null) {
                "The air stays careful, as if it knows which shadow still follows you home."
            } else {
                "The air stays soft, as if the weather decided not to press its luck."
            }
            ForestMood.STEADY -> if (repeatFriend != null) {
                "The wind keeps a steady pace, like it recognizes a familiar bond walking back in."
            } else if (repeatedKindnessCreature != null && summary?.pacifistRouteTier == PacifistRouteTier.KIND) {
                "The wind keeps a patient pace, like it noticed kindness becoming a habit."
            } else if (milestoneReward != null) {
                "The wind keeps a patient pace, like it recognizes this version of you."
            } else {
                "The wind keeps a patient pace through the garden."
            }
        }
        unlockMemoryPage(appContext, "page_weather_${mood.name.lowercase()}")
        return text
    }

    fun memoryPageCount(context: Context): Int =
        SaveManager.loadUnlockedMemoryPages(context.applicationContext).size

    fun unlockedMemoryPages(context: Context): Set<String> =
        SaveManager.loadUnlockedMemoryPages(context.applicationContext)

    private fun selectRestFragment(context: Context, biome: Biome, killer: EntityType?): StoryFragment {
        val hitCount = killer?.let { PersistentMemoryManager.getHitCount(context, it) } ?: 0
        val repeatedKiller = killer != null && hitCount >= 2
        val relationshipStage = killer?.let { RelationshipArcSystem.stageFor(context, it) }
        val repeatFriend = RelationshipArcSystem.featuredRepeatFriend(context)
        val warmCreature = PersistentMemoryManager.featuredWarmCreature(context)

        if (killer != null && repeatedKiller) {
            val text = when (killer) {
                EntityType.CAT -> "The cat already knew your rhythm."
                EntityType.FOX -> "The fox keeps learning you faster than you learn it."
                EntityType.WOLF -> if (relationshipStage == RelationshipStage.TRUST || relationshipStage == RelationshipStage.MILESTONE) {
                    "Even the familiar howl found the same opening."
                } else {
                    "The howl keeps finding the same weak moment."
                }
                EntityType.DOG -> "You heard the bark and still stayed in its line."
                EntityType.HEDGEHOG -> "The tiny ones still punish impatience."
                EntityType.OWL -> "The owl waits for that same jump every time."
                EntityType.EAGLE -> "The sky keeps remembering where you panic."
                else -> "The forest noticed the pattern before you did."
            }
            return StoryFragment(
                id = "rest_repeat_${killer.name.lowercase()}",
                type = StoryFragmentType.REST,
                text = text,
                unlocksPageId = "page_repeat_${killer.name.lowercase()}"
            )
        }

        if (killer != null) {
            val text = when (killer) {
                EntityType.CAT -> if (relationshipStage == RelationshipStage.TRUST || relationshipStage == RelationshipStage.MILESTONE) {
                    "Even familiar paws need you to slow down."
                } else {
                    "You rushed the cat instead of reading it."
                }
                EntityType.FOX -> "The fox wanted a conversation in motion."
                EntityType.WOLF -> "The wolf announced the charge. You stayed anyway."
                EntityType.DOG -> "The bark wave came first. The hit came second."
                EntityType.HEDGEHOG -> "You clipped the thorns and lost your tempo."
                EntityType.DUCK -> "The duck owned the lane you forgot to lower for."
                EntityType.TIT, EntityType.CHICKADEE -> "The flock looked small until it was not."
                EntityType.OWL -> if (relationshipStage == RelationshipStage.TRUST || relationshipStage == RelationshipStage.MILESTONE) {
                    "The owl only turns when you ask the night to notice."
                } else {
                    "The owl only dives when you tell it to."
                }
                EntityType.EAGLE -> "The eagle marked you before it arrived."
                EntityType.CACTUS,
                EntityType.LILY_OF_VALLEY,
                EntityType.HYACINTH,
                EntityType.EUCALYPTUS,
                EntityType.VANILLA_ORCHID,
                EntityType.WEEPING_WILLOW,
                EntityType.JACARANDA,
                EntityType.BAMBOO,
                EntityType.CHERRY_BLOSSOM -> "The plants were speaking with spacing, not words."
            }
            return StoryFragment(
                id = "rest_hit_${killer.name.lowercase()}",
                type = StoryFragmentType.REST,
                text = text,
                unlocksPageId = when (killer) {
                    EntityType.OWL, EntityType.EAGLE, EntityType.WOLF -> "page_mark_${killer.name.lowercase()}"
                    else -> null
                }
            )
        }

        if (repeatFriend != null) {
            return StoryFragment(
                id = "rest_repeat_friend_${repeatFriend.name.lowercase()}",
                type = StoryFragmentType.REST,
                text = when (repeatFriend) {
                    EntityType.CAT -> "The cat is beginning to treat your return like part of the evening."
                    EntityType.FOX -> "The fox no longer makes your gentler timing feel accidental."
                    EntityType.WOLF -> "The grove has started trusting the calmer version of your courage."
                    EntityType.DOG -> "The dog seems certain this happiness belongs to your return now."
                    EntityType.OWL -> "The owl has started leaving more familiarity than warning in the dark."
                    EntityType.EAGLE -> "Even the eagle's shadow feels less severe when it keeps meeting the same calm."
                    else -> "Something in the forest has started expecting your gentler return."
                },
                unlocksPageId = "page_repeat_friend_${repeatFriend.name.lowercase()}"
            )
        }

        if (warmCreature != null) {
            val text = when (warmCreature) {
                EntityType.CAT -> "The cat is beginning to expect your softer timing."
                EntityType.FOX -> "The fox seems to remember when you answer with calm."
                EntityType.WOLF -> "The grove keeps the version of you that did not flinch."
                EntityType.DOG -> "The dog seems to think your gentler runs are worth celebrating."
                EntityType.OWL -> "Even the owl leaves the night feeling less severe after mercy."
                EntityType.EAGLE -> "The sky feels less punishing when you keep choosing restraint."
                else -> "The forest noticed that your gentleness lasted longer this time."
            }
            return StoryFragment(
                id = "rest_warm_${warmCreature.name.lowercase()}",
                type = StoryFragmentType.REST,
                text = text,
                unlocksPageId = "page_warm_${warmCreature.name.lowercase()}"
            )
        }

        val biomeText = when (biome) {
            Biome.MEADOW -> "The meadow is gentle only if you stay gentle with it."
            Biome.ORCHARD -> "The orchard rewards rhythm more than speed."
            Biome.ANCIENT_GROVE -> "The grove asks for patience before bravery."
            Biome.DUSK_CANYON -> "Dusk shortens every decision."
            Biome.NIGHT_FOREST -> "Night keeps the score, but it also keeps the memory."
        }
        return StoryFragment(
            id = "rest_biome_${biome.name.lowercase()}",
            type = StoryFragmentType.REST,
            text = biomeText
        )
    }

    private fun selectGardenFragment(context: Context, summary: RunSummary?): StoryFragment? {
        val strongest = RelationshipArcSystem.strongestRelationship(context)
        val milestoneReward = RelationshipArcSystem.featuredMilestoneReward(context)
        val repeatFriend = RelationshipArcSystem.featuredRepeatFriend(context)
        val mood = ForestMoodSystem.currentState(context).currentMood
        val repeatedKiller = PersistentMemoryManager.featuredRepeatKiller(context)
        val repeatedHarmCreature = PersistentMemoryManager.featuredTenderCreature(context)
            ?: (summary?.lastKiller ?: PersistentMemoryManager.getLastKiller(context))?.takeIf {
                PersistentMemoryManager.getHitCount(context, it) >= 2
            }
        val repeatedKindnessCreature = PersistentMemoryManager.featuredWarmCreature(context)

        if (milestoneReward != null && summary?.forestMood == ForestMood.GENTLE && (summary.sparedCount > 0 || summary.kindnessChain >= 5)) {
            return StoryFragment(
                id = "garden_milestone_gentle_${milestoneReward.type.name.lowercase()}",
                type = StoryFragmentType.GARDEN_REFLECTION,
                text = when (milestoneReward.type) {
                    EntityType.CAT -> "The cat's quiet patch feels especially close after a gentle return."
                    EntityType.FOX -> "The brighter trail looks almost proud of how gently you came back."
                    EntityType.WOLF -> "The watch stone feels calmer when your courage stays soft."
                    EntityType.DOG -> "Even the welcome bell sounds softer after a kind run."
                    EntityType.OWL -> "The lantern branch keeps a warmer light after a gentler night."
                    EntityType.EAGLE -> "The sky thread looks less severe when you come home with mercy."
                    else -> "Something trusted your gentler return."
                },
                unlocksPageId = "page_milestone_gentle_${milestoneReward.type.name.lowercase()}"
            )
        }

        if (summary != null && repeatFriend != null && summary.hitsTaken == 0 && summary.cleanPasses >= 8) {
            return StoryFragment(
                id = "garden_repeat_friend_${repeatFriend.name.lowercase()}",
                type = StoryFragmentType.GARDEN_REFLECTION,
                text = when (repeatFriend) {
                    EntityType.CAT -> "The garden has started treating the cat's quiet return as part of yours."
                    EntityType.FOX -> "The brighter part of the path behaves like it remembers both of you now."
                    EntityType.WOLF -> "The grove keeps the wolf's respect near the same places your calm returns to."
                    EntityType.DOG -> "The garden sounds more welcoming when the dog's joy feels expected instead of sudden."
                    EntityType.OWL -> "The dark edge feels like a familiar witness instead of a warning now."
                    EntityType.EAGLE -> "Even the sky seems to expect recognition instead of fear from that shadow now."
                    else -> "The garden has started keeping a familiar shape around your better returns."
                },
                unlocksPageId = "page_repeat_friend_garden_${repeatFriend.name.lowercase()}"
            )
        }

        if (summary != null && repeatedKindnessCreature != null && (summary.sparedCount > 0 || summary.kindnessChain >= 4)) {
            return StoryFragment(
                id = "garden_repeated_kindness_${repeatedKindnessCreature.name.lowercase()}",
                type = StoryFragmentType.GARDEN_REFLECTION,
                text = "The garden has started trusting your gentler habits as something dependable.",
                unlocksPageId = "page_garden_warm_${repeatedKindnessCreature.name.lowercase()}"
            )
        }

        if (summary != null && summary.pacifistRouteTier == PacifistRouteTier.KIND && (summary.sparedCount > 0 || summary.kindnessChain >= 4)) {
            return StoryFragment(
                id = "garden_route_kind",
                type = StoryFragmentType.GARDEN_REFLECTION,
                text = "The garden kept the kindness of that run close instead of treating it like a small accident.",
                unlocksPageId = "page_route_kind"
            )
        }

        if (summary != null && summary.hitsTaken == 0 && summary.pacifistRouteTier == PacifistRouteTier.PEACEFUL) {
            return StoryFragment(
                id = "garden_route_peaceful",
                type = StoryFragmentType.GARDEN_REFLECTION,
                text = "The whole garden seems to be listening to the peace you brought back with you.",
                unlocksPageId = "page_route_peaceful"
            )
        }

        if (summary != null && summary.hitsTaken == 0 && summary.pacifistRouteTier == PacifistRouteTier.MERCIFUL) {
            return StoryFragment(
                id = "garden_route_merciful",
                type = StoryFragmentType.GARDEN_REFLECTION,
                text = "Mercy left the path feeling less guarded than it did before the run began.",
                unlocksPageId = "page_route_merciful"
            )
        }

        if (strongest != null) {
            val (type, stage) = strongest
            if (stage == RelationshipStage.MILESTONE) {
                val text = when (type) {
                    EntityType.CAT -> "A quiet patch of grass feels already occupied."
                    EntityType.FOX -> "Something clever left the path slightly brighter."
                    EntityType.WOLF -> "The grove feels guarded instead of watched."
                    EntityType.DOG -> "The air still carries a happy kind of noise."
                    EntityType.OWL -> "The dark edge of the garden no longer feels empty."
                    EntityType.EAGLE -> "Even the sky above home feels like part of the bond now."
                    else -> "The garden keeps a trace of someone you know."
                }
                return StoryFragment(
                    id = "garden_bond_${type.name.lowercase()}",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = text,
                    unlocksPageId = "page_bond_${type.name.lowercase()}"
                )
            } else if (stage == RelationshipStage.TRUST) {
                val text = when (type) {
                    EntityType.CAT -> "A familiar pause lingers near the flowers."
                    EntityType.FOX -> "The path looks like it expects an answer from you."
                    EntityType.WOLF -> "The grove sounds less empty than it used to."
                    EntityType.DOG -> "Something about the air still feels eager."
                    EntityType.OWL -> "The dark edge feels watched over instead of watched."
                    EntityType.EAGLE -> "The sky feels stern, but not unfriendly."
                    else -> "Something familiar has stayed behind."
                }
                return StoryFragment(
                    id = "garden_trust_${type.name.lowercase()}",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = text
                )
            }
        }

        summary?.let {
            if (repeatedKiller != null && repeatedKiller == repeatedHarmCreature && it.hitsTaken > 0) {
                return StoryFragment(
                    id = "garden_same_shadow_${repeatedKiller.name.lowercase()}",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = "The garden has started recognizing the same shadow before you even name it.",
                    unlocksPageId = "page_same_shadow_${repeatedKiller.name.lowercase()}"
                )
            }
            if (repeatedHarmCreature != null && (it.hitsTaken > 0 || it.forestMood == ForestMood.FEARFUL)) {
                return StoryFragment(
                    id = "garden_repeated_harm_${repeatedHarmCreature.name.lowercase()}",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = "The garden is gentle about the places your nerves still remember.",
                    unlocksPageId = "page_garden_caution_${repeatedHarmCreature.name.lowercase()}"
                )
            }
            if (it.forestMood == ForestMood.GENTLE && it.sparedCount > 0) {
                return StoryFragment(
                    id = "garden_gentle_aftercare",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = "The leaves settle as if they trusted the way you came home.",
                    unlocksPageId = "page_gentle_aftercare"
                )
            }
            if (it.cleanPasses >= 12 && it.hitsTaken == 0) {
                return StoryFragment(
                    id = "garden_clean_return",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = "The whole garden feels as if it noticed how calmly you made it through.",
                    unlocksPageId = "page_clean_return"
                )
            }
            if (it.bloomConversions >= 4) {
                return StoryFragment(
                    id = "garden_bloom_afterglow",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = "A little of Bloom is still hanging in the leaves around home.",
                    unlocksPageId = "page_bloom_afterglow"
                )
            }
            if (it.isNewHighScore) {
                return StoryFragment(
                    id = "garden_after_best",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = "The path still hums with the distance you carried back.",
                    unlocksPageId = "page_after_best"
                )
            }
        }

        val text = when (mood) {
            ForestMood.GENTLE -> if (repeatFriend != null) {
                "The garden breathes like it has started expecting the same familiar kindness to come back."
            } else if (repeatedKindnessCreature != null) {
                "The garden breathes like it has started trusting your softer returns."
            } else {
                "The garden breathes a little easier tonight."
            }
            ForestMood.RECKLESS -> "Even the flowers look like they heard you arrive too fast."
            ForestMood.FEARFUL -> "The garden keeps its voice low until you are ready again."
            ForestMood.STEADY -> "Home feels ordinary in the best possible way."
        }
        return StoryFragment(
            id = "garden_mood_${mood.name.lowercase()}",
            type = StoryFragmentType.GARDEN_REFLECTION,
            text = text
        )
    }

    private fun unlockMemoryPage(context: Context, pageId: String) {
        val unlocked = SaveManager.loadUnlockedMemoryPages(context).toMutableSet()
        if (unlocked.add(pageId)) {
            SaveManager.saveUnlockedMemoryPages(context, unlocked)
        }
    }
}
