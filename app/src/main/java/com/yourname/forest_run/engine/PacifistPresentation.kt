package com.yourname.forest_run.engine

import android.content.Context
import android.graphics.Color

enum class PacifistRewardKind {
    CLEAN_STREAK,
    SPARE_STREAK,
    BIOME_FRIENDSHIP,
    ROUTE_KIND,
    ROUTE_MERCIFUL,
    ROUTE_PEACEFUL
}

data class PacifistCue(
    val bubbleText: String,
    val flavorText: String,
    val fillColor: Int,
    val borderColor: Int,
    val flavorColor: Int,
    val flavorSize: Float = 28f
)

data class RouteWorldState(
    val label: String,
    val line: String
)

object PacifistPresentation {

    fun routeWorldState(
        context: Context,
        latestRouteTier: PacifistRouteTier
    ): RouteWorldState? {
        val appContext = context.applicationContext
        val kindRuns = SaveManager.loadRouteTierCount(appContext, PacifistRouteTier.KIND)
        val mercifulRuns = SaveManager.loadRouteTierCount(appContext, PacifistRouteTier.MERCIFUL)
        val peacefulRuns = SaveManager.loadRouteTierCount(appContext, PacifistRouteTier.PEACEFUL)
        val cumulativeKind = kindRuns + mercifulRuns + peacefulRuns
        val cumulativeMerciful = mercifulRuns + peacefulRuns
        return when {
            peacefulRuns >= 2 || (latestRouteTier == PacifistRouteTier.PEACEFUL && cumulativeMerciful >= 2) ->
                RouteWorldState(
                    label = "Peace Remembered",
                    line = "The world has started treating peace as something it expects from you, not a one-run accident."
                )
            cumulativeMerciful >= 3 || (latestRouteTier == PacifistRouteTier.MERCIFUL && cumulativeKind >= 3) ->
                RouteWorldState(
                    label = "Mercy Remembered",
                    line = "The world has started meeting you with softer edges because mercy keeps making it home."
                )
            cumulativeKind >= 4 || (latestRouteTier == PacifistRouteTier.KIND && cumulativeKind >= 2) ->
                RouteWorldState(
                    label = "Gentler Paths",
                    line = "The world has started assuming you might choose the gentler answer again instead of bracing for the harsh one."
                )
            else -> null
        }
    }

    fun mercyMissCue(
        mercyHearts: Int,
        kindnessChain: Int,
        routeTier: PacifistRouteTier
    ): PacifistCue = when {
        routeTier == PacifistRouteTier.PEACEFUL -> PacifistCue(
            bubbleText = "Peace kept",
            flavorText = "Peace held",
            fillColor = Color.rgb(228, 250, 236),
            borderColor = Color.rgb(92, 150, 120),
            flavorColor = Color.rgb(210, 255, 228)
        )
        routeTier == PacifistRouteTier.MERCIFUL -> PacifistCue(
            bubbleText = "Mercy kept",
            flavorText = "Mercy answered",
            fillColor = Color.rgb(232, 248, 220),
            borderColor = Color.rgb(108, 156, 76),
            flavorColor = Color.rgb(226, 255, 198)
        )
        mercyHearts >= 8 -> PacifistCue(
            bubbleText = "Spare close",
            flavorText = "Mercy is close",
            fillColor = Color.rgb(242, 248, 214),
            borderColor = Color.rgb(150, 162, 80),
            flavorColor = Color.rgb(246, 255, 196)
        )
        kindnessChain >= 7 || routeTier == PacifistRouteTier.KIND -> PacifistCue(
            bubbleText = "Kindness held",
            flavorText = "Kindness stayed",
            fillColor = Color.rgb(234, 248, 220),
            borderColor = Color.rgb(106, 160, 86),
            flavorColor = Color.rgb(216, 255, 202)
        )
        mercyHearts >= 3 -> PacifistCue(
            bubbleText = "Mercy noticed",
            flavorText = "Mercy noticed",
            fillColor = Color.rgb(236, 246, 220),
            borderColor = Color.rgb(112, 154, 82),
            flavorColor = Color.rgb(220, 255, 206)
        )
        else -> PacifistCue(
            bubbleText = "Close, gently",
            flavorText = "Close call",
            fillColor = Color.rgb(238, 245, 224),
            borderColor = Color.rgb(116, 150, 88),
            flavorColor = Color.rgb(224, 255, 210),
            flavorSize = 24f
        )
    }

    fun rewardCue(reward: PacifistReward): PacifistCue = when (reward.kind) {
        PacifistRewardKind.CLEAN_STREAK -> PacifistCue(
            bubbleText = "Kindness carries",
            flavorText = "Gentle streak",
            fillColor = Color.rgb(236, 250, 220),
            borderColor = Color.rgb(104, 158, 82),
            flavorColor = Color.rgb(224, 255, 208)
        )
        PacifistRewardKind.SPARE_STREAK -> PacifistCue(
            bubbleText = "Mercy kept",
            flavorText = "Spare remembered",
            fillColor = Color.rgb(238, 248, 220),
            borderColor = Color.rgb(112, 160, 90),
            flavorColor = Color.rgb(226, 255, 206)
        )
        PacifistRewardKind.BIOME_FRIENDSHIP -> {
            val biomeName = reward.friendBiome?.name
                ?.lowercase()
                ?.replace('_', ' ')
                ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                ?: "Biome"
            PacifistCue(
                bubbleText = "$biomeName at peace",
                flavorText = "$biomeName softened",
                fillColor = Color.rgb(232, 246, 228),
                borderColor = Color.rgb(94, 142, 104),
                flavorColor = Color.rgb(214, 248, 214)
            )
        }
        PacifistRewardKind.ROUTE_KIND -> PacifistCue(
            bubbleText = "Kind route",
            flavorText = "Kindness noticed",
            fillColor = Color.rgb(236, 248, 220),
            borderColor = Color.rgb(108, 156, 78),
            flavorColor = Color.rgb(224, 255, 206)
        )
        PacifistRewardKind.ROUTE_MERCIFUL -> PacifistCue(
            bubbleText = "Merciful route",
            flavorText = "Mercy carried",
            fillColor = Color.rgb(234, 246, 220),
            borderColor = Color.rgb(104, 150, 78),
            flavorColor = Color.rgb(220, 255, 202)
        )
        PacifistRewardKind.ROUTE_PEACEFUL -> PacifistCue(
            bubbleText = "Peaceful route",
            flavorText = "Forest at peace",
            fillColor = Color.rgb(228, 248, 232),
            borderColor = Color.rgb(88, 146, 112),
            flavorColor = Color.rgb(210, 255, 226)
        )
    }

    fun routeAfterglowLine(routeTier: PacifistRouteTier): String = when (routeTier) {
        PacifistRouteTier.KIND ->
            "The run kept leaning toward the gentler answer."
        PacifistRouteTier.MERCIFUL ->
            "Mercy kept making room in front of you instead of forcing the path shut."
        PacifistRouteTier.PEACEFUL ->
            "Even home feels quieter after a run that stayed peaceful all the way through."
        PacifistRouteTier.NONE ->
            ""
    }
}
