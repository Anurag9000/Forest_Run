package com.yourname.forest_run.entities

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.sin

class CostumeOverlay {

    private var elapsed = 0f
    private val capePath = Path()

    private val flowerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 4f
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val capePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(210, 72, 88, 150)
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(180, 35, 40, 55)
    }

    fun update(deltaTime: Float) {
        elapsed += deltaTime
    }

    fun draw(
        canvas: Canvas,
        bodyRect: RectF,
        style: CostumeStyle,
        state: PlayerState,
        isInvincible: Boolean
    ) {
        if (style == CostumeStyle.NONE) return
        when (style) {
            CostumeStyle.NONE -> Unit
            CostumeStyle.FLOWER_CROWN -> drawFlowerCrown(canvas, bodyRect, isInvincible)
            CostumeStyle.VINE_SCARF -> drawVineScarf(canvas, bodyRect, state, isInvincible)
            CostumeStyle.MOON_CAPE -> drawMoonCape(canvas, bodyRect, state, isInvincible)
            CostumeStyle.BLOOM_RIBBON -> drawBloomRibbon(canvas, bodyRect, isInvincible)
        }
    }

    private fun drawFlowerCrown(canvas: Canvas, bodyRect: RectF, isInvincible: Boolean) {
        val crownY = bodyRect.top + bodyRect.height() * 0.13f
        val step = bodyRect.width() * 0.12f
        val startX = bodyRect.centerX() - step * 2f
        leafPaint.color = if (isInvincible) Color.rgb(190, 255, 220) else Color.rgb(92, 160, 78)
        flowerPaint.color = if (isInvincible) Color.rgb(255, 248, 255) else Color.rgb(255, 214, 228)
        ribbonPaint.color = if (isInvincible) Color.rgb(240, 255, 245) else Color.rgb(86, 120, 78)
        canvas.drawArc(
            bodyRect.centerX() - bodyRect.width() * 0.22f,
            crownY - bodyRect.height() * 0.06f,
            bodyRect.centerX() + bodyRect.width() * 0.22f,
            crownY + bodyRect.height() * 0.10f,
            200f,
            140f,
            false,
            ribbonPaint
        )
        repeat(5) { index ->
            val cx = startX + step * index
            canvas.drawOval(
                cx - step * 0.32f,
                crownY - step * 0.18f,
                cx + step * 0.32f,
                crownY + step * 0.18f,
                leafPaint
            )
            canvas.drawCircle(cx, crownY, step * 0.22f, flowerPaint)
        }
    }

    private fun drawVineScarf(canvas: Canvas, bodyRect: RectF, state: PlayerState, isInvincible: Boolean) {
        val neckY = bodyRect.top + bodyRect.height() * 0.42f
        val wave = sin(elapsed * 7f) * bodyRect.height() * 0.025f
        ribbonPaint.color = if (isInvincible) Color.rgb(214, 255, 230) else Color.rgb(78, 145, 92)
        leafPaint.color = if (isInvincible) Color.rgb(245, 255, 235) else Color.rgb(120, 194, 116)
        val scarfDrop = if (state == PlayerState.DUCKING) bodyRect.height() * 0.10f else bodyRect.height() * 0.18f
        canvas.drawArc(
            bodyRect.centerX() - bodyRect.width() * 0.18f,
            neckY - bodyRect.height() * 0.06f,
            bodyRect.centerX() + bodyRect.width() * 0.18f,
            neckY + bodyRect.height() * 0.07f,
            180f,
            180f,
            false,
            ribbonPaint
        )
        canvas.drawLine(
            bodyRect.centerX() + bodyRect.width() * 0.08f,
            neckY + bodyRect.height() * 0.02f,
            bodyRect.centerX() + bodyRect.width() * 0.18f,
            neckY + scarfDrop + wave,
            ribbonPaint
        )
        canvas.drawLine(
            bodyRect.centerX() - bodyRect.width() * 0.02f,
            neckY + bodyRect.height() * 0.02f,
            bodyRect.centerX() + bodyRect.width() * 0.05f,
            neckY + scarfDrop * 0.78f - wave,
            ribbonPaint
        )
        canvas.drawOval(
            bodyRect.centerX() + bodyRect.width() * 0.12f,
            neckY + scarfDrop * 0.62f + wave - bodyRect.height() * 0.04f,
            bodyRect.centerX() + bodyRect.width() * 0.22f,
            neckY + scarfDrop * 0.62f + wave + bodyRect.height() * 0.04f,
            leafPaint
        )
    }

    private fun drawMoonCape(canvas: Canvas, bodyRect: RectF, state: PlayerState, isInvincible: Boolean) {
        val shoulderY = bodyRect.top + bodyRect.height() * 0.34f
        val capeBottom = bodyRect.bottom - bodyRect.height() * if (state == PlayerState.DUCKING) 0.20f else 0.02f
        val swing = sin(elapsed * 5f) * bodyRect.width() * 0.05f
        capePaint.color = if (isInvincible) Color.argb(220, 142, 170, 255) else Color.argb(210, 72, 88, 150)
        accentPaint.color = if (isInvincible) Color.rgb(255, 248, 214) else Color.rgb(235, 221, 165)
        capePath.reset()
        capePath.moveTo(bodyRect.centerX() - bodyRect.width() * 0.18f, shoulderY)
        capePath.lineTo(bodyRect.centerX() + bodyRect.width() * 0.18f, shoulderY)
        capePath.lineTo(bodyRect.centerX() + bodyRect.width() * 0.28f + swing, capeBottom)
        capePath.lineTo(bodyRect.centerX() - bodyRect.width() * 0.28f + swing * 0.4f, capeBottom - bodyRect.height() * 0.04f)
        capePath.close()
        canvas.drawPath(capePath, capePaint)
        canvas.drawPath(capePath, outlinePaint)
        canvas.drawCircle(
            bodyRect.centerX() + bodyRect.width() * 0.08f,
            bodyRect.top + bodyRect.height() * 0.48f,
            bodyRect.width() * 0.08f,
            accentPaint
        )
        accentPaint.color = capePaint.color
        canvas.drawCircle(
            bodyRect.centerX() + bodyRect.width() * 0.11f,
            bodyRect.top + bodyRect.height() * 0.48f,
            bodyRect.width() * 0.05f,
            accentPaint
        )
    }

    private fun drawBloomRibbon(canvas: Canvas, bodyRect: RectF, isInvincible: Boolean) {
        val sideX = bodyRect.centerX() + bodyRect.width() * 0.18f
        val topY = bodyRect.top + bodyRect.height() * 0.20f
        accentPaint.color = if (isInvincible) Color.rgb(255, 255, 255) else Color.rgb(255, 214, 92)
        ribbonPaint.color = if (isInvincible) Color.rgb(255, 255, 250) else Color.rgb(255, 138, 176)
        canvas.drawCircle(sideX, topY, bodyRect.width() * 0.07f, accentPaint)
        canvas.drawLine(
            sideX,
            topY + bodyRect.height() * 0.03f,
            sideX - bodyRect.width() * 0.07f,
            topY + bodyRect.height() * 0.16f,
            ribbonPaint
        )
        canvas.drawLine(
            sideX,
            topY + bodyRect.height() * 0.03f,
            sideX + bodyRect.width() * 0.05f,
            topY + bodyRect.height() * 0.17f,
            ribbonPaint
        )
    }
}
