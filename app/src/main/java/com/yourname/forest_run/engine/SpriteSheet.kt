package com.yourname.forest_run.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/**
 * Handles playing animations from a single packed sprite sheet bitmap.
 *
 * Assumes all frames in the sheet are arranged horizontally in a single row
 * and have the exact same width and height.
 *
 * @param bitmap        The full sprite sheet image.
 * @param frameCount    Total number of frames in this strip.
 * @param framesPerSec  Playback speed (FPS). 0 means it won't animate automatically.
 * @param isLooping     If true, loops back to 0; if false, stops on the last frame.
 */
class SpriteSheet(
    val bitmap: Bitmap,
    val frameCount: Int,
    var framesPerSec: Float,
    var isLooping: Boolean = true,
    /** Offset into the strip — allows multiple SpriteSheets to share one bitmap. */
    private val startFrame: Int = 0,
    /** Number of physical frames packed into the backing bitmap strip. */
    private val totalFramesInBitmap: Int = frameCount
) {
    init {
        require(frameCount > 0) { "frameCount must be > 0" }
        require(totalFramesInBitmap > 0) { "totalFramesInBitmap must be > 0" }
        require(startFrame >= 0) { "startFrame must be >= 0" }
        require(startFrame + frameCount <= totalFramesInBitmap) {
            "Requested frames [$startFrame, ${startFrame + frameCount}) exceed bitmap strip size $totalFramesInBitmap"
        }
    }

    /** Width of a single frame in pixels. */
    val frameWidth: Int =
        if (totalFramesInBitmap > 0) bitmap.width / totalFramesInBitmap.coerceAtLeast(1) else bitmap.width

    /** Height of a single frame in pixels (same as bitmap height). */
    val frameHeight: Int = bitmap.height

    /** Width / height ratio of a single frame. */
    val aspectRatio: Float = frameWidth.toFloat() / frameHeight.coerceAtLeast(1)

    /** The currently displayed frame index (0..frameCount-1). */
    var currentFrame: Int = 0
        private set

    /** Whether a non-looping animation has reached its final frame. */
    val isFinished: Boolean get() = !isLooping && currentFrame == frameCount - 1

    private var animationTimer: Float = 0f
    private val srcRect = Rect()
    private val dstRect = Rect()

    // Pixel-art Paint (no filtering)
    private val paint = Paint().apply { isFilterBitmap = false }

    /** Must be called every game frame to advance the animation. */
    fun update(deltaTime: Float) {
        if (framesPerSec <= 0f || frameCount <= 1 || isFinished) return

        animationTimer += deltaTime
        val timePerFrame = 1f / framesPerSec

        while (animationTimer >= timePerFrame) {
            animationTimer -= timePerFrame
            currentFrame++

            if (currentFrame >= frameCount) {
                if (isLooping) {
                    currentFrame = 0
                } else {
                    currentFrame = frameCount - 1
                    animationTimer = 0f // clamp timer
                }
            }
        }
    }

    /** Forces the animation back to the first frame. */
    fun reset() {
        currentFrame = 0
        animationTimer = 0f
    }

    /** Hard-sets a specific frame (useful for state-based single frames like JUMP_START). */
    fun setFrame(frameIndex: Int) {
        currentFrame = frameIndex.coerceIn(0, frameCount - 1)
        animationTimer = 0f
    }

    /**
     * Draws the current frame stretching/squashing it to exactly fit [drawRect].
     */
    fun draw(canvas: Canvas, drawRect: android.graphics.RectF) {
        // Offset by startFrame so a shared bitmap plays only the right segment
        val absoluteFrame = startFrame + currentFrame
        val srcLeft = absoluteFrame * frameWidth
        srcRect.set(srcLeft, 0, srcLeft + frameWidth, frameHeight)

        canvas.drawBitmap(bitmap, srcRect, drawRect, paint)
    }

    /** Clones this instance (sharing the underlying Bitmap memory) so multiple entities can use it. */
    fun copy(): SpriteSheet {
        return SpriteSheet(bitmap, frameCount, framesPerSec, isLooping, startFrame, totalFramesInBitmap)
    }
}
