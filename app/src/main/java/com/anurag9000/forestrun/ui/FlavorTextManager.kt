package com.anurag9000.forestrun.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.anurag9000.forestrun.engine.AssetPaths
import com.anurag9000.forestrun.utils.MathUtils
import java.util.ArrayDeque
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
        val colour: Int = Color.WHITE,
        val lifetime: Float = 1.4f,
        val baseSize: Float = 30f,
        var elapsed: Float = 0f
    ) {
        val progress: Float
            get() = (elapsed / lifetime).coerceIn(0f, 1f)

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
            get() = elapsed >= lifetime
    }

    private val active = ArrayDeque<FlavorText>(MAX_ACTIVE)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 0, 0, 0)
    }

    fun spawn(
        text: String,
        x: Float,
        y: Float,
        colour: Int = Color.WHITE,
        lifetime: Float = 1.4f,
        size: Float = 30f
    ) {
        val normalizedText = text.trim().take(MAX_TEXT_CHARS)
        if (normalizedText.isEmpty() || !x.isFinite() || !y.isFinite()) return

        val safeLifetime = lifetime.coerceIn(0.15f, 8f)
        val safeSize = size.coerceIn(8f, 72f)

        // Repeated callbacks should not flood the screen with the same message.
        val duplicate = active.lastOrNull { existing ->
            existing.text == normalizedText &&
                abs(existing.x - x) <= DUPLICATE_RADIUS_PX &&
                abs(existing.y - y) <= DUPLICATE_RADIUS_PX
        }
        if (duplicate != null) {
            duplicate.x = x
            duplicate.y = y
            duplicate.elapsed = 0f
            return
        }

        while (active.size >= MAX_ACTIVE) active.removeFirst()
        active.addLast(
            FlavorText(
                text = normalizedText,
                x = x,
                y = y,
                colour = colour,
                lifetime = safeLifetime,
                baseSize = safeSize
            )
        )
    }

    fun update(deltaTime: Float) {
        if (!deltaTime.isFinite() || deltaTime <= 0f) return
        val iterator = active.iterator()
        while (iterator.hasNext()) {
            val flavorText = iterator.next()
            flavorText.elapsed += deltaTime
            flavorText.y -= FLOAT_SPEED * deltaTime
            if (flavorText.isDead) iterator.remove()
        }
    }

    fun draw(canvas: Canvas) {
        val font = pixelFont ?: Typeface.MONOSPACE
        for (flavorText in active) {
            val size = flavorText.currentSize
            val alpha = flavorText.alpha
            if (alpha <= 0) continue

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
    }

    fun clear() = active.clear()

    internal fun activeCountForTest(): Int = active.size
    internal fun activeTextsForTest(): List<String> = active.map { it.text }
}
