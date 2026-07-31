package com.anurag9000.forestrun.engine

object SpriteSizing {
    private const val DEFAULT_MIN_SIZE_PX = 1f

    fun widthForHeight(sprite: SpriteSheet, height: Float, minWidth: Float = DEFAULT_MIN_SIZE_PX): Float {
        val safeMinimum = positiveFiniteMinimum(minWidth)
        val safeHeight = height.takeIf { it.isFinite() && it > 0f } ?: return safeMinimum
        val aspectRatio = sprite.aspectRatio.takeIf { it.isFinite() && it > 0f } ?: return safeMinimum
        return finiteSizeOrMinimum(
            value = safeHeight.toDouble() * aspectRatio.toDouble(),
            minimum = safeMinimum
        )
    }

    fun heightForWidth(sprite: SpriteSheet, width: Float, minHeight: Float = DEFAULT_MIN_SIZE_PX): Float {
        val safeMinimum = positiveFiniteMinimum(minHeight)
        val safeWidth = width.takeIf { it.isFinite() && it > 0f } ?: return safeMinimum
        val aspectRatio = sprite.aspectRatio.takeIf { it.isFinite() && it > 0f } ?: return safeMinimum
        return finiteSizeOrMinimum(
            value = safeWidth.toDouble() / aspectRatio.toDouble(),
            minimum = safeMinimum
        )
    }

    private fun positiveFiniteMinimum(value: Float): Float =
        value.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_MIN_SIZE_PX

    private fun finiteSizeOrMinimum(value: Double, minimum: Float): Float {
        if (!value.isFinite() || value <= 0.0 || value > Float.MAX_VALUE.toDouble()) return minimum
        return value.toFloat().coerceAtLeast(minimum)
    }
}
