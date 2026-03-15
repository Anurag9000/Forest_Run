package com.yourname.forest_run.ui

import android.content.Context
import com.yourname.forest_run.engine.Biome
import com.yourname.forest_run.engine.PersistentMemoryManager
import com.yourname.forest_run.entities.EntityType

/**
 * Builds short reflective quotes for the post-run rest beat.
 */
object RestQuoteManager {

    fun quoteFor(context: Context, biome: Biome, killer: EntityType?): String {
        val hitCount = killer?.let { PersistentMemoryManager.getHitCount(context, it) } ?: 0
        val repeatedKiller = killer != null && hitCount >= 2

        killer?.let { type ->
            if (repeatedKiller) {
                return when (type) {
                    EntityType.CAT -> "The cat already knew your rhythm."
                    EntityType.FOX -> "The fox keeps learning you faster than you learn it."
                    EntityType.WOLF -> "The howl keeps finding the same weak moment."
                    EntityType.DOG -> "You heard the bark and still stayed in its line."
                    EntityType.HEDGEHOG -> "The tiny ones still punish impatience."
                    EntityType.OWL -> "The owl waits for that same jump every time."
                    EntityType.EAGLE -> "The sky keeps remembering where you panic."
                    else -> "The forest noticed the pattern before you did."
                }
            }
        }

        killer?.let { type ->
            return when (type) {
                EntityType.CAT -> "You rushed the cat instead of reading it."
                EntityType.FOX -> "The fox wanted a conversation in motion."
                EntityType.WOLF -> "The wolf announced the charge. You stayed anyway."
                EntityType.DOG -> "The bark wave came first. The hit came second."
                EntityType.HEDGEHOG -> "You clipped the thorns and lost your tempo."
                EntityType.DUCK -> "The duck owned the lane you forgot to lower for."
                EntityType.TIT, EntityType.CHICKADEE -> "The flock looked small until it was not."
                EntityType.OWL -> "The owl only dives when you tell it to."
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
        }

        return when (biome) {
            Biome.MEADOW -> "The meadow is gentle only if you stay gentle with it."
            Biome.ORCHARD -> "The orchard rewards rhythm more than speed."
            Biome.ANCIENT_GROVE -> "The grove asks for patience before bravery."
            Biome.DUSK_CANYON -> "Dusk shortens every decision."
            Biome.NIGHT_FOREST -> "Night keeps the score, but it also keeps the memory."
        }
    }
}
