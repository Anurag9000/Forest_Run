package com.yourname.forest_run.engine

object SpriteSizing {
    fun widthForHeight(sprite: SpriteSheet, height: Float, minWidth: Float = 1f): Float {
        return (height * sprite.aspectRatio).coerceAtLeast(minWidth)
    }

    fun heightForWidth(sprite: SpriteSheet, width: Float, minHeight: Float = 1f): Float {
        return (width / sprite.aspectRatio.coerceAtLeast(0.01f)).coerceAtLeast(minHeight)
    }
}
