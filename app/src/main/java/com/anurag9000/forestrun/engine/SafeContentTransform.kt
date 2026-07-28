package com.anurag9000.forestrun.engine

import kotlin.math.min

/** Physical pixels that must not contain essential UI. */
data class SafeAreaInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
)

/** A point in the game's existing full-surface logical UI coordinate system. */
data class LogicalUiPoint(val x: Float, val y: Float)

/**
 * Maps the existing full-surface UI into the largest aspect-preserving rectangle
 * inside system-bar and display-cutout insets. World rendering remains full bleed;
 * only menus, HUD, debug controls, and rest overlays use this transform.
 */
data class SafeContentTransform private constructor(
    val logicalWidth: Int,
    val logicalHeight: Int,
    val contentLeft: Float,
    val contentTop: Float,
    val contentWidth: Float,
    val contentHeight: Float,
    val scale: Float
) {
    fun toLogical(physicalX: Float, physicalY: Float): LogicalUiPoint =
        LogicalUiPoint(
            x = ((physicalX - contentLeft) / scale).coerceIn(0f, logicalWidth.toFloat()),
            y = ((physicalY - contentTop) / scale).coerceIn(0f, logicalHeight.toFloat())
        )

    fun toPhysical(logicalX: Float, logicalY: Float): LogicalUiPoint =
        LogicalUiPoint(
            x = contentLeft + logicalX.coerceIn(0f, logicalWidth.toFloat()) * scale,
            y = contentTop + logicalY.coerceIn(0f, logicalHeight.toFloat()) * scale
        )

    companion object {
        fun create(
            surfaceWidth: Int,
            surfaceHeight: Int,
            insets: SafeAreaInsets = SafeAreaInsets()
        ): SafeContentTransform {
            val width = surfaceWidth.coerceAtLeast(1)
            val height = surfaceHeight.coerceAtLeast(1)

            val left = insets.left.coerceIn(0, width - 1)
            val right = insets.right.coerceIn(0, width - left - 1)
            val top = insets.top.coerceIn(0, height - 1)
            val bottom = insets.bottom.coerceIn(0, height - top - 1)

            val availableWidth = (width - left - right).coerceAtLeast(1).toFloat()
            val availableHeight = (height - top - bottom).coerceAtLeast(1).toFloat()
            val scale = min(availableWidth / width, availableHeight / height)
                .coerceIn(1f / maxOf(width, height), 1f)
            val contentWidth = width * scale
            val contentHeight = height * scale

            return SafeContentTransform(
                logicalWidth = width,
                logicalHeight = height,
                contentLeft = left + (availableWidth - contentWidth) * 0.5f,
                contentTop = top + (availableHeight - contentHeight) * 0.5f,
                contentWidth = contentWidth,
                contentHeight = contentHeight,
                scale = scale
            )
        }
    }
}
