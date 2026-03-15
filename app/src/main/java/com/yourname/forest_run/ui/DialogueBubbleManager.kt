package com.yourname.forest_run.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.yourname.forest_run.engine.AssetPaths
import com.yourname.forest_run.utils.MathUtils

/**
 * Screen-readable world-space dialogue bubbles for animals and other characterful entities.
 */
object DialogueBubbleManager {

    private const val FLOAT_SPEED = 28f
    private const val LIFETIME_S = 1.8f
    private const val PADDING_X = 18f
    private const val PADDING_Y = 14f
    private const val POINTER_H = 12f
    private const val CORNER_R = 16f
    private const val TEXT_SIZE = 18f
    private const val MAX_BUBBLES = 5
    private const val MAX_WIDTH = 240f

    private var pixelFont: Typeface? = null

    data class Bubble(
        val text: String,
        var x: Float,
        var y: Float,
        val fillColor: Int,
        val borderColor: Int,
        var elapsed: Float = 0f
    ) {
        val progress: Float get() = (elapsed / LIFETIME_S).coerceIn(0f, 1f)
        val alpha: Int get() = ((1f - MathUtils.normalise(progress, 0.65f, 1f)) * 255f).toInt().coerceIn(0, 255)
        val isDead: Boolean get() = elapsed >= LIFETIME_S
    }

    private val active = mutableListOf<Bubble>()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(28, 28, 28)
        textAlign = Paint.Align.CENTER
        textSize = TEXT_SIZE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val bubbleRect = RectF()
    private val shadowRect = RectF()
    private val pointerPath = Path()
    private val shadowPath = Path()

    fun init(context: Context) {
        pixelFont = runCatching {
            Typeface.createFromAsset(context.assets, AssetPaths.PIXEL_FONT)
        }.getOrNull()
        textPaint.typeface = pixelFont ?: Typeface.MONOSPACE
    }

    fun spawn(
        text: String,
        anchorX: Float,
        anchorY: Float,
        fillColor: Int = Color.rgb(250, 246, 228),
        borderColor: Int = Color.rgb(40, 40, 40)
    ) {
        if (active.size >= MAX_BUBBLES) active.removeAt(0)
        active.add(Bubble(text = text, x = anchorX, y = anchorY, fillColor = fillColor, borderColor = borderColor))
    }

    fun update(deltaTime: Float) {
        val iter = active.iterator()
        while (iter.hasNext()) {
            val bubble = iter.next()
            bubble.elapsed += deltaTime
            bubble.y -= FLOAT_SPEED * deltaTime
            if (bubble.isDead) iter.remove()
        }
    }

    fun draw(canvas: Canvas) {
        for (bubble in active) {
            val alpha = bubble.alpha
            if (alpha <= 0) continue

            textPaint.alpha = alpha
            fillPaint.color = bubble.fillColor
            fillPaint.alpha = (alpha * 0.96f).toInt()
            borderPaint.color = bubble.borderColor
            borderPaint.alpha = alpha
            shadowPaint.alpha = (alpha * 0.33f).toInt()

            val textWidth = textPaint.measureText(bubble.text).coerceAtMost(MAX_WIDTH)
            val bubbleW = textWidth + PADDING_X * 2f
            val bubbleH = TEXT_SIZE + PADDING_Y * 2f
            val left = bubble.x - bubbleW / 2f
            val top = bubble.y - bubbleH - POINTER_H
            bubbleRect.set(left, top, left + bubbleW, top + bubbleH)
            shadowRect.set(left + 4f, top + 5f, left + bubbleW + 4f, top + bubbleH + 5f)

            pointerPath.reset()
            pointerPath.moveTo(bubble.x - 12f, bubbleRect.bottom - 1f)
            pointerPath.lineTo(bubble.x, bubble.y)
            pointerPath.lineTo(bubble.x + 12f, bubbleRect.bottom - 1f)
            pointerPath.close()

            shadowPath.reset()
            shadowPath.moveTo(bubble.x - 9f + 4f, bubbleRect.bottom + 4f)
            shadowPath.lineTo(bubble.x + 4f, bubble.y + 5f)
            shadowPath.lineTo(bubble.x + 15f + 4f, bubbleRect.bottom + 4f)
            shadowPath.close()

            canvas.drawRoundRect(shadowRect, CORNER_R, CORNER_R, shadowPaint)
            canvas.drawPath(shadowPath, shadowPaint)
            canvas.drawRoundRect(bubbleRect, CORNER_R, CORNER_R, fillPaint)
            canvas.drawPath(pointerPath, fillPaint)
            canvas.drawRoundRect(bubbleRect, CORNER_R, CORNER_R, borderPaint)
            canvas.drawPath(pointerPath, borderPaint)

            val baseline = bubbleRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(bubble.text, bubbleRect.centerX(), baseline, textPaint)
        }
    }

    fun clear() = active.clear()
}
