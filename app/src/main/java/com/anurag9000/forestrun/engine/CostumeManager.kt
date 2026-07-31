package com.anurag9000.forestrun.engine

import android.content.Context
import com.anurag9000.forestrun.entities.CostumeStyle
import com.anurag9000.forestrun.entities.EntityType

data class CostumeUnlock(
    val style: CostumeStyle,
    val line: String
)

data class CostumePresentationState(
    val style: CostumeStyle,
    val signLabel: String,
    val signLine: String,
    val activeLabel: String,
    val activeLine: String
)

object CostumeManager {

    private val unlockRules = listOf(
        CostumeStyle.FLOWER_CROWN to { context: Context ->
            PersistentMemoryManager.getSparedCount(context, EntityType.CAT) >= 3 ||
                RelationshipArcSystem.hasUnlockedMilestone(context, EntityType.CAT)
        },
        CostumeStyle.VINE_SCARF to { context: Context ->
            PersistentMemoryManager.getSparedCount(context, EntityType.FOX) >= 3 ||
                RelationshipArcSystem.hasUnlockedMilestone(context, EntityType.FOX)
        },
        CostumeStyle.MOON_CAPE to { context: Context ->
            PersistentMemoryManager.getSparedCount(context, EntityType.WOLF) >= 2 ||
                RelationshipArcSystem.hasUnlockedMilestone(context, EntityType.WOLF)
        },
        CostumeStyle.BELL_CHARM to { context: Context ->
            RelationshipArcSystem.hasUnlockedMilestone(context, EntityType.DOG)
        },
        CostumeStyle.LANTERN_PIN to { context: Context ->
            RelationshipArcSystem.hasUnlockedMilestone(context, EntityType.OWL)
        },
        CostumeStyle.SKY_SASH to { context: Context ->
            RelationshipArcSystem.hasUnlockedMilestone(context, EntityType.EAGLE)
        },
        CostumeStyle.BLOOM_RIBBON to { context: Context ->
            SaveManager.loadBestDistance(context) >= 1_500f ||
                SaveManager.loadLifetimeSeeds(context) >= 120
        }
    )

    fun refreshUnlocks(context: Context): List<CostumeUnlock> {
        val appContext = context.applicationContext
        val unlocked = SaveManager.loadUnlockedCostumes(appContext).toMutableSet()
        val newlyUnlocked = mutableListOf<CostumeUnlock>()
        for ((style, requirement) in unlockRules) {
            if (style !in unlocked && requirement(appContext)) {
                unlocked += style
                newlyUnlocked += CostumeUnlock(style, unlockLineFor(appContext, style))
            }
        }
        if (newlyUnlocked.isNotEmpty()) {
            SaveManager.saveUnlockedCostumes(appContext, unlocked)
            SaveManager.saveFeaturedCostume(appContext, newlyUnlocked.last().style)
        }
        val active = SaveManager.loadActiveCostume(appContext)
        if (active != CostumeStyle.NONE && active !in unlocked) {
            SaveManager.saveActiveCostume(appContext, CostumeStyle.NONE)
        }
        return newlyUnlocked
    }

    private fun unlockLineFor(context: Context, style: CostumeStyle): String = when (style) {
        CostumeStyle.FLOWER_CROWN -> if (RelationshipArcSystem.hasUnlockedMilestone(context, EntityType.CAT)) {
            "The cat finally left something gentle enough to wear."
        } else {
            "The flower crown keeps the quieter kindness you showed the cat."
        }
        CostumeStyle.VINE_SCARF -> if (RelationshipArcSystem.hasUnlockedMilestone(context, EntityType.FOX)) {
            "The fox left a brighter answer on the trail for you."
        } else {
            "The vine scarf remembers the fox runs you answered gently."
        }
        CostumeStyle.MOON_CAPE -> if (RelationshipArcSystem.hasUnlockedMilestone(context, EntityType.WOLF)) {
            "The wolf's respect has started looking like something you can carry."
        } else {
            "The moon cape keeps the calmer courage you earned from the wolf."
        }
        CostumeStyle.BELL_CHARM -> "The dog's welcome now comes with something bright enough to carry back into the run."
        CostumeStyle.LANTERN_PIN -> "The owl left a steadier light behind than the dark edge used to allow."
        CostumeStyle.SKY_SASH -> "The eagle's line through the sky finally feels like something you can wear without fear."
        CostumeStyle.BLOOM_RIBBON -> "Bloom left enough light behind to wear home."
        CostumeStyle.NONE -> "Always available"
    }

    fun availableCostumes(context: Context): List<CostumeStyle> {
        val unlocked = SaveManager.loadUnlockedCostumes(context.applicationContext)
        return CostumeStyle.entries.filter { it == CostumeStyle.NONE || it in unlocked }
    }

    fun featuredPresentation(context: Context): CostumePresentationState? {
        val appContext = context.applicationContext
        val unlocked = SaveManager.loadUnlockedCostumes(appContext)
        val featured = SaveManager.loadFeaturedCostume(appContext)?.takeIf { it in unlocked }
            ?: activeCostume(appContext).takeIf { it != CostumeStyle.NONE }
            ?: unlocked.maxByOrNull { it.ordinal }
        return presentationFor(featured ?: return null)
    }

    fun activePresentation(context: Context): CostumePresentationState? {
        val active = activeCostume(context.applicationContext)
        return presentationFor(active.takeIf { it != CostumeStyle.NONE } ?: return null)
    }

    fun activeCostume(context: Context): CostumeStyle {
        val appContext = context.applicationContext
        val active = SaveManager.loadActiveCostume(appContext)
        val unlocked = SaveManager.loadUnlockedCostumes(appContext)
        return if (active == CostumeStyle.NONE || active in unlocked) active else CostumeStyle.NONE
    }

    fun equip(context: Context, style: CostumeStyle): Boolean {
        val appContext = context.applicationContext
        val unlocked = SaveManager.loadUnlockedCostumes(appContext)
        if (style != CostumeStyle.NONE && style !in unlocked) return false
        SaveManager.saveActiveCostume(appContext, style)
        if (style != CostumeStyle.NONE) {
            SaveManager.saveFeaturedCostume(appContext, style)
        }
        return true
    }

    private fun presentationFor(style: CostumeStyle): CostumePresentationState = when (style) {
        CostumeStyle.FLOWER_CROWN -> CostumePresentationState(
            style = style,
            signLabel = "Worn Quiet",
            signLine = "The flower crown keeps the cat's gentler pause close enough that home starts from quiet instead of noise.",
            activeLabel = "Flower Crown",
            activeLine = "You are still wearing the cat's gentler trust into the next start."
        )
        CostumeStyle.VINE_SCARF -> CostumePresentationState(
            style = style,
            signLabel = "Answered Trail",
            signLine = "The vine scarf leaves a brighter trail through home, like the fox still expects your gentler answer.",
            activeLabel = "Vine Scarf",
            activeLine = "You are still carrying the fox's brighter answer into the next run."
        )
        CostumeStyle.MOON_CAPE -> CostumePresentationState(
            style = style,
            signLabel = "Earned Calm",
            signLine = "The moon cape makes home feel calmer and more deliberate, as if the wolf's respect is still standing nearby.",
            activeLabel = "Moon Cape",
            activeLine = "You are still wearing the wolf's hard-won calm into the next opening."
        )
        CostumeStyle.BELL_CHARM -> CostumePresentationState(
            style = style,
            signLabel = "Glad Welcome",
            signLine = "The bell charm keeps a brighter welcome at home, like the dog already expects to meet you halfway again.",
            activeLabel = "Bell Charm",
            activeLine = "You are still carrying the dog's glad welcome into the next run."
        )
        CostumeStyle.LANTERN_PIN -> CostumePresentationState(
            style = style,
            signLabel = "Carried Light",
            signLine = "The lantern pin keeps a steadier light at home, like the owl left something watchful behind for you on purpose.",
            activeLabel = "Lantern Pin",
            activeLine = "You are still wearing the owl's steadier light into the next dark edge."
        )
        CostumeStyle.SKY_SASH -> CostumePresentationState(
            style = style,
            signLabel = "Held Horizon",
            signLine = "The sky sash keeps the horizon clearer at home, like the eagle's line is still holding open above you.",
            activeLabel = "Sky Sash",
            activeLine = "You are still carrying the eagle's held line into the next start."
        )
        CostumeStyle.BLOOM_RIBBON -> CostumePresentationState(
            style = style,
            signLabel = "Bloom Afterglow",
            signLine = "The Bloom ribbon keeps a warmer afterglow at home, like some of that light decided not to leave with the run.",
            activeLabel = "Bloom Ribbon",
            activeLine = "You are still wearing a little of Bloom's afterglow into the next path."
        )
        CostumeStyle.NONE -> CostumePresentationState(
            style = style,
            signLabel = "",
            signLine = "",
            activeLabel = "",
            activeLine = ""
        )
    }
}
