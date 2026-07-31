package com.anurag9000.forestrun.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.anurag9000.forestrun.engine.AssetPaths
import com.anurag9000.forestrun.engine.RuntimeWorkloadTelemetry
import com.anurag9000.forestrun.utils.MathUtils
import kotlin.math.abs

/** Bounded floating flavor-text overlay manager. */
object FlavorTextManager {
    private const val FLOAT_SPEED = 55f
    private const val SHADOW_DX = 2f
    private const val SHADOW_DY = 2f
    private const val FADE_START = 0.55f
    private const val MAX_ACTIVE = 16
    private const val DUPLICATE_RADIUS_PX = 44f
    private const val MAX_TEXT_CHARS = 72
    private const val DEFAULT_LIFETIME_S = 1.4f
    private const val DEFAULT_SIZE_PX = 30f
    private const val MIN_LIFETIME_S = 0.15f
    private const val MAX_LIFETIME_S = 8f
    private const val MIN_SIZE_PX = 8f
    private const val MAX_SIZE_PX = 72f

    private var pixelFont: Typeface? = null

    fun init(context: Context) {
        pixelFont = runCatching {
            Typeface.createFromAsset(context.assets, AssetPaths.PIXEL_FONT)
        }.getOrNull()
    }

    data class FlavorText(
        val text: String,
        var x: Float,
        var y: Float,
        var colour: Int = Color.WHITE,
        var lifetime: Float = DEFAULT_LIFETIME_S,
        var baseSize: Float = DEFAULT_SIZE_PX,
        var elapsed: Float = 0f,
        var anchorX: Float = x,
        var anchorY: Float = y
    ) {
        val progress: Float
            get() = if (!elapsed.isFinite() || !lifetime.isFinite() || lifetime <= 0f) {
                1f
            } else {
                (elapsed / lifetime).coerceIn(0f, 1f)
            }

        val alpha: Int
            get() {
                val fade = MathUtils.normalise(progress, FADE_START, 1f)
                return ((1f - fade) * 255f).toInt().coerceIn(0, 255)
            }

        val currentSize: Float
            get() {
                val popIn = MathUtils.normalise(1f - progress, 0.7f, 1f)
                return baseSize * (0.85f + 0.15f * popIn)
            }

        val isDead: Boolean
            get() = !elapsed.isFinite() || !lifetime.isFinite() || lifetime <= 0f || elapsed >= lifetime
    }

    private val active = ArrayList<FlavorText>(MAX_ACTIVE)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 0, 0, 0)
    }

    fun spawn(
        text: String,
        x: Float,
        y: Float,
        colour: Int = Color.WHITE,
        lifetime: Float = DEFAULT_LIFETIME_S,
        size: Float = DEFAULT_SIZE_PX
    ) {
        val normalizedText = text.trim().take(MAX_TEXT_CHARS)
        if (normalizedText.isEmpty() || !x.isFinite() || !y.isFinite()) return

        val safeLifetime = lifetime.takeIf { it.isFinite() }
            ?.coerceIn(MIN_LIFETIME_S, MAX_LIFETIME_S)
            ?: DEFAULT_LIFETIME_S
        val safeSize = size.takeIf { it.isFinite() }
            ?.coerceIn(MIN_SIZE_PX, MAX_SIZE_PX)
            ?: DEFAULT_SIZE_PX

        // Match repeated callbacks against their stable authored anchors rather
        // than animated coordinates that drift upward every frame.
        val duplicate = active.lastOrNull { existing ->
            existing.text == normalizedText &&
                abs(existing.anchorX - x) <= DUPLICATE_RADIUS_PX &&
                abs(existing.anchorY - y) <= DUPLICATE_RADIUS_PX
        }
        if (duplicate != null) {
            duplicate.anchorX = x
            duplicate.anchorY = y
            duplicate.x = x
            duplicate.y = y
            duplicate.colour = colour
            duplicate.lifetime = safeLifetime
            duplicate.baseSize = safeSize
            duplicate.elapsed = 0f
            RuntimeWorkloadTelemetry.publishFlavorTexts(active.size)
            return
        }

        while (active.size >= MAX_ACTIVE) active.removeAt(0)
        active.add(
            FlavorText(
                text = normalizedText,
                x = x,
                y = y,
                colour = colour,
                lifetime = safeLifetime,
                baseSize = safeSize,
                anchorX = x,
                anchorY = y
            )
        )
        RuntimeWorkloadTelemetry.publishFlavorTexts(active.size)
    }

    fun update(deltaTime: Float) {
        if (!deltaTime.isFinite() || deltaTime <= 0f) return
        var textIndex = 0
        while (textIndex < active.size) {
            val flavorText = active[textIndex]
            flavorText.elapsed = (flavorText.elapsed.toDouble() + deltaTime.toDouble())
                .coerceAtMost(Float.MAX_VALUE.toDouble())
                .toFloat()
            flavorText.y = (flavorText.y.toDouble() - FLOAT_SPEED.toDouble() * deltaTime.toDouble())
                .coerceIn(-Float.MAX_VALUE.toDouble(), Float.MAX_VALUE.toDouble())
                .toFloat()
            if (flavorText.isDead) {
                active.removeAt(textIndex)
            } else {
                textIndex++
            }
        }
        RuntimeWorkloadTelemetry.publishFlavorTexts(active.size)
    }

    fun draw(canvas: Canvas) {
        val font = pixelFont ?: Typeface.MONOSPACE
        var textIndex = 0
        while (textIndex < active.size) {
            val flavorText = active[textIndex]
            val size = flavorText.currentSize
            val alpha = flavorText.alpha
            if (alpha > 0 && size.isFinite() && size > 0f) {
                textPaint.typeface = font
                textPaint.textSize = size
                textPaint.color = flavorText.colour
                textPaint.alpha = alpha

                shadowPaint.typeface = font
                shadowPaint.textSize = size
                shadowPaint.alpha = (alpha * 0.6f).toInt().coerceIn(0, 255)

                canvas.drawText(
                    flavorText.text,
                    flavorText.x + SHADOW_DX,
                    flavorText.y + SHADOW_DY,
                    shadowPaint
                )
                canvas.drawText(flavorText.text, flavorText.x, flavorText.y, textPaint)
            }
            textIndex++
        }
    }

    fun clear() {
        active.clear()
        RuntimeWorkloadTelemetry.publishFlavorTexts(0)
    }

    internal fun activeCountForTest(): Int = active.size
    internal fun activeTextsForTest(): List<String> = active.map { it.text }
    internal fun activeLifetimesForTest(): List<Float> = active.map { it.lifetime }
    internal fun activeSizesForTest(): List<Float> = active.map { it.baseSize }
    internal fun activeColoursForTest(): List<Int> = active.map { it.colour }
}
