package com.anurag9000.forestrun.engine

/**
 * Packs colour channels without invoking Android framework stubs.
 *
 * Catalogue and simulation models are loaded by both local JVM tests and the
 * Android runtime. Keeping channel packing in pure Kotlin prevents model
 * initialization from depending on android.graphics.Color while preserving the
 * exact signed Int representation consumed by Canvas and Paint.
 */
internal object PackedArgb {
    fun rgb(red: Int, green: Int, blue: Int): Int =
        argb(alpha = 255, red = red, green = green, blue = blue)

    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int {
        require(alpha in CHANNEL_RANGE) { "alpha must be in 0..255" }
        require(red in CHANNEL_RANGE) { "red must be in 0..255" }
        require(green in CHANNEL_RANGE) { "green must be in 0..255" }
        require(blue in CHANNEL_RANGE) { "blue must be in 0..255" }
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private val CHANNEL_RANGE = 0..255
}
