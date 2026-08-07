package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.CostumeStyle
import com.anurag9000.forestrun.ui.FeedbackSettingsPanelLayout
import com.anurag9000.forestrun.ui.GardenLayoutPlanner

/** Logical Canvas bounds for one virtual accessibility node. */
internal data class AccessibilityNodeBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite())
        require(left < right && top < bottom)
    }
}

/**
 * Keeps virtual-node geometry deterministic and tied to the same layout planners
 * used by the Canvas UI. Gameplay actions are intentionally represented by
 * generous screen regions because they are semantic controls, not hidden touch
 * hit targets.
 */
internal object GameAccessibilityGeometry {
    fun boundsFor(nodeId: Int, width: Float, height: Float): AccessibilityNodeBounds {
        require(width.isFinite() && width > 0f)
        require(height.isFinite() && height > 0f)

        return when (nodeId) {
            AccessibilityNodeIds.MENU_CONTINUE -> box(width, height, 0.28f, 0.79f, 0.72f, 0.98f)
            AccessibilityNodeIds.MENU_GARDEN -> box(width, height, 0f, 0.80f, 0.35f, 1f)
            AccessibilityNodeIds.MENU_SETTINGS -> box(width, height, 0.72f, 0.55f, 1f, 0.82f)

            AccessibilityNodeIds.SETTINGS_REDUCED_MOTION -> settingsBounds(width, height, 0)
            AccessibilityNodeIds.SETTINGS_AUDIO -> settingsBounds(width, height, 1)
            AccessibilityNodeIds.SETTINGS_HAPTICS -> settingsBounds(width, height, 2)
            AccessibilityNodeIds.SETTINGS_CLOSE -> box(width, height, 0.78f, 0.50f, 1f, 0.58f)

            AccessibilityNodeIds.RUN_STATUS -> box(width, height, 0f, 0f, 1f, 0.20f)
            AccessibilityNodeIds.RUN_JUMP -> box(width, height, 0f, 0.68f, 0.34f, 1f)
            AccessibilityNodeIds.RUN_LONG_JUMP -> box(width, height, 0.34f, 0.68f, 0.67f, 1f)
            AccessibilityNodeIds.RUN_DUCK -> box(width, height, 0.67f, 0.68f, 1f, 1f)

            AccessibilityNodeIds.GARDEN_SUMMARY -> gardenBox(width, height) { it.statsPanel }
            AccessibilityNodeIds.GARDEN_WARDROBE -> gardenBox(width, height) { it.wardrobePanel }
            AccessibilityNodeIds.GARDEN_RUN -> gardenBox(width, height) { it.runButton }
            AccessibilityNodeIds.GARDEN_HOME -> box(width, height, 0f, 0.88f, 1f, 1f)

            AccessibilityNodeIds.REST_SUMMARY -> box(width, height, 0.08f, 0.12f, 0.92f, 0.76f)
            AccessibilityNodeIds.REST_CONTINUE -> box(width, height, 0.18f, 0.76f, 0.82f, 0.98f)

            in AccessibilityNodeIds.GARDEN_FIRST_PLANT until
                AccessibilityNodeIds.GARDEN_FIRST_PLANT + GardenEconomy.catalogueSize -> {
                val index = nodeId - AccessibilityNodeIds.GARDEN_FIRST_PLANT
                gardenBox(width, height) { it.plantCards[index] }
            }

            in AccessibilityNodeIds.GARDEN_FIRST_COSTUME until
                AccessibilityNodeIds.GARDEN_FIRST_COSTUME + CostumeStyle.entries.size -> {
                val index = nodeId - AccessibilityNodeIds.GARDEN_FIRST_COSTUME
                gardenBox(width, height) { it.wardrobeCards[index] }
            }

            else -> box(width, height, 0.05f, 0.05f, 0.95f, 0.95f)
        }
    }

    private fun settingsBounds(width: Float, height: Float, index: Int): AccessibilityNodeBounds {
        val layout = FeedbackSettingsPanelLayout.build(width, height)
        val rect = layout.all[index]
        return AccessibilityNodeBounds(rect.left, rect.top, rect.right, rect.bottom)
    }

    private inline fun gardenBox(
        width: Float,
        height: Float,
        select: (com.anurag9000.forestrun.ui.GardenLayoutPlan) -> com.anurag9000.forestrun.ui.LayoutBox
    ): AccessibilityNodeBounds {
        val layout = GardenLayoutPlanner.build(
            width = width,
            height = height,
            plantCount = GardenEconomy.catalogueSize,
            costumeCount = CostumeStyle.entries.size
        )
        val box = select(layout)
        return AccessibilityNodeBounds(box.left, box.top, box.right, box.bottom)
    }

    private fun box(
        width: Float,
        height: Float,
        leftFraction: Float,
        topFraction: Float,
        rightFraction: Float,
        bottomFraction: Float
    ): AccessibilityNodeBounds = AccessibilityNodeBounds(
        left = width * leftFraction,
        top = height * topFraction,
        right = width * rightFraction,
        bottom = height * bottomFraction
    )
}
