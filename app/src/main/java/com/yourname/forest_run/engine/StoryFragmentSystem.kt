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

    fun memoryPageCount(context: Context): Int =
        SaveManager.loadUnlockedMemoryPages(context.applicationContext).size

    fun unlockedMemoryPages(context: Context): Set<String> =
        SaveManager.loadUnlockedMemoryPages(context.applicationContext)

    private fun selectRestFragment(context: Context, biome: Biome, killer: EntityType?): StoryFragment {
        val hitCount = killer?.let { PersistentMemoryManager.getHitCount(context, it) } ?: 0
        val repeatedKiller = killer != null && hitCount >= 2
        val relationshipStage = killer?.let { RelationshipArcSystem.stageFor(context, it) }

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
        val mood = ForestMoodSystem.currentState(context).currentMood

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
            }
        }

        summary?.let {
            if (it.forestMood == ForestMood.GENTLE && it.sparedCount > 0) {
                return StoryFragment(
                    id = "garden_gentle_aftercare",
                    type = StoryFragmentType.GARDEN_REFLECTION,
                    text = "The leaves settle as if they trusted the way you came home.",
                    unlocksPageId = "page_gentle_aftercare"
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
            ForestMood.GENTLE -> "The garden breathes a little easier tonight."
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
