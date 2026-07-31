package com.anurag9000.forestrun.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.floor

/** Plays a horizontal packed sprite strip without allocating per frame. */
class SpriteSheet(
    val bitmap: Bitmap,
    val frameCount: Int,
    var framesPerSec: Float,
    var isLooping: Boolean = true,
    private val startFrame: Int = 0,
    private val totalFramesInBitmap: Int = frameCount
) {
    init {
        require(frameCount > 0) { "frameCount must be > 0" }
        require(totalFramesInBitmap > 0) { "totalFramesInBitmap must be > 0" }
        require(startFrame >= 0) { "startFrame must be >= 0" }
        require(startFrame + frameCount <= totalFramesInBitmap) {
            "Requested frames [$startFrame, ${startFrame + frameCount}) exceed bitmap strip size $totalFramesInBitmap"
        }
        require(bitmap.width >= totalFramesInBitmap) {
            "Bitmap width ${bitmap.width} cannot contain $totalFramesInBitmap frames"
        }
        require(bitmap.width % totalFramesInBitmap == 0) {
            "Bitmap width ${bitmap.width} must divide exactly into $totalFramesInBitmap frames"
        }
        require(bitmap.height > 0) { "Bitmap height must be positive" }
    }

    val frameWidth: Int = bitmap.width / totalFramesInBitmap
    val frameHeight: Int = bitmap.height
    val aspectRatio: Float = frameWidth.toFloat() / frameHeight.toFloat()

    var currentFrame: Int = 0
        private set

    val isFinished: Boolean
        get() = !isLooping && currentFrame == frameCount - 1

    private var animationTimer: Float = 0f
    internal val animationTimerForTest: Float
        get() = animationTimer

    private val srcRect = Rect()
    private val paint = Paint().apply { isFilterBitmap = false }

    /**
     * Advance in O(1). Invalid timing or FPS values are no-ops, and arbitrarily
     * large valid deltas skip directly to the mathematically equivalent frame.
     */
    fun update(deltaTime: Float) {
        if (!deltaTime.isFinite() || deltaTime <= 0f || frameCount <= 1 || isFinished) return
        val fps = framesPerSec
        if (!fps.isFinite() || fps <= 0f) return

        val safeTimer = animationTimer.takeIf { it.isFinite() && it >= 0f } ?: 0f
        val totalSeconds = safeTimer.toDouble() + deltaTime.toDouble()
        val frameCredit = totalSeconds * fps.toDouble()
        val wholeFrames = floor(frameCredit)
        if (wholeFrames < 1.0) {
            animationTimer = totalSeconds
                .coerceAtMost(Float.MAX_VALUE.toDouble())
                .toFloat()
            return
        }

        val fractionalCredit = (frameCredit - wholeFrames).coerceIn(0.0, 1.0)
        if (isLooping) {
            val steps = (wholeFrames % frameCount.toDouble()).toInt()
            currentFrame = (currentFrame + steps) % frameCount
            animationTimer = (fractionalCredit / fps.toDouble()).toFloat()
            return
        }

        val remainingFrames = frameCount - 1 - currentFrame
        if (wholeFrames >= remainingFrames.toDouble()) {
            currentFrame = frameCount - 1
            animationTimer = 0f
        } else {
            currentFrame += wholeFrames.toInt()
            animationTimer = (fractionalCredit / fps.toDouble()).toFloat()
        }
    }

    fun reset() {
        currentFrame = 0
        animationTimer = 0f
    }

    fun setFrame(frameIndex: Int) {
        currentFrame = frameIndex.coerceIn(0, frameCount - 1)
        animationTimer = 0f
    }

    fun draw(canvas: Canvas, drawRect: RectF) {
        draw(canvas, drawRect, paint)
    }

    fun draw(canvas: Canvas, drawRect: RectF, drawPaint: Paint) {
        if (!drawRect.left.isFinite() || !drawRect.top.isFinite() ||
            !drawRect.right.isFinite() || !drawRect.bottom.isFinite() ||
            drawRect.width() <= 0f || drawRect.height() <= 0f
        ) return

        val absoluteFrame = startFrame + currentFrame
        val srcLeft = absoluteFrame * frameWidth
        srcRect.set(srcLeft, 0, srcLeft + frameWidth, frameHeight)
        canvas.drawBitmap(bitmap, srcRect, drawRect, drawPaint)
    }

    fun copy(): SpriteSheet =
        SpriteSheet(bitmap, frameCount, framesPerSec, isLooping, startFrame, totalFramesInBitmap)
}
