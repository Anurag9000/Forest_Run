package com.anurag9000.forestrun.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.anurag9000.forestrun.engine.AssetPaths
import com.anurag9000.forestrun.utils.MathUtils

/** Screen-readable, bounded world-space dialogue bubbles. */
object DialogueBubbleManager {
    private const val FLOAT_SPEED = 28f
    private const val LIFETIME_S = 1.8f
    private const val PADDING_X = 18f
    private const val PADDING_Y = 12f
    private const val POINTER_H = 12f
    private const val CORNER_R = 16f
    private const val TEXT_SIZE = 18f
    private const val LINE_SPACING = 6f
    private const val MAX_BUBBLES = 5
    private const val MAX_TEXT_WIDTH = 240f
    private const val MAX_LINES = 3
    private const val SCREEN_MARGIN = 8f
    private const val MIN_POINTER_INSET = 16f

    private var pixelFont: Typeface? = null
    private val variantCounts = mutableMapOf<String, Int>()

    data class Bubble(
        val text: String,
        val lines: List<String>,
        var x: Float,
        var y: Float,
        val fillColor: Int,
        val borderColor: Int,
        var elapsed: Float = 0f
    ) {
        val progress: Float
            get() = (elapsed / LIFETIME_S).coerceIn(0f, 1f)

        val alpha: Int
            get() = (
                (1f - MathUtils.normalise(progress, 0.65f, 1f)) * 255f
            ).toInt().coerceIn(0, 255)

        val isDead: Boolean
            get() = elapsed >= LIFETIME_S
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
        val normalized = text.trim()
        if (normalized.isEmpty() || !anchorX.isFinite() || !anchorY.isFinite()) return

        if (active.size >= MAX_BUBBLES) active.removeAt(0)
        active.add(
            Bubble(
                text = normalized,
                lines = wrapText(normalized, MAX_TEXT_WIDTH, textPaint, MAX_LINES),
                x = anchorX,
                y = anchorY,
                fillColor = fillColor,
                borderColor = borderColor
            )
        )
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
        val nextIndex = variantCounts.getOrDefault(triggerKey, 0)
        variantCounts[triggerKey] = nextIndex + 1
        spawn(
            text = textOptions[nextIndex % textOptions.size],
            anchorX = anchorX,
            anchorY = anchorY,
            fillColor = fillColor,
            borderColor = borderColor
        )
    }

    fun update(deltaTime: Float) {
        if (!deltaTime.isFinite() || deltaTime <= 0f) return
        val iterator = active.iterator()
        while (iterator.hasNext()) {
            val bubble = iterator.next()
            bubble.elapsed += deltaTime
            bubble.y -= FLOAT_SPEED * deltaTime
            if (bubble.isDead) iterator.remove()
        }
    }

    fun draw(canvas: Canvas) {
        val lineHeight = TEXT_SIZE + LINE_SPACING
        for (bubble in active) {
            val alpha = bubble.alpha
            if (alpha <= 0) continue

            textPaint.alpha = alpha
            fillPaint.color = bubble.fillColor
            fillPaint.alpha = (alpha * 0.96f).toInt().coerceIn(0, 255)
            borderPaint.color = bubble.borderColor
            borderPaint.alpha = alpha
            shadowPaint.alpha = (alpha * 0.33f).toInt().coerceIn(0, 255)

            val widestLine = bubble.lines.maxOfOrNull(textPaint::measureText) ?: 0f
            val bubbleWidth = (widestLine + PADDING_X * 2f)
                .coerceAtMost(canvas.width - SCREEN_MARGIN * 2f)
                .coerceAtLeast(PADDING_X * 2f + 1f)
            val textBlockHeight = bubble.lines.size * lineHeight - LINE_SPACING
            val bubbleHeight = textBlockHeight + PADDING_Y * 2f

            val unclampedLeft = bubble.x - bubbleWidth / 2f
            val maxLeft = (canvas.width - bubbleWidth - SCREEN_MARGIN)
                .coerceAtLeast(SCREEN_MARGIN)
            val left = unclampedLeft.coerceIn(SCREEN_MARGIN, maxLeft)
            val desiredTop = bubble.y - bubbleHeight - POINTER_H
            val maxTop = (canvas.height - bubbleHeight - POINTER_H - SCREEN_MARGIN)
                .coerceAtLeast(SCREEN_MARGIN)
            val top = desiredTop.coerceIn(SCREEN_MARGIN, maxTop)
            bubbleRect.set(left, top, left + bubbleWidth, top + bubbleHeight)
            shadowRect.set(
                bubbleRect.left + 4f,
                bubbleRect.top + 5f,
                bubbleRect.right + 4f,
                bubbleRect.bottom + 5f
            )

            val pointerX = bubble.x.coerceIn(
                bubbleRect.left + MIN_POINTER_INSET,
                bubbleRect.right - MIN_POINTER_INSET
            )
            val pointerTipY = (bubbleRect.bottom + POINTER_H)
                .coerceAtMost(canvas.height - SCREEN_MARGIN)

            pointerPath.reset()
            pointerPath.moveTo(pointerX - 12f, bubbleRect.bottom - 1f)
            pointerPath.lineTo(pointerX, pointerTipY)
            pointerPath.lineTo(pointerX + 12f, bubbleRect.bottom - 1f)
            pointerPath.close()

            shadowPath.reset()
            shadowPath.moveTo(pointerX - 8f, bubbleRect.bottom + 4f)
            shadowPath.lineTo(pointerX + 4f, pointerTipY + 5f)
            shadowPath.lineTo(pointerX + 16f, bubbleRect.bottom + 4f)
            shadowPath.close()

            canvas.drawRoundRect(shadowRect, CORNER_R, CORNER_R, shadowPaint)
            canvas.drawPath(shadowPath, shadowPaint)
            canvas.drawRoundRect(bubbleRect, CORNER_R, CORNER_R, fillPaint)
            canvas.drawPath(pointerPath, fillPaint)
            canvas.drawRoundRect(bubbleRect, CORNER_R, CORNER_R, borderPaint)
            canvas.drawPath(pointerPath, borderPaint)

            var baseline = bubbleRect.top + PADDING_Y - textPaint.ascent()
            for (line in bubble.lines) {
                canvas.drawText(line, bubbleRect.centerX(), baseline, textPaint)
                baseline += lineHeight
            }
        }
    }

    fun clear() {
        active.clear()
        variantCounts.clear()
    }

    internal fun activeTextsForTest(): List<String> = active.map { it.text }

    internal fun wrapTextForTest(text: String, maxWidth: Float): List<String> =
        wrapText(text.trim(), maxWidth, textPaint, MAX_LINES)

    private fun wrapText(
        text: String,
        maxWidth: Float,
        paint: Paint,
        maxLines: Int
    ): List<String> {
        if (text.isBlank()) return emptyList()
        val safeWidth = maxWidth.coerceAtLeast(1f)
        val tokens = splitOversizedWords(text.split(Regex("\\s+")).filter(String::isNotBlank), safeWidth, paint)
        val lines = mutableListOf<String>()
        val current = StringBuilder()

        for (token in tokens) {
            val candidate = if (current.isEmpty()) token else "$current $token"
            if (paint.measureText(candidate) <= safeWidth) {
                current.clear()
                current.append(candidate)
                continue
            }

            if (current.isNotEmpty()) {
                lines.add(current.toString())
                current.clear()
            }
            current.append(token)

            if (lines.size == maxLines - 1) break
        }

        if (current.isNotEmpty() && lines.size < maxLines) lines.add(current.toString())

        val consumed = lines.joinToString(" ").length
        if (consumed < text.length && lines.isNotEmpty()) {
            val lastIndex = lines.lastIndex
            lines[lastIndex] = ellipsize(lines[lastIndex], safeWidth, paint)
        }
        return lines.ifEmpty { listOf(ellipsize(text, safeWidth, paint)) }
    }

    private fun splitOversizedWords(words: List<String>, maxWidth: Float, paint: Paint): List<String> {
        val result = mutableListOf<String>()
        for (word in words) {
            if (paint.measureText(word) <= maxWidth) {
                result.add(word)
                continue
            }

            val part = StringBuilder()
            for (character in word) {
                val candidate = "$part$character"
                if (part.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                    result.add(part.toString())
                    part.clear()
                }
                part.append(character)
            }
            if (part.isNotEmpty()) result.add(part.toString())
        }
        return result
    }

    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        val ellipsis = "…"
        if (paint.measureText(text) <= maxWidth) return text
        val result = StringBuilder(text)
        while (result.isNotEmpty() && paint.measureText("$result$ellipsis") > maxWidth) {
            result.deleteCharAt(result.lastIndex)
        }
        return if (result.isEmpty()) ellipsis else "$result$ellipsis"
    }
}
