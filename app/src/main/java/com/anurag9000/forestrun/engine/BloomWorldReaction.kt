package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.EntityType

enum class BloomReactionFamily {
    FLORA,
    TREE,
    BIRD,
    ANIMAL
}

data class BloomWorldReactionCue(
    val family: BloomReactionFamily,
    val text: String
)

object BloomWorldReaction {

    fun cueFor(type: EntityType): BloomWorldReactionCue = when (type) {
        EntityType.CACTUS -> BloomWorldReactionCue(BloomReactionFamily.FLORA, "The needles bloom first.")
        EntityType.LILY_OF_VALLEY -> BloomWorldReactionCue(BloomReactionFamily.FLORA, "The glow leans open.")
        EntityType.HYACINTH -> BloomWorldReactionCue(BloomReactionFamily.FLORA, "The rhythm brightens.")
        EntityType.EUCALYPTUS -> BloomWorldReactionCue(BloomReactionFamily.FLORA, "Even the gust turns warm.")
        EntityType.VANILLA_ORCHID -> BloomWorldReactionCue(BloomReactionFamily.FLORA, "The thread lights first.")
        EntityType.WEEPING_WILLOW -> BloomWorldReactionCue(BloomReactionFamily.TREE, "The curtain lifts.")
        EntityType.JACARANDA -> BloomWorldReactionCue(BloomReactionFamily.TREE, "Petals wake early.")
        EntityType.BAMBOO -> BloomWorldReactionCue(BloomReactionFamily.TREE, "The seam lights up.")
        EntityType.CHERRY_BLOSSOM -> BloomWorldReactionCue(BloomReactionFamily.TREE, "The gust loosens.")
        EntityType.DUCK -> BloomWorldReactionCue(BloomReactionFamily.BIRD, "Even the lane answers.")
        EntityType.TIT -> BloomWorldReactionCue(BloomReactionFamily.BIRD, "The beat opens.")
        EntityType.CHICKADEE -> BloomWorldReactionCue(BloomReactionFamily.BIRD, "Flutter turns bright.")
        EntityType.OWL -> BloomWorldReactionCue(BloomReactionFamily.BIRD, "The dark makes room.")
        EntityType.EAGLE -> BloomWorldReactionCue(BloomReactionFamily.BIRD, "The mark softens.")
        EntityType.CAT -> BloomWorldReactionCue(BloomReactionFamily.ANIMAL, "The path knows you.")
        EntityType.WOLF -> BloomWorldReactionCue(BloomReactionFamily.ANIMAL, "The charge loosens.")
        EntityType.FOX -> BloomWorldReactionCue(BloomReactionFamily.ANIMAL, "The trick opens first.")
        EntityType.HEDGEHOG -> BloomWorldReactionCue(BloomReactionFamily.ANIMAL, "The thorns give way.")
        EntityType.DOG -> BloomWorldReactionCue(BloomReactionFamily.ANIMAL, "The lane runs with you.")
    }

    fun shouldReact(
        playerCenterX: Float,
        playerCenterY: Float,
        entityCenterX: Float,
        entityCenterY: Float,
        alreadyReacted: Boolean
    ): Boolean {
        if (alreadyReacted) return false
        val forwardDistance = entityCenterX - playerCenterX
        val verticalOffset = kotlin.math.abs(entityCenterY - playerCenterY)
        return forwardDistance in 42f..430f && verticalOffset <= 250f
    }
}
