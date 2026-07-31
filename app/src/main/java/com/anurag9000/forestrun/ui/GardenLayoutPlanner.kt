package com.anurag9000.forestrun.ui

import kotlin.math.ceil

data class LayoutBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    private val isValid: Boolean
        get() = left.isFinite() &&
            top.isFinite() &&
            right.isFinite() &&
            bottom.isFinite() &&
            left < right &&
            top < bottom

    fun intersects(other: LayoutBox): Boolean =
        isValid &&
            other.isValid &&
            left < other.right &&
            right > other.left &&
            top < other.bottom &&
            bottom > other.top

    fun contains(other: LayoutBox): Boolean =
        isValid &&
            other.isValid &&
            other.left >= left &&
            other.top >= top &&
            other.right <= right &&
            other.bottom <= bottom
}

data class GardenLayoutPlan(
    val runButton: LayoutBox,
    val catalogueBand: LayoutBox,
    val statsPanel: LayoutBox,
    val lastRunPanel: LayoutBox,
    val wardrobePanel: LayoutBox,
    val plantCards: List<LayoutBox>,
    val wardrobeCards: List<LayoutBox>
)

/** Landscape layout that assigns every interactive Garden surface its own band. */
object GardenLayoutPlanner {
    fun build(
        width: Float,
        height: Float,
        plantCount: Int,
        costumeCount: Int
    ): GardenLayoutPlan {
        require(width.isFinite() && width > 0f) { "Garden width must be finite and positive." }
        require(height.isFinite() && height > 0f) { "Garden height must be finite and positive." }
        require(plantCount > 0) { "Garden plant count must be positive." }
        require(costumeCount > 0) { "Garden costume count must be positive." }

        val marginX = width * 0.03f
        val catalogueBand = LayoutBox(
            left = marginX,
            top = height * 0.34f,
            right = width - marginX,
            bottom = height * 0.57f
        )
        val runButton = LayoutBox(
            left = marginX,
            top = height * 0.13f,
            right = width * 0.18f,
            bottom = height * 0.205f
        )
        val statsPanel = LayoutBox(
            left = marginX,
            top = height * 0.60f,
            right = width * 0.27f,
            bottom = height * 0.89f
        )
        val lastRunPanel = LayoutBox(
            left = width * 0.29f,
            top = height * 0.60f,
            right = width * 0.58f,
            bottom = height * 0.89f
        )
        val wardrobePanel = LayoutBox(
            left = width * 0.60f,
            top = height * 0.60f,
            right = width - marginX,
            bottom = height * 0.89f
        )

        val plantGap = width * 0.006f
        val plantCardWidth =
            (catalogueBand.width - plantGap * (plantCount - 1)) / plantCount
        require(plantCardWidth.isFinite() && plantCardWidth > 0f) {
            "Garden plant count does not fit the catalogue band."
        }
        val plantCards = List(plantCount) { index ->
            val left = catalogueBand.left + index * (plantCardWidth + plantGap)
            LayoutBox(left, catalogueBand.top, left + plantCardWidth, catalogueBand.bottom)
        }

        val columns = 4
        val rows = ceil(costumeCount / columns.toFloat()).toInt().coerceAtLeast(1)
        val innerMarginX = wardrobePanel.width * 0.035f
        val topInset = wardrobePanel.height * 0.16f
        val bottomInset = wardrobePanel.height * 0.10f
        val cardGapX = wardrobePanel.width * 0.018f
        val cardGapY = wardrobePanel.height * 0.055f
        val cardWidth = (
            wardrobePanel.width - innerMarginX * 2f - cardGapX * (columns - 1)
        ) / columns
        val cardHeight = (
            wardrobePanel.height - topInset - bottomInset - cardGapY * (rows - 1)
        ) / rows
        require(cardWidth.isFinite() && cardWidth > 0f && cardHeight.isFinite() && cardHeight > 0f) {
            "Garden costume count does not fit the wardrobe panel."
        }
        val wardrobeCards = List(costumeCount) { index ->
            val row = index / columns
            val column = index % columns
            val left = wardrobePanel.left + innerMarginX + column * (cardWidth + cardGapX)
            val top = wardrobePanel.top + topInset + row * (cardHeight + cardGapY)
            LayoutBox(left, top, left + cardWidth, top + cardHeight)
        }

        return GardenLayoutPlan(
            runButton = runButton,
            catalogueBand = catalogueBand,
            statsPanel = statsPanel,
            lastRunPanel = lastRunPanel,
            wardrobePanel = wardrobePanel,
            plantCards = plantCards,
            wardrobeCards = wardrobeCards
        )
    }
}
