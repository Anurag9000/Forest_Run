package com.yourname.forest_run.engine

import android.content.Context
import com.yourname.forest_run.entities.CostumeStyle
import com.yourname.forest_run.entities.EntityType

data class CostumeUnlock(
    val style: CostumeStyle,
    val line: String
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
        CostumeStyle.BLOOM_RIBBON -> "Bloom left enough light behind to wear home."
        CostumeStyle.NONE -> "Always available"
    }

    fun availableCostumes(context: Context): List<CostumeStyle> {
        val unlocked = SaveManager.loadUnlockedCostumes(context.applicationContext)
        return CostumeStyle.entries.filter { it == CostumeStyle.NONE || it in unlocked }
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
        return true
    }
}
