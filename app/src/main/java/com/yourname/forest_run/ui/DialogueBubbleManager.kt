
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

object DialogueBubbleManager {
    private const val FLOAT_SPEED = 28f
    private const val LIFETIME_S = 1.8f
    private const val PADDING_X = 18f
    private const val PADDING_Y = 12f
    private const val POINTER_H = 12f
    private const val CORNER_R = 16f
    private const val TEXT_SIZE = 18f
    private const val MAX_BUBBLES = 5
    private const val MAX_WIDTH = 240f
    private const val MAX_LINES = 3

    private var pixelFont: Typeface? = null
    private val variantCounts = mutableMapOf<String, Int>()

    data class Bubble(
        val lines: List<String>,
        var x: Float,
        var y: Float,
        val fillColor: Int,
        val borderColor: Int,
        var elapsed: Float = 0f
    ) {
        val progress get() = (elapsed / LIFETIME_S).coerceIn(0f, 1f)
        val alpha get() = ((1f - MathUtils.normalise(progress, 0.65f, 1f)) * 255f).toInt().coerceIn(0, 255)
        val isDead get() = elapsed >= LIFETIME_S
    }

    private val active = mutableListOf<Bubble>()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(28, 28, 28)
        textAlign = Paint.Align.CENTER
        textSize = TEXT_SIZE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
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
        pixelFont = runCatching { Typeface.createFromAsset(context.assets, AssetPaths.PIXEL_FONT) }.getOrNull()
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
        active += Bubble(wrap(text), anchorX, anchorY, fillColor, borderColor)
    }

    fun spawnVariant(
        triggerKey: String,
        textOptions: List<String>,
        anchorX: Float,
        anchorY: Float,
        fillColor: Int = Color.rgb(250, 246, 228),
        borderColor: Int = Color.rgb(40, 40, 40)
    ) {
        if (textOptions.isEmpty()) return
        val next = variantCounts.getOrDefault(triggerKey, 0)
        variantCounts[triggerKey] = next + 1
        spawn(textOptions[next % textOptions.size], anchorX, anchorY, fillColor, borderColor)
    }

    fun update(deltaTime: Float) {
        val iterator = active.iterator()
        while (iterator.hasNext()) {
            val bubble = iterator.next()
            bubble.elapsed += deltaTime
            bubble.y -= FLOAT_SPEED * deltaTime
            if (bubble.isDead) iterator.remove()
        }
    }

    fun draw(canvas: Canvas) {
        for (bubble in active) {
            val alpha = bubble.alpha
            if (alpha <= 0) continue
            val widest = bubble.lines.maxOfOrNull(textPaint::measureText) ?: 0f
            val bubbleW = widest.coerceAtMost(MAX_WIDTH) + PADDING_X * 2f
            val lineHeight = TEXT_SIZE + 5f
            val bubbleH = bubble.lines.size * lineHeight + PADDING_Y * 2f
            val centerX = bubble.x.coerceIn(bubbleW / 2f + 6f, canvas.width - bubbleW / 2f - 6f)
            val top = (bubble.y - bubbleH - POINTER_H).coerceAtLeast(6f)
            bubbleRect.set(centerX - bubbleW / 2f, top, centerX + bubbleW / 2f, top + bubbleH)
            shadowRect.set(bubbleRect.left + 4f, bubbleRect.top + 5f, bubbleRect.right + 4f, bubbleRect.bottom + 5f)

            textPaint.alpha = alpha
            fillPaint.color = bubble.fillColor
            fillPaint.alpha = (alpha * 0.96f).toInt()
            borderPaint.color = bubble.borderColor
            borderPaint.alpha = alpha
            shadowPaint.alpha = (alpha * 0.33f).toInt()

            val pointerX = bubble.x.coerceIn(bubbleRect.left + 14f, bubbleRect.right - 14f)
            pointerPath.reset()
            pointerPath.moveTo(pointerX - 12f, bubbleRect.bottom - 1f)
            pointerPath.lineTo(pointerX, bubbleRect.bottom + POINTER_H)
            pointerPath.lineTo(pointerX + 12f, bubbleRect.bottom - 1f)
            pointerPath.close()
            shadowPath.set(pointerPath)
            shadowPath.offset(4f, 5f)

            canvas.drawRoundRect(shadowRect, CORNER_R, CORNER_R, shadowPaint)
            canvas.drawPath(shadowPath, shadowPaint)
            canvas.drawRoundRect(bubbleRect, CORNER_R, CORNER_R, fillPaint)
            canvas.drawPath(pointerPath, fillPaint)
            canvas.drawRoundRect(bubbleRect, CORNER_R, CORNER_R, borderPaint)
            canvas.drawPath(pointerPath, borderPaint)

            var baseline = bubbleRect.top + PADDING_Y - textPaint.ascent()
            bubble.lines.forEach { line ->
                canvas.drawText(line, bubbleRect.centerX(), baseline, textPaint)
                baseline += lineHeight
            }
        }
    }

    private fun wrap(text: String): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isBlank()) word else "$current $word"
            if (textPaint.measureText(candidate) <= MAX_WIDTH || current.isBlank()) {
                current = candidate
            } else {
                lines += current
                current = word
            }
        }
        if (current.isNotBlank()) lines += current
        if (lines.size <= MAX_LINES) return lines
        val result = lines.take(MAX_LINES).toMutableList()
        result[result.lastIndex] = result.last().take(24).trimEnd() + "…"
        return result
    }

    fun clear() {
        active.clear()
        variantCounts.clear()
    }

    internal fun activeTextsForTest(): List<String> = active.map { it.lines.joinToString(" ") }
}
