package com.yourname.forest_run.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

object BitmapHelper {

    /**
     * Creates a placeholder horizontal sprite strip with [frameCount] frames.
     * Each frame has a slightly different shade or border so animation is visible.
     * This is only used in Phase 6 until the real hand-drawn sprite sheets are loaded.
     *
     * @param frameW      Width of one frame
     * @param frameH      Height of one frame
     * @param frameCount  Number of frames in the strip
     * @param baseColor   Base (average) color of the sprite
     */
    fun buildPlaceholderStrip(
        frameW: Int,
        frameH: Int,
        frameCount: Int,
        baseColor: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(frameW * frameCount, frameH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val fillPaint = Paint().apply { style = Paint.Style.FILL }
        val strokePaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val r = Color.red(baseColor)
        val g = Color.green(baseColor)
        val b = Color.blue(baseColor)

        for (i in 0 until frameCount) {
            // Pulse the brightness slightly per frame to show active animation
            val pulse = Math.sin((i.toDouble() / frameCount) * Math.PI * 2.0).toFloat()
            val mod = (pulse * 30f).toInt()

            val cr = (r + mod).coerceIn(0, 255)
            val cg = (g + mod).coerceIn(0, 255)
            val cb = (b + mod).coerceIn(0, 255)

            fillPaint.color = Color.rgb(cr, cg, cb)

            val left = (i * frameW).toFloat()
            val right = left + frameW
            val bottom = frameH.toFloat()

            // Draw a slightly smaller rounded rect for the body
            canvas.drawRoundRect(left + 8f, 8f, right - 8f, bottom - 4f, 12f, 12f, fillPaint)
            canvas.drawRoundRect(left + 8f, 8f, right - 8f, bottom - 4f, 12f, 12f, strokePaint)

            // Draw a simple eye that moves up and down per frame
            val eyePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
            val pupilPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }

            val eyeY = frameH * 0.35f + pulse * 6f
            val eyeX = left + frameW * 0.70f // facing right

            canvas.drawCircle(eyeX, eyeY, 8f, eyePaint)
            canvas.drawCircle(eyeX + 3f, eyeY, 4f, pupilPaint)
        }

        return bmp
    }
}
