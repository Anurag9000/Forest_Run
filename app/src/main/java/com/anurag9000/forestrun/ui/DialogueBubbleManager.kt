package com.anurag9000.forestrun.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.anurag9000.forestrun.engine.AssetPaths
import com.anurag9000.forestrun.engine.RuntimeWorkloadTelemetry
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
    private const val MAX_VARIANT_KEYS = 128
    private const val MAX_TEXT_WIDTH = 240f
    private const val MAX_LINES = 3
    private const val SCREEN_MARGIN = 8f
    private const val MIN_POINTER_INSET = 16f

    private val whitespace = Regex("\\s+")
    private var pixelFont: Typeface? = null
    private val variantIndices = LinkedHashMap<String, Int>()

    data class Bubble(
        val text: String,
        val lines: List<String>,
        val widestLine: Float,
        var x: Float,
        var y: Float,
        val fillColor: Int,
        val borderColor: Int,
        var elapsed: Float = 0f
    ) {
        val progress: Float
            get() = (elapsed / LIFETIME_S).takeIf { it.isFinite() }
                ?.coerceIn(0f, 1f)
                ?: 1f

        val alpha: Int
            get() = ((1f - MathUtils.normalise(progress, 0.65f, 1f)) * 255f)
                .toInt()
                .coerceIn(0, 255)

        val isDead: Boolean
            get() = !elapsed.isFinite() || elapsed >= LIFETIME_S
    }

    private val active = mutableListOf<Bubble>()
    internal var lineMeasurementCountForTest: Int = 0
        private set
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
        val lines = wrapText(normalized, MAX_TEXT_WIDTH, textPaint, MAX_LINES)
        var widestLine = 0f
        var lineIndex = 0
        while (lineIndex < lines.size) {
            widestLine = maxOf(widestLine, textPaint.measureText(lines[lineIndex]))
            lineMeasurementCountForTest++
            lineIndex++
        }
        active.add(
            Bubble(
                text = normalized,
                lines = lines,
                widestLine = widestLine,
                x = anchorX,
                y = anchorY,
                fillColor = fillColor,
                borderColor = borderColor
            )
        )
        RuntimeWorkloadTelemetry.publishDialogueBubbles(active.size)
    }

    fun spawnVariant(
        triggerKey: String,
        textOptions: List<String>,
        anchorX: Float,
        anchorY: Float,
        fillColor: Int = Color.rgb(250, 246, 228),
        borderColor: Int = Color.rgb(40, 40, 40)
    ) {
        val key = triggerKey.trim()
        val options = textOptions.map { it.trim() }.filter { it.isNotEmpty() }
        if (key.isEmpty() || options.isEmpty()) return

        if (key !in variantIndices && variantIndices.size >= MAX_VARIANT_KEYS) {
            val eldest = variantIndices.entries.firstOrNull()?.key
            if (eldest != null) variantIndices.remove(eldest)
        }
        val index = variantIndices.getOrDefault(key, 0).coerceIn(0, options.lastIndex)
        variantIndices[key] = (index + 1) % options.size
        spawn(
            text = options[index],
            anchorX = anchorX,
            anchorY = anchorY,
            fillColor = fillColor,
            borderColor = borderColor
        )
    }

    fun update(deltaTime: Float) {
        if (!deltaTime.isFinite() || deltaTime <= 0f) return
        var bubbleIndex = 0
        while (bubbleIndex < active.size) {
            val bubble = active[bubbleIndex]
            bubble.elapsed = (bubble.elapsed.toDouble() + deltaTime.toDouble())
                .coerceAtMost(Float.MAX_VALUE.toDouble())
                .toFloat()
            bubble.y = (bubble.y.toDouble() - FLOAT_SPEED.toDouble() * deltaTime.toDouble())
                .coerceIn(-Float.MAX_VALUE.toDouble(), Float.MAX_VALUE.toDouble())
                .toFloat()
            if (bubble.isDead) {
                active.removeAt(bubbleIndex)
            } else {
                bubbleIndex++
            }
        }
        RuntimeWorkloadTelemetry.publishDialogueBubbles(active.size)
    }

    fun draw(canvas: Canvas) {
        val lineHeight = TEXT_SIZE + LINE_SPACING
        var bubbleIndex = 0
        while (bubbleIndex < active.size) {
            val bubble = active[bubbleIndex]
            val alpha = bubble.alpha
            if (alpha > 0) {
                textPaint.alpha = alpha
                fillPaint.color = bubble.fillColor
                fillPaint.alpha = (alpha * 0.96f).toInt().coerceIn(0, 255)
                borderPaint.color = bubble.borderColor
                borderPaint.alpha = alpha
                shadowPaint.alpha = (alpha * 0.33f).toInt().coerceIn(0, 255)

                val availableWidth = (canvas.width - SCREEN_MARGIN * 2f).coerceAtLeast(1f)
                val bubbleWidth = (bubble.widestLine + PADDING_X * 2f)
                    .coerceAtMost(availableWidth)
                    .coerceAtLeast(minOf(PADDING_X * 2f + 1f, availableWidth))
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

                val pointerInset = minOf(MIN_POINTER_INSET, bubbleRect.width() / 2f)
                val pointerMin = bubbleRect.left + pointerInset
                val pointerMax = bubbleRect.right - pointerInset
                val pointerX = bubble.x.coerceIn(pointerMin, maxOf(pointerMin, pointerMax))
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
                var lineIndex = 0
                while (lineIndex < bubble.lines.size) {
                    canvas.drawText(
                        bubble.lines[lineIndex],
                        bubbleRect.centerX(),
                        baseline,
                        textPaint
                    )
                    baseline += lineHeight
                    lineIndex++
                }
            }
            bubbleIndex++
        }
    }

    fun clear() {
        active.clear()
        variantIndices.clear()
        lineMeasurementCountForTest = 0
        RuntimeWorkloadTelemetry.publishDialogueBubbles(0)
    }

    internal fun activeTextsForTest(): List<String> = active.map { it.text }

    internal fun variantKeyCountForTest(): Int = variantIndices.size

    internal fun wrapTextForTest(text: String, maxWidth: Float): List<String> =
        wrapText(text.trim(), maxWidth, textPaint, MAX_LINES)

    private fun wrapText(
        text: String,
        maxWidth: Float,
        paint: Paint,
        maxLines: Int
    ): List<String> {
        if (text.isBlank()) return emptyList()
        val safeWidth = maxWidth.takeIf { it.isFinite() && it > 0f } ?: 1f
        val lineLimit = maxLines.coerceAtLeast(1)
        val tokens = splitOversizedWords(
            text.split(whitespace).filter { it.isNotBlank() },
            safeWidth,
            paint
        )
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        var tokenIndex = 0
        var truncated = false

        while (tokenIndex < tokens.size) {
            val token = tokens[tokenIndex]
            val candidate = if (current.isEmpty()) token else "$current $token"
            if (paint.measureText(candidate) <= safeWidth) {
                current.clear()
                current.append(candidate)
                tokenIndex++
                continue
            }

            if (current.isNotEmpty()) {
                lines.add(current.toString())
                current.clear()
                if (lines.size >= lineLimit) {
                    truncated = true
                    break
                }
            }
            current.append(token)
            tokenIndex++
        }

        if (current.isNotEmpty() && lines.size < lineLimit) lines.add(current.toString())
        if (tokenIndex < tokens.size) truncated = true
        if (truncated && lines.isNotEmpty()) {
            lines[lines.lastIndex] = appendEllipsis(lines.last(), safeWidth, paint)
        }
        return lines.ifEmpty { listOf(appendEllipsis(text, safeWidth, paint)) }
    }

    private fun splitOversizedWords(
        words: List<String>,
        maxWidth: Float,
        paint: Paint
    ): List<String> {
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

    private fun appendEllipsis(text: String, maxWidth: Float, paint: Paint): String {
        val ellipsis = "…"
        val result = StringBuilder(text.removeSuffix(ellipsis))
        while (result.isNotEmpty() && paint.measureText("$result$ellipsis") > maxWidth) {
            result.deleteCharAt(result.lastIndex)
        }
        return if (result.isEmpty()) ellipsis else "$result$ellipsis"
    }
}
