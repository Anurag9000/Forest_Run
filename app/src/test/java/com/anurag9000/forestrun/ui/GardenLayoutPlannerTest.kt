package com.anurag9000.forestrun.ui

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

    @Test
    fun `non finite or non positive surface dimensions are rejected`() {
        val malformedDimensions = listOf(
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            0f,
            -1f
        )

        malformedDimensions.forEach { malformed ->
            assertRejected {
                GardenLayoutPlanner.build(
                    width = malformed,
                    height = 1080f,
                    plantCount = 9,
                    costumeCount = 8
                )
            }
            assertRejected {
                GardenLayoutPlanner.build(
                    width = 1920f,
                    height = malformed,
                    plantCount = 9,
                    costumeCount = 8
                )
            }
        }
    }

    @Test
    fun `non positive catalogue counts are rejected`() {
        listOf(0, -1, Int.MIN_VALUE).forEach { malformedCount ->
            assertRejected {
                GardenLayoutPlanner.build(
                    width = 1920f,
                    height = 1080f,
                    plantCount = malformedCount,
                    costumeCount = 8
                )
            }
            assertRejected {
                GardenLayoutPlanner.build(
                    width = 1920f,
                    height = 1080f,
                    plantCount = 9,
                    costumeCount = malformedCount
                )
            }
        }
    }

    @Test
    fun `excessive catalogue counts fail before list allocation`() {
        assertRejected {
            GardenLayoutPlanner.build(
                width = 1920f,
                height = 1080f,
                plantCount = Int.MAX_VALUE,
                costumeCount = 8
            )
        }
        assertRejected {
            GardenLayoutPlanner.build(
                width = 1920f,
                height = 1080f,
                plantCount = 9,
                costumeCount = Int.MAX_VALUE
            )
        }
    }

    @Test
    fun `malformed layout boxes fail closed`() {
        val valid = LayoutBox(0f, 0f, 100f, 100f)
        val malformed = listOf(
            LayoutBox(Float.NaN, 0f, 10f, 10f),
            LayoutBox(20f, 0f, 10f, 10f),
            LayoutBox(0f, 10f, 10f, 10f),
            LayoutBox(0f, Float.POSITIVE_INFINITY, 10f, 20f)
        )

        malformed.forEach { box ->
            assertFalse(valid.intersects(box))
            assertFalse(box.intersects(valid))
            assertFalse(valid.contains(box))
            assertFalse(box.contains(valid))
        }
    }

    private fun assertRejected(block: () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Expected malformed Garden layout input to be rejected.", rejected)
    }
}
