package com.anurag9000.forestrun.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.anurag9000.forestrun.engine.AssetPaths
import com.anurag9000.forestrun.engine.FeedbackSettings

internal enum class FeedbackToggle { REDUCED_MOTION, AUDIO, HAPTICS }

internal data class FeedbackSettingsLayout(
    val reducedMotion: RectF,
    val audio: RectF,
    val haptics: RectF
) {
    val all: List<RectF> = listOf(reducedMotion, audio, haptics)
}

internal object FeedbackSettingsPanelLayout {
    fun build(width: Float, height: Float): FeedbackSettingsLayout {
        require(width > 0f && height > 0f)
        val right = width - (width * 0.02f).coerceAtLeast(18f)
        val chipWidth = (width * 0.18f).coerceIn(210f, 340f)
        val chipHeight = (height * 0.055f).coerceIn(38f, 54f)
        val gap = (height * 0.012f).coerceIn(7f, 13f)
        val top = (height * 0.61f).coerceAtMost(height - chipHeight * 3f - gap * 2f - 18f)
        fun rect(index: Int): RectF {
            val y = top + index * (chipHeight + gap)
            return RectF(right - chipWidth, y, right, y + chipHeight)
        }
        return FeedbackSettingsLayout(rect(0), rect(1), rect(2))
    }

    fun hitTest(layout: FeedbackSettingsLayout, x: Float, y: Float): FeedbackToggle? = when {
        contains(layout.reducedMotion, x, y) -> FeedbackToggle.REDUCED_MOTION
        contains(layout.audio, x, y) -> FeedbackToggle.AUDIO
        contains(layout.haptics, x, y) -> FeedbackToggle.HAPTICS
        else -> null
    }

    private fun contains(rect: RectF, x: Float, y: Float): Boolean =
        x >= rect.left && x < rect.right && y >= rect.top && y < rect.bottom
}

internal class FeedbackSettingsPanel(
    private val context: Context,
    screenWidth: Int,
    screenHeight: Int
) {
    private val layout = FeedbackSettingsPanelLayout.build(screenWidth.toFloat(), screenHeight.toFloat())
    private val pixelFont: Typeface = runCatching {
        Typeface.createFromAsset(context.assets, AssetPaths.PIXEL_FONT)
    }.getOrDefault(Typeface.MONOSPACE)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(205, 18, 28, 24)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 214, 232, 198)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(238, 244, 224)
        textSize = 13f
        typeface = pixelFont
        textAlign = Paint.Align.CENTER
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 238, 232, 184)
        textSize = 11f
        typeface = pixelFont
        textAlign = Paint.Align.RIGHT
    }

    fun onTap(x: Float, y: Float): Boolean {
        when (FeedbackSettingsPanelLayout.hitTest(layout, x, y)) {
            FeedbackToggle.REDUCED_MOTION -> FeedbackSettings.setReducedMotion(
                context,
                !FeedbackSettings.reducedMotion
            )
            FeedbackToggle.AUDIO -> FeedbackSettings.setAudioEnabled(
                context,
                !FeedbackSettings.audioEnabled
            )
            FeedbackToggle.HAPTICS -> FeedbackSettings.setHapticsEnabled(
                context,
                !FeedbackSettings.hapticsEnabled
            )
            null -> return false
        }
        return true
    }

    fun draw(canvas: Canvas) {
        canvas.drawText("COMFORT", layout.audio.right, layout.reducedMotion.top - 9f, titlePaint)
        drawChip(canvas, layout.reducedMotion, if (FeedbackSettings.reducedMotion) "MOTION: LOW" else "MOTION: FULL")
        drawChip(canvas, layout.audio, if (FeedbackSettings.audioEnabled) "AUDIO: ON" else "AUDIO: OFF")
        drawChip(canvas, layout.haptics, if (FeedbackSettings.hapticsEnabled) "HAPTICS: ON" else "HAPTICS: OFF")
    }

    private fun drawChip(canvas: Canvas, rect: RectF, label: String) {
        canvas.drawRoundRect(rect, 12f, 12f, fillPaint)
        canvas.drawRoundRect(rect, 12f, 12f, borderPaint)
        val baseline = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, rect.centerX(), baseline, textPaint)
    }
}
