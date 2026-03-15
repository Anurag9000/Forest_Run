package com.yourname.forest_run.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.yourname.forest_run.engine.EncounterDirector

enum class DebugOverlayAction {
    PREVIOUS,
    TOGGLE_RUN,
    NEXT
}

class DebugEncounterOverlay(
    private val screenWidth: Int
) {
    private val panelRect = RectF()
    private val prevRect = RectF()
    private val toggleRect = RectF()
    private val nextRect = RectF()

    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(195, 16, 22, 24)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 160, 220, 170)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(238, 255, 232)
        textSize = 16f
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.LEFT
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 220, 238, 226)
        textSize = 13f
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.LEFT
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 108, 172, 118)
        style = Paint.Style.FILL
    }
    private val buttonBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(54, 76, 50)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(28, 42, 24)
        textSize = 14f
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    fun handleTap(tapX: Float, tapY: Float): DebugOverlayAction? {
        syncLayout()
        return when {
            prevRect.contains(tapX, tapY) -> DebugOverlayAction.PREVIOUS
            toggleRect.contains(tapX, tapY) -> DebugOverlayAction.TOGGLE_RUN
            nextRect.contains(tapX, tapY) -> DebugOverlayAction.NEXT
            else -> null
        }
    }

    fun draw(
        canvas: Canvas,
        director: EncounterDirector,
        biomeLabel: String,
        activeEntityCount: Int,
        bloomText: String
    ) {
        syncLayout()
        canvas.drawRoundRect(panelRect, 18f, 18f, panelPaint)
        canvas.drawRoundRect(panelRect, 18f, 18f, borderPaint)

        var y = panelRect.top + 22f
        canvas.drawText("DEBUG ENCOUNTERS", panelRect.left + 14f, y, titlePaint)
        y += 18f
        val scenario = director.selectedScenario
        canvas.drawText("Scenario: ${scenario.title}", panelRect.left + 14f, y, bodyPaint)
        y += 16f
        canvas.drawText("Plan: ${scenario.summary}", panelRect.left + 14f, y, bodyPaint)
        y += 16f
        canvas.drawText(
            "Mode: ${if (director.isScenarioActive) "SCENARIO" else "LIVE"}  Steps: ${director.remainingSteps}",
            panelRect.left + 14f,
            y,
            bodyPaint
        )
        y += 16f
        canvas.drawText("Biome: $biomeLabel  Active: $activeEntityCount", panelRect.left + 14f, y, bodyPaint)
        y += 16f
        canvas.drawText("Bloom: $bloomText", panelRect.left + 14f, y, bodyPaint)

        drawButton(canvas, prevRect, "PREV")
        drawButton(canvas, toggleRect, if (director.isScenarioActive) "LIVE" else "RUN")
        drawButton(canvas, nextRect, "NEXT")
    }

    private fun drawButton(canvas: Canvas, rect: RectF, label: String) {
        canvas.drawRoundRect(rect, 12f, 12f, buttonPaint)
        canvas.drawRoundRect(rect, 12f, 12f, buttonBorderPaint)
        val baseline = rect.centerY() - (buttonTextPaint.descent() + buttonTextPaint.ascent()) / 2f
        canvas.drawText(label, rect.centerX(), baseline, buttonTextPaint)
    }

    private fun syncLayout() {
        panelRect.set(screenWidth * 0.48f, 12f, screenWidth * 0.98f, 124f)
        val buttonTop = panelRect.bottom - 36f
        val buttonBottom = panelRect.bottom - 10f
        val buttonGap = 10f
        val buttonWidth = (panelRect.width() - buttonGap * 4f) / 3f
        prevRect.set(panelRect.left + buttonGap, buttonTop, panelRect.left + buttonGap + buttonWidth, buttonBottom)
        toggleRect.set(prevRect.right + buttonGap, buttonTop, prevRect.right + buttonGap + buttonWidth, buttonBottom)
        nextRect.set(toggleRect.right + buttonGap, buttonTop, toggleRect.right + buttonGap + buttonWidth, buttonBottom)
    }
}
