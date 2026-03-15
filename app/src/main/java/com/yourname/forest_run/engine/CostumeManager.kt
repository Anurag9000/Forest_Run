package com.yourname.forest_run.engine

import android.content.Context
import com.yourname.forest_run.entities.CostumeStyle
import com.yourname.forest_run.entities.EntityType

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

    fun refreshUnlocks(context: Context): List<CostumeStyle> {
        val appContext = context.applicationContext
        val unlocked = SaveManager.loadUnlockedCostumes(appContext).toMutableSet()
        val newlyUnlocked = mutableListOf<CostumeStyle>()
        for ((style, requirement) in unlockRules) {
            if (style !in unlocked && requirement(appContext)) {
                unlocked += style
                newlyUnlocked += style
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
