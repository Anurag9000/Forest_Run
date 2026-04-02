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

    fun mercyCue(
        context: Context,
        type: EntityType?,
        mercyHearts: Int,
        kindnessChain: Int,
        routeTier: PacifistRouteTier
    ): RunFlavorCue {
        val baseCue = PacifistPresentation.mercyMissCue(
            mercyHearts = mercyHearts,
            kindnessChain = kindnessChain,
            routeTier = routeTier
        )
        val appContext = context.applicationContext
        val repeatHits = type?.let { PersistentMemoryManager.getHitCount(appContext, it) } ?: 0
        val flavorText = when (type) {
            EntityType.CAT ->
                RelationshipArcSystem.encounterCueLine(appContext, EntityType.CAT, RelationshipArcSystem.EncounterCue.MERCY)
            EntityType.WOLF ->
                RelationshipArcSystem.encounterCueLine(appContext, EntityType.WOLF, RelationshipArcSystem.EncounterCue.WOLF_CHARGE)
            EntityType.DOG ->
                RelationshipArcSystem.encounterCueLine(appContext, EntityType.DOG, RelationshipArcSystem.EncounterCue.DOG_MIDDLE)
            EntityType.HEDGEHOG -> AnimalEncounterFlavor.hedgehogWarning(repeatHits)
            EntityType.DUCK -> BirdEncounterFlavor.duckAnswerPrompt()
            EntityType.TIT -> BirdEncounterFlavor.titThroughPrompt(groupSize = 5)
            EntityType.CHICKADEE -> BirdEncounterFlavor.chickadeePocketPrompt()
            EntityType.OWL ->
                RelationshipArcSystem.encounterCueLine(appContext, EntityType.OWL, RelationshipArcSystem.EncounterCue.OWL_ALERT)
            EntityType.EAGLE ->
                RelationshipArcSystem.encounterCueLine(appContext, EntityType.EAGLE, RelationshipArcSystem.EncounterCue.EAGLE_LOCK)
            EntityType.CACTUS -> if (repeatHits >= 1) "Still the careful line." else "Needles close."
            EntityType.LILY_OF_VALLEY -> if (repeatHits >= 1) "Low glow. Stay above it." else "Low glow. Careful."
            EntityType.HYACINTH -> if (repeatHits >= 1) "Keep the third beat." else "Third beat."
            EntityType.EUCALYPTUS -> if (repeatHits >= 1) "Late whip. Stay ahead." else "Watch the whip."
            EntityType.VANILLA_ORCHID -> if (repeatHits >= 1) "Thread still open." else "Find the thread."
            EntityType.WEEPING_WILLOW -> if (repeatHits >= 1) "Duck the curtain." else "Curtain low."
            EntityType.JACARANDA -> if (repeatHits >= 1) "Petals hide the lane." else "Find the lane."
            EntityType.BAMBOO -> if (repeatHits >= 1) "Seam still there." else "Thread the seam."
            EntityType.CHERRY_BLOSSOM -> if (repeatHits >= 1) "Gust band close." else "Gust close."
            EntityType.FOX -> RelationshipArcSystem.lineFor(appContext, EntityType.FOX, RelationshipArcSystem.Event.THREAT)
            null -> baseCue.flavorText
        }
        return RunFlavorCue(
            bubbleText = baseCue.bubbleText,
            flavorText = flavorText,
            fillColor = baseCue.fillColor,
            borderColor = baseCue.borderColor,
            flavorColor = baseCue.flavorColor,
            flavorSize = baseCue.flavorSize
        )
    }

    fun passCue(
        context: Context,
        type: EntityType,
        routeTier: PacifistRouteTier
    ): RunFlavorCue {
        val appContext = context.applicationContext
        val repeatHits = PersistentMemoryManager.getHitCount(appContext, type)
        val fillColor = when (routeTier) {
            PacifistRouteTier.PEACEFUL -> Color.rgb(228, 248, 234)
            PacifistRouteTier.MERCIFUL -> Color.rgb(234, 248, 220)
            PacifistRouteTier.KIND -> Color.rgb(236, 250, 222)
            PacifistRouteTier.NONE -> Color.rgb(244, 240, 226)
        }
        val borderColor = when (routeTier) {
            PacifistRouteTier.PEACEFUL -> Color.rgb(92, 146, 116)
            PacifistRouteTier.MERCIFUL -> Color.rgb(108, 154, 78)
            PacifistRouteTier.KIND -> Color.rgb(106, 158, 84)
            PacifistRouteTier.NONE -> Color.rgb(146, 128, 94)
        }
        val flavorColor = when (routeTier) {
            PacifistRouteTier.PEACEFUL -> Color.rgb(214, 255, 228)
            PacifistRouteTier.MERCIFUL -> Color.rgb(222, 255, 204)
            PacifistRouteTier.KIND -> Color.rgb(224, 255, 206)
            PacifistRouteTier.NONE -> Color.rgb(255, 232, 198)
        }
        val flavorText = when (type) {
            EntityType.CAT, EntityType.FOX, EntityType.WOLF, EntityType.DOG, EntityType.OWL, EntityType.EAGLE ->
                RelationshipArcSystem.lineFor(appContext, type, RelationshipArcSystem.Event.PASS)
            EntityType.HEDGEHOG -> AnimalEncounterFlavor.hedgehogPass(repeatHits, clearedRead = true)
            EntityType.DUCK -> BirdEncounterFlavor.duckPass(answeredQuack = true)
            EntityType.TIT -> BirdEncounterFlavor.titPass(groupSize = 5, keptBeat = true)
            EntityType.CHICKADEE -> BirdEncounterFlavor.chickadeePass(verticalSpread = 140f, readPocket = true)
            EntityType.CACTUS -> if (repeatHits >= 1) "Still clean through the needles." else "Past the needles."
            EntityType.LILY_OF_VALLEY -> "Kept above the low glow."
            EntityType.HYACINTH -> "Kept the third beat."
            EntityType.EUCALYPTUS -> "Stayed ahead of the whip."
            EntityType.VANILLA_ORCHID -> "Held the thread."
            EntityType.WEEPING_WILLOW -> "Found the curtain gap."
            EntityType.JACARANDA -> "Stayed under the bloom."
            EntityType.BAMBOO -> "Held the seam."
            EntityType.CHERRY_BLOSSOM -> "Stayed out of the gust band."
        }
        return RunFlavorCue(
            bubbleText = when (routeTier) {
                PacifistRouteTier.PEACEFUL -> "Peace kept"
                PacifistRouteTier.MERCIFUL -> "Mercy kept"
                PacifistRouteTier.KIND -> "Kindness kept"
                PacifistRouteTier.NONE -> "Clean read"
            },
            flavorText = flavorText,
            fillColor = fillColor,
            borderColor = borderColor,
            flavorColor = flavorColor,
            flavorSize = 24f
        )
    }

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
            EntityType.CHERRY_BLOSSOM -> if (repeatHits >= 1) "Same pressure band." else "Caught in the gust band."
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
        WorldOpinionPresentation.current(context.applicationContext, routeTierOverride = routeTier) != null -> {
            val opinion = requireNotNull(
                WorldOpinionPresentation.current(context.applicationContext, routeTierOverride = routeTier)
            )
            RunFlavorCue(
                bubbleText = opinion.runBubbleText,
                flavorText = opinion.runFlavorText,
                fillColor = when (opinion.label) {
                    "Shadowed" -> Color.rgb(238, 232, 246)
                    "Watchful", "Sheltering" -> Color.rgb(230, 240, 252)
                    "Softened", "Soft Home", "Trusting", "Warmed" -> Color.rgb(232, 248, 228)
                    "Stirred" -> Color.rgb(248, 236, 212)
                    else -> Color.rgb(240, 244, 228)
                },
                borderColor = when (opinion.label) {
                    "Shadowed" -> Color.rgb(118, 112, 160)
                    "Watchful", "Sheltering" -> Color.rgb(106, 132, 172)
                    "Softened", "Soft Home", "Trusting", "Warmed" -> Color.rgb(92, 146, 112)
                    "Stirred" -> Color.rgb(164, 118, 74)
                    else -> Color.rgb(128, 138, 102)
                },
                flavorColor = when (opinion.label) {
                    "Shadowed" -> Color.rgb(222, 214, 255)
                    "Watchful", "Sheltering" -> Color.rgb(214, 232, 255)
                    "Softened", "Soft Home", "Trusting", "Warmed" -> Color.rgb(214, 255, 220)
                    "Stirred" -> Color.rgb(255, 220, 182)
                    else -> Color.rgb(232, 246, 212)
                }
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
        EntityType.DUCK -> "Same quack lane."
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
        EntityType.CHERRY_BLOSSOM -> "Same gust band."
    }

    private fun shorten(text: String, maxLength: Int): String =
        if (text.length <= maxLength) text else text.take(maxLength - 1).trimEnd() + "…"
}
