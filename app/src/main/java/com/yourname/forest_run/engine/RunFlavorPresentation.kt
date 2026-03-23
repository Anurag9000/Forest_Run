package com.yourname.forest_run.engine

import android.content.Context
import android.graphics.Color
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.EntityType

data class RunFlavorCue(
    val bubbleText: String,
    val flavorText: String,
    val fillColor: Int,
    val borderColor: Int,
    val flavorColor: Int,
    val flavorSize: Float = 28f
)

object RunFlavorPresentation {

    fun collisionCue(
        context: Context,
        type: EntityType?,
        result: CollisionResult,
        routeTier: PacifistRouteTier
    ): RunFlavorCue {
        val appContext = context.applicationContext
        val repeatHits = type?.let { PersistentMemoryManager.getHitCount(appContext, it) } ?: 0
        val strainedBond = type?.let { RelationshipArcSystem.isStrainedBond(appContext, it) } == true
        val isFatal = result == CollisionResult.HIT

        val fillColor = if (isFatal) {
            Color.rgb(255, 236, 224)
        } else {
            Color.rgb(255, 242, 220)
        }
        val borderColor = if (isFatal) {
            Color.rgb(168, 88, 76)
        } else {
            Color.rgb(170, 122, 62)
        }
        val flavorColor = if (isFatal) {
            Color.rgb(255, 210, 202)
        } else {
            Color.rgb(255, 230, 188)
        }

        if (type != null && repeatHits >= 2) {
            return RunFlavorCue(
                bubbleText = "Again?",
                flavorText = repeatKillerFlavor(type),
                fillColor = fillColor,
                borderColor = borderColor,
                flavorColor = flavorColor,
                flavorSize = 30f
            )
        }

        if (type != null && strainedBond) {
            val strained = RelationshipArcSystem.strainedBondLine(appContext, type)
            return RunFlavorCue(
                bubbleText = "Careful.",
                flavorText = shorten(strained, 30),
                fillColor = fillColor,
                borderColor = borderColor,
                flavorColor = flavorColor,
                flavorSize = 24f
            )
        }

        if (type != null && RelationshipArcSystem.isTracked(type)) {
            return RunFlavorCue(
                bubbleText = if (isFatal) "Not that line." else "Too close.",
                flavorText = RelationshipArcSystem.lineFor(appContext, type, RelationshipArcSystem.Event.THREAT),
                fillColor = fillColor,
                borderColor = borderColor,
                flavorColor = flavorColor
            )
        }

        val defaultFlavor = when (type) {
            EntityType.HEDGEHOG -> AnimalEncounterFlavor.hedgehogHit(repeatHits)
            EntityType.DUCK -> BirdEncounterFlavor.duckHit(repeatHits)
            EntityType.TIT -> BirdEncounterFlavor.titHit(repeatHits)
            EntityType.CHICKADEE -> BirdEncounterFlavor.chickadeeHit(repeatHits)
            EntityType.CACTUS -> if (repeatHits >= 1) "Still the rigid line." else "Sharp read missed."
            EntityType.LILY_OF_VALLEY -> if (repeatHits >= 1) "Same low lure." else "Caught the glow."
            EntityType.HYACINTH -> if (repeatHits >= 1) "Lost the third beat again." else "Lost the third beat."
            EntityType.EUCALYPTUS -> if (repeatHits >= 1) "Same late whip." else "Late on the whip."
            EntityType.VANILLA_ORCHID -> if (repeatHits >= 1) "Same closed thread." else "Lost the thread."
            EntityType.WEEPING_WILLOW -> if (repeatHits >= 1) "Lost the lane again." else "Caught in the curtain."
            EntityType.JACARANDA -> if (repeatHits >= 1) "Same bloom curtain." else "Lost under petals."
            EntityType.BAMBOO -> if (repeatHits >= 1) "Missed the seam again." else "Missed the seam."
            EntityType.CHERRY_BLOSSOM -> if (repeatHits >= 1) "Same gust line." else "Caught in the gust."
            null -> if (isFatal) "The path answered back." else "The path caught you."
            else -> if (isFatal) "Again." else "Too close."
        }

        val routeBubble = when (routeTier) {
            PacifistRouteTier.PEACEFUL -> "Peace shaken"
            PacifistRouteTier.MERCIFUL -> "Mercy shaken"
            PacifistRouteTier.KIND -> "Kindness shaken"
            PacifistRouteTier.NONE -> if (isFatal) "Again?" else "Careful."
        }

        return RunFlavorCue(
            bubbleText = routeBubble,
            flavorText = defaultFlavor,
            fillColor = fillColor,
            borderColor = borderColor,
            flavorColor = flavorColor,
            flavorSize = 24f
        )
    }

    fun milestoneCue(
        context: Context,
        score: Int,
        routeTier: PacifistRouteTier,
        isNewHighScore: Boolean
    ): RunFlavorCue = when {
        isNewHighScore -> RunFlavorCue(
            bubbleText = "New best",
            flavorText = "The forest noticed",
            fillColor = Color.rgb(246, 238, 216),
            borderColor = Color.rgb(158, 122, 58),
            flavorColor = Color.rgb(255, 226, 172)
        )
        RelationshipArcSystem.featuredMilestoneReward(context.applicationContext) != null -> {
            val reward = requireNotNull(RelationshipArcSystem.featuredMilestoneReward(context.applicationContext))
            RunFlavorCue(
                bubbleText = reward.milestoneBubbleText,
                flavorText = reward.milestoneFlavorText,
                fillColor = milestoneFillColor(reward.type),
                borderColor = milestoneBorderColor(reward.type),
                flavorColor = milestoneFlavorColor(reward.type)
            )
        }
        routeTier == PacifistRouteTier.PEACEFUL -> RunFlavorCue(
            bubbleText = "Peace held",
            flavorText = "Calm carries",
            fillColor = Color.rgb(228, 248, 234),
            borderColor = Color.rgb(92, 146, 116),
            flavorColor = Color.rgb(214, 255, 228)
        )
        routeTier == PacifistRouteTier.MERCIFUL -> RunFlavorCue(
            bubbleText = "Mercy climbs",
            flavorText = "Mercy remembered",
            fillColor = Color.rgb(234, 248, 220),
            borderColor = Color.rgb(108, 154, 78),
            flavorColor = Color.rgb(222, 255, 204)
        )
        routeTier == PacifistRouteTier.KIND -> RunFlavorCue(
            bubbleText = "Kindness climbs",
            flavorText = "Gentle streak",
            fillColor = Color.rgb(236, 250, 222),
            borderColor = Color.rgb(106, 158, 84),
            flavorColor = Color.rgb(224, 255, 206)
        )
        score >= 3_000 -> RunFlavorCue(
            bubbleText = "Still going",
            flavorText = "Deep run",
            fillColor = Color.rgb(244, 240, 226),
            borderColor = Color.rgb(146, 128, 94),
            flavorColor = Color.rgb(255, 232, 198)
        )
        else -> RunFlavorCue(
            bubbleText = "Milestone",
            flavorText = "Keep going",
            fillColor = Color.rgb(244, 240, 226),
            borderColor = Color.rgb(146, 128, 94),
            flavorColor = Color.rgb(255, 232, 198),
            flavorSize = 24f
        )
    }

    private fun milestoneFillColor(type: EntityType): Int = when (type) {
        EntityType.CAT -> Color.rgb(250, 232, 240)
        EntityType.FOX -> Color.rgb(252, 230, 204)
        EntityType.WOLF -> Color.rgb(228, 236, 246)
        EntityType.DOG -> Color.rgb(250, 240, 198)
        EntityType.OWL -> Color.rgb(234, 232, 252)
        EntityType.EAGLE -> Color.rgb(228, 240, 252)
        else -> Color.rgb(244, 240, 226)
    }

    private fun milestoneBorderColor(type: EntityType): Int = when (type) {
        EntityType.CAT -> Color.rgb(168, 112, 136)
        EntityType.FOX -> Color.rgb(176, 120, 72)
        EntityType.WOLF -> Color.rgb(112, 128, 154)
        EntityType.DOG -> Color.rgb(170, 142, 58)
        EntityType.OWL -> Color.rgb(122, 122, 178)
        EntityType.EAGLE -> Color.rgb(104, 138, 178)
        else -> Color.rgb(146, 128, 94)
    }

    private fun milestoneFlavorColor(type: EntityType): Int = when (type) {
        EntityType.CAT -> Color.rgb(255, 220, 232)
        EntityType.FOX -> Color.rgb(255, 220, 176)
        EntityType.WOLF -> Color.rgb(214, 228, 248)
        EntityType.DOG -> Color.rgb(255, 232, 170)
        EntityType.OWL -> Color.rgb(220, 224, 255)
        EntityType.EAGLE -> Color.rgb(208, 232, 255)
        else -> Color.rgb(255, 232, 198)
    }

    private fun repeatKillerFlavor(type: EntityType): String = when (type) {
        EntityType.CAT -> "Same paw line."
        EntityType.FOX -> "Same sly feint."
        EntityType.WOLF -> "Same howl."
        EntityType.DOG -> "Same bark line."
        EntityType.HEDGEHOG -> "Same low thorns."
        EntityType.DUCK -> "Same low lane."
        EntityType.TIT -> "Same broken rhythm."
        EntityType.CHICKADEE -> "Same flutter rush."
        EntityType.OWL -> "Same shadow."
        EntityType.EAGLE -> "Same mark."
        EntityType.CACTUS -> "Same rigid line."
        EntityType.LILY_OF_VALLEY -> "Same low lure."
        EntityType.HYACINTH -> "Same beat slip."
        EntityType.EUCALYPTUS -> "Same whip line."
        EntityType.VANILLA_ORCHID -> "Same thread closed."
        EntityType.WEEPING_WILLOW -> "Same willow curtain."
        EntityType.JACARANDA -> "Same bloom curtain."
        EntityType.BAMBOO -> "Same seam missed."
        EntityType.CHERRY_BLOSSOM -> "Same gust line."
    }

    private fun shorten(text: String, maxLength: Int): String =
        if (text.length <= maxLength) text else text.take(maxLength - 1).trimEnd() + "…"
}
