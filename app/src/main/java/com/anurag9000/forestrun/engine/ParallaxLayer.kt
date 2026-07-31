package com.anurag9000.forestrun.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/** A single horizontally scrolling, seamlessly wrapped background layer. */
class ParallaxLayer(
    val bitmap: Bitmap,
    private val speedFraction: Float
) {
    init {
        require(speedFraction.isFinite() && speedFraction >= 0f) {
            "speedFraction must be finite and non-negative"
        }
        require(bitmap.width > 0) { "bitmap width must be positive" }
    }

    /** Current left-edge position, normalized to (-bitmap.width, 0]. */
    var x: Float = 0f
        set(value) {
            if (value.isFinite()) field = value
        }

    private val paint = Paint().apply { isFilterBitmap = false }

    /** Advance in O(1), rejecting malformed or reversing frame inputs. */
    fun update(deltaTime: Float, gameScrollSpeed: Float) {
        if (!deltaTime.isFinite() || deltaTime <= 0f ||
            !gameScrollSpeed.isFinite() || gameScrollSpeed < 0f
        ) return

        val width = bitmap.width.toDouble()
        val current = x.takeIf { it.isFinite() }?.toDouble() ?: 0.0
        val distance = speedFraction.toDouble() *
            gameScrollSpeed.toDouble() *
            deltaTime.toDouble()
        val raw = current - distance
        val positiveRemainder = ((raw % width) + width) % width
        x = if (positiveRemainder == 0.0) {
            0f
        } else {
            (positiveRemainder - width).toFloat()
        }
    }

    /** Draw the current tile and its immediate right-hand successor. */
    fun draw(canvas: Canvas) {
        val safeX = x.takeIf { it.isFinite() } ?: 0f
        canvas.drawBitmap(bitmap, safeX, 0f, paint)
        canvas.drawBitmap(bitmap, safeX + bitmap.width.toFloat(), 0f, paint)
    }
}
