package com.anurag9000.forestrun.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.anurag9000.forestrun.ForestJournalActivity
import com.anurag9000.forestrun.engine.ApplicationPersistenceFacade
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
        require(width.isFinite() && width > 0f) {
            "Feedback settings width must be finite and positive."
        }
        require(height.isFinite() && height > 0f) {
            "Feedback settings height must be finite and positive."
        }

        val right = width - (width * 0.02f).coerceAtLeast(18f)
        val chipWidth = (width * 0.18f).coerceIn(210f, 340f)
        val chipHeight = (height * 0.055f).coerceIn(38f, 54f)
        val gap = (height * 0.012f).coerceIn(7f, 13f)
        val top = (height * 0.61f).coerceAtMost(height - chipHeight * 3f - gap * 2f - 18f)
        fun rect(index: Int): RectF {
            val y = top + index * (chipHeight + gap)
            return RectF(right - chipWidth, y, right, y + chipHeight)
        }

        val layout = FeedbackSettingsLayout(rect(0), rect(1), rect(2))
        require(layout.all.all { isValidContainedRect(it, width, height) }) {
            "Feedback settings surface is too small for the comfort controls."
        }
        return layout
    }

    /**
     * A separate home-memory chip above the existing comfort stack. Keeping it
     * outside [FeedbackSettingsLayout.all] preserves the three-toggle contract.
     */
    fun journalBounds(width: Float, height: Float): RectF {
        val layout = build(width, height)
        val gap = (height * 0.012f).coerceIn(7f, 13f)
        val bottom = layout.reducedMotion.top - gap
        val top = bottom - layout.reducedMotion.height()
        return RectF(layout.reducedMotion.left, top, layout.reducedMotion.right, bottom).also { rect ->
            require(isValidContainedRect(rect, width, height)) {
                "Feedback settings surface is too small for the Forest Journal chip."
            }
        }
    }

    fun hitTest(layout: FeedbackSettingsLayout, x: Float, y: Float): FeedbackToggle? {
        if (!FiniteCoordinateAdmission.accepts(x, y)) return null
        return when {
            contains(layout.reducedMotion, x, y) -> FeedbackToggle.REDUCED_MOTION
            contains(layout.audio, x, y) -> FeedbackToggle.AUDIO
            contains(layout.haptics, x, y) -> FeedbackToggle.HAPTICS
            else -> null
        }
    }

    fun journalHitTest(width: Float, height: Float, x: Float, y: Float): Boolean {
        if (!FiniteCoordinateAdmission.accepts(x, y)) return false
        return contains(journalBounds(width, height), x, y)
    }

    private fun contains(rect: RectF, x: Float, y: Float): Boolean =
        isFiniteNonEmpty(rect) &&
            x >= rect.left && x < rect.right && y >= rect.top && y < rect.bottom

    private fun isValidContainedRect(rect: RectF, width: Float, height: Float): Boolean =
        isFiniteNonEmpty(rect) &&
            rect.left >= 0f &&
            rect.top >= 0f &&
            rect.right <= width &&
            rect.bottom <= height

    private fun isFiniteNonEmpty(rect: RectF): Boolean =
        rect.left.isFinite() &&
            rect.top.isFinite() &&
            rect.right.isFinite() &&
            rect.bottom.isFinite() &&
            rect.left < rect.right &&
            rect.top < rect.bottom
}

internal class FeedbackSettingsPanel(
    private val context: Context,
    screenWidth: Int,
    screenHeight: Int,
    private val persistenceFacade: ApplicationPersistenceFacade =
        ApplicationPersistenceFacade.android(context)
) {
    private val screenWidthF = screenWidth.toFloat()
    private val screenHeightF = screenHeight.toFloat()
    private val layout = FeedbackSettingsPanelLayout.build(screenWidthF, screenHeightF)
    private val journalRect = FeedbackSettingsPanelLayout.journalBounds(screenWidthF, screenHeightF)
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
    private val memoryFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(218, 48, 62, 38)
        style = Paint.Style.FILL
    }
    private val memoryBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(226, 236, 218, 140)
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
        if (FeedbackSettingsPanelLayout.journalHitTest(screenWidthF, screenHeightF, x, y)) {
            openForestJournal()
            return true
        }
        when (FeedbackSettingsPanelLayout.hitTest(layout, x, y)) {
            FeedbackToggle.REDUCED_MOTION -> persistenceFacade.saveFeedbackPreferences(
                FeedbackSettings.snapshot().copy(
                    reducedMotion = !FeedbackSettings.reducedMotion
                )
            )
            FeedbackToggle.AUDIO -> persistenceFacade.saveFeedbackPreferences(
                FeedbackSettings.snapshot().copy(
                    audioEnabled = !FeedbackSettings.audioEnabled
                )
            )
            FeedbackToggle.HAPTICS -> persistenceFacade.saveFeedbackPreferences(
                FeedbackSettings.snapshot().copy(
                    hapticsEnabled = !FeedbackSettings.hapticsEnabled
                )
            )
            null -> return false
        }
        return true
    }

    fun draw(canvas: Canvas) {
        canvas.drawText("MEMORY", journalRect.right, journalRect.top - 9f, titlePaint)
        drawMemoryChip(canvas, journalRect, "FOREST JOURNAL")
        canvas.drawText("COMFORT", layout.audio.right, layout.reducedMotion.top - 9f, titlePaint)
        drawChip(canvas, layout.reducedMotion, if (FeedbackSettings.reducedMotion) "MOTION: LOW" else "MOTION: FULL")
        drawChip(canvas, layout.audio, if (FeedbackSettings.audioEnabled) "AUDIO: ON" else "AUDIO: OFF")
        drawChip(canvas, layout.haptics, if (FeedbackSettings.hapticsEnabled) "HAPTICS: ON" else "HAPTICS: OFF")
    }

    private fun openForestJournal() {
        val intent = Intent(context, ForestJournalActivity::class.java)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun drawMemoryChip(canvas: Canvas, rect: RectF, label: String) {
        canvas.drawRoundRect(rect, 12f, 12f, memoryFillPaint)
        canvas.drawRoundRect(rect, 12f, 12f, memoryBorderPaint)
        val baseline = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, rect.centerX(), baseline, textPaint)
    }

    private fun drawChip(canvas: Canvas, rect: RectF, label: String) {
        canvas.drawRoundRect(rect, 12f, 12f, fillPaint)
        canvas.drawRoundRect(rect, 12f, 12f, borderPaint)
        val baseline = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, rect.centerX(), baseline, textPaint)
    }
}
