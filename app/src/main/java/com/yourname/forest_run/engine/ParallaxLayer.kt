package com.yourname.forest_run.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/**
 * A single horizontally-scrolling background layer.
 *
 * The bitmap is drawn twice side-by-side ([x] and [x + bitmap.width]) so
 * the seam is never visible.  When the left copy fully exits the screen
 * the X position is snapped forward by one bitmap width, keeping the loop
 * seamless forever.
 *
 * @param bitmap        The pre-loaded, pre-scaled layer image.
 * @param speedFraction Fraction of the game's scroll speed this layer moves at
 *                      (0.1 = far background, 1.0 = ground layer, 1.5 = near foreground).
 */
class ParallaxLayer(
    val bitmap: Bitmap,
    private val speedFraction: Float
) {
    /** Current left-edge position of the layer. */
    var x: Float = 0f

    private val paint = Paint().apply { isFilterBitmap = false }   // pixel-art: no filtering

    /**
     * Advance the layer.
     * @param deltaTime      Seconds since last frame.
     * @param gameScrollSpeed Current game scroll speed in pixels per second.
     */
    fun update(deltaTime: Float, gameScrollSpeed: Float) {
        x -= speedFraction * gameScrollSpeed * deltaTime

        // Seamless wrap: once the left copy is fully off-screen, snap forward
        if (x <= -bitmap.width.toFloat()) {
            x += bitmap.width.toFloat()
        }
    }

    /**
     * Draw the layer (two copies: the current one and the one directly to its right).
     */
    fun draw(canvas: Canvas) {
        canvas.drawBitmap(bitmap, x, 0f, paint)
        canvas.drawBitmap(bitmap, x + bitmap.width.toFloat(), 0f, paint)
    }
}
