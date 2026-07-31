package com.anurag9000.forestrun.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.sin

object BitmapHelper {
    private const val MIN_FRAME_WIDTH_PX = 17
    private const val MIN_FRAME_HEIGHT_PX = 13
    private const val MAX_PLACEHOLDER_PIXELS = 16L * 1_024L * 1_024L

    /**
     * Creates a placeholder horizontal sprite strip with [frameCount] frames.
     * Each frame has a slightly different shade or border so animation is visible.
     * This is only used in debuggable builds when a real sprite cannot be loaded.
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
        require(frameW >= MIN_FRAME_WIDTH_PX) {
            "Placeholder frame width must be at least $MIN_FRAME_WIDTH_PX px."
        }
        require(frameH >= MIN_FRAME_HEIGHT_PX) {
            "Placeholder frame height must be at least $MIN_FRAME_HEIGHT_PX px."
        }
        require(frameCount > 0) {
            "Placeholder frame count must be positive."
        }

        val stripWidth = frameW.toLong() * frameCount.toLong()
        val pixelCount = stripWidth * frameH.toLong()
        require(stripWidth in 1L..Int.MAX_VALUE.toLong()) {
            "Placeholder strip width exceeds Android bitmap limits."
        }
        require(pixelCount in 1L..MAX_PLACEHOLDER_PIXELS) {
            "Placeholder strip exceeds the debug allocation budget."
        }

        val bmp = Bitmap.createBitmap(stripWidth.toInt(), frameH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val fillPaint = Paint().apply { style = Paint.Style.FILL }
        val strokePaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val eyePaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val pupilPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        val r = Color.red(baseColor)
        val g = Color.green(baseColor)
        val b = Color.blue(baseColor)

        for (i in 0 until frameCount) {
            // Pulse the brightness slightly per frame to show active animation.
            val pulse = sin(i.toFloat() / frameCount * PI.toFloat() * 2f)
            val mod = (pulse * 30f).toInt()

            val cr = (r + mod).coerceIn(0, 255)
            val cg = (g + mod).coerceIn(0, 255)
            val cb = (b + mod).coerceIn(0, 255)

            fillPaint.color = Color.rgb(cr, cg, cb)

            val left = (i * frameW).toFloat()
            val right = left + frameW
            val bottom = frameH.toFloat()

            // Draw a slightly smaller rounded rect for the body.
            canvas.drawRoundRect(left + 8f, 8f, right - 8f, bottom - 4f, 12f, 12f, fillPaint)
            canvas.drawRoundRect(left + 8f, 8f, right - 8f, bottom - 4f, 12f, 12f, strokePaint)

            // Draw a simple eye that moves up and down per frame.
            val eyeY = frameH * 0.35f + pulse * 6f
            val eyeX = left + frameW * 0.70f

            canvas.drawCircle(eyeX, eyeY, 8f, eyePaint)
            canvas.drawCircle(eyeX + 3f, eyeY, 4f, pupilPaint)
        }

        return bmp
    }
}
