package com.yourname.forest_run.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GardenLayoutPlannerTest {
    @Test
    fun `major Garden surfaces do not overlap across landscape sizes`() {
        listOf(
            1280f to 720f,
            1920f to 1080f,
            2560f to 1440f
        ).forEach { (width, height) ->
            val plan = GardenLayoutPlanner.build(width, height, plantCount = 9, costumeCount = 8)
            val major = listOf(
                plan.runButton,
                plan.catalogueBand,
                plan.statsPanel,
                plan.lastRunPanel,
                plan.wardrobePanel
            )

            major.forEachIndexed { index, box ->
                assertTrue(box.width > 0f)
                assertTrue(box.height > 0f)
                for (otherIndex in index + 1 until major.size) {
                    assertFalse(
                        "Regions $index and $otherIndex overlap at ${width.toInt()}x${height.toInt()}",
                        box.intersects(major[otherIndex])
                    )
                }
            }
        }
    }

    @Test
    fun `all plant cards remain inside the catalogue without overlapping`() {
        val plan = GardenLayoutPlanner.build(1920f, 1080f, plantCount = 9, costumeCount = 8)

        plan.plantCards.forEachIndexed { index, card ->
            assertTrue(plan.catalogueBand.contains(card))
            assertTrue(card.width > 0f)
            assertTrue(card.height > 0f)
            for (otherIndex in index + 1 until plan.plantCards.size) {
                assertFalse(card.intersects(plan.plantCards[otherIndex]))
            }
        }
    }

    @Test
    fun `all wardrobe cards remain inside their panel without overlapping`() {
        val plan = GardenLayoutPlanner.build(1280f, 720f, plantCount = 9, costumeCount = 8)

        plan.wardrobeCards.forEachIndexed { index, card ->
            assertTrue(plan.wardrobePanel.contains(card))
            assertTrue(card.width > 0f)
            assertTrue(card.height > 0f)
            for (otherIndex in index + 1 until plan.wardrobeCards.size) {
                assertFalse(card.intersects(plan.wardrobeCards[otherIndex]))
            }
        }
    }
}
