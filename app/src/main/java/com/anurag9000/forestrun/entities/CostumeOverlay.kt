package com.anurag9000.forestrun.entities

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
        isInvincible: Boolean,
        motion: PlayerSecondaryMotionState
    ) {
        if (style == CostumeStyle.NONE) return
        when (style) {
            CostumeStyle.NONE -> Unit
            CostumeStyle.FLOWER_CROWN -> drawFlowerCrown(canvas, bodyRect, isInvincible, motion)
            CostumeStyle.VINE_SCARF -> drawVineScarf(canvas, bodyRect, state, isInvincible, motion)
            CostumeStyle.MOON_CAPE -> drawMoonCape(canvas, bodyRect, state, isInvincible, motion)
            CostumeStyle.BELL_CHARM -> drawBellCharm(canvas, bodyRect, isInvincible, motion)
            CostumeStyle.LANTERN_PIN -> drawLanternPin(canvas, bodyRect, isInvincible, motion)
            CostumeStyle.SKY_SASH -> drawSkySash(canvas, bodyRect, state, isInvincible, motion)
            CostumeStyle.BLOOM_RIBBON -> drawBloomRibbon(canvas, bodyRect, isInvincible, motion)
        }
    }

    private fun drawFlowerCrown(canvas: Canvas, bodyRect: RectF, isInvincible: Boolean, motion: PlayerSecondaryMotionState) {
        val crownY = bodyRect.top + bodyRect.height() * 0.13f + motion.headOffsetPx
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

    private fun drawVineScarf(canvas: Canvas, bodyRect: RectF, state: PlayerState, isInvincible: Boolean, motion: PlayerSecondaryMotionState) {
        val neckY = bodyRect.top + bodyRect.height() * 0.42f
        val wave = sin(elapsed * 7f) * bodyRect.height() * 0.025f + motion.costumeSwingPx * 0.45f
        ribbonPaint.color = if (isInvincible) Color.rgb(214, 255, 230) else Color.rgb(78, 145, 92)
        leafPaint.color = if (isInvincible) Color.rgb(245, 255, 235) else Color.rgb(120, 194, 116)
        val scarfDrop = (if (state == PlayerState.DUCKING) bodyRect.height() * 0.10f else bodyRect.height() * 0.18f) + motion.costumeTrailLiftPx * 0.35f
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

    private fun drawMoonCape(canvas: Canvas, bodyRect: RectF, state: PlayerState, isInvincible: Boolean, motion: PlayerSecondaryMotionState) {
        val shoulderY = bodyRect.top + bodyRect.height() * 0.34f
        val capeBottom = bodyRect.bottom - bodyRect.height() * if (state == PlayerState.DUCKING) 0.20f else 0.02f
        val swing = sin(elapsed * 5f) * bodyRect.width() * 0.05f + motion.costumeSwingPx * 0.55f
        val capeLift = motion.costumeTrailLiftPx * 0.55f
        capePaint.color = if (isInvincible) Color.argb(220, 142, 170, 255) else Color.argb(210, 72, 88, 150)
        accentPaint.color = if (isInvincible) Color.rgb(255, 248, 214) else Color.rgb(235, 221, 165)
        capePath.reset()
        capePath.moveTo(bodyRect.centerX() - bodyRect.width() * 0.18f, shoulderY)
        capePath.lineTo(bodyRect.centerX() + bodyRect.width() * 0.18f, shoulderY)
        capePath.lineTo(bodyRect.centerX() + bodyRect.width() * 0.28f + swing, capeBottom - capeLift)
        capePath.lineTo(bodyRect.centerX() - bodyRect.width() * 0.28f + swing * 0.4f, capeBottom - bodyRect.height() * 0.04f - capeLift * 0.65f)
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

    private fun drawBloomRibbon(canvas: Canvas, bodyRect: RectF, isInvincible: Boolean, motion: PlayerSecondaryMotionState) {
        val sideX = bodyRect.centerX() + bodyRect.width() * 0.18f
        val topY = bodyRect.top + bodyRect.height() * 0.20f + motion.headOffsetPx
        val swing = motion.costumeSwingPx * 0.35f
        accentPaint.color = if (isInvincible) Color.rgb(255, 255, 255) else Color.rgb(255, 214, 92)
        ribbonPaint.color = if (isInvincible) Color.rgb(255, 255, 250) else Color.rgb(255, 138, 176)
        canvas.drawCircle(sideX, topY, bodyRect.width() * 0.07f, accentPaint)
        canvas.drawLine(
            sideX,
            topY + bodyRect.height() * 0.03f,
            sideX - bodyRect.width() * 0.07f + swing * 0.3f,
            topY + bodyRect.height() * 0.16f + motion.costumeTrailLiftPx * 0.18f,
            ribbonPaint
        )
        canvas.drawLine(
            sideX,
            topY + bodyRect.height() * 0.03f,
            sideX + bodyRect.width() * 0.05f + swing * 0.55f,
            topY + bodyRect.height() * 0.17f + motion.costumeTrailLiftPx * 0.22f,
            ribbonPaint
        )
    }

    private fun drawBellCharm(canvas: Canvas, bodyRect: RectF, isInvincible: Boolean, motion: PlayerSecondaryMotionState) {
        ribbonPaint.color = if (isInvincible) Color.rgb(255, 250, 220) else Color.rgb(172, 108, 42)
        accentPaint.color = if (isInvincible) Color.rgb(255, 248, 180) else Color.rgb(255, 216, 104)
        val collarY = bodyRect.top + bodyRect.height() * 0.40f
        val charmSwing = motion.costumeSwingPx * 0.18f
        canvas.drawArc(
            bodyRect.centerX() - bodyRect.width() * 0.17f,
            collarY - bodyRect.height() * 0.05f,
            bodyRect.centerX() + bodyRect.width() * 0.17f,
            collarY + bodyRect.height() * 0.05f,
            180f,
            180f,
            false,
            ribbonPaint
        )
        canvas.drawCircle(
            bodyRect.centerX() + charmSwing,
            collarY + bodyRect.height() * 0.11f + motion.costumeTrailLiftPx * 0.10f,
            bodyRect.width() * 0.07f,
            accentPaint
        )
    }

    private fun drawLanternPin(canvas: Canvas, bodyRect: RectF, isInvincible: Boolean, motion: PlayerSecondaryMotionState) {
        val pinX = bodyRect.centerX() - bodyRect.width() * 0.16f
        val pinY = bodyRect.top + bodyRect.height() * 0.36f + motion.headOffsetPx * 0.35f
        accentPaint.color = if (isInvincible) Color.rgb(255, 252, 232) else Color.rgb(255, 233, 168)
        ribbonPaint.color = if (isInvincible) Color.rgb(232, 244, 255) else Color.rgb(134, 146, 212)
        canvas.drawRect(
            pinX - bodyRect.width() * 0.05f,
            pinY - bodyRect.height() * 0.04f,
            pinX + bodyRect.width() * 0.05f,
            pinY + bodyRect.height() * 0.06f,
            accentPaint
        )
        canvas.drawLine(
            pinX,
            pinY - bodyRect.height() * 0.10f,
            pinX,
            pinY - bodyRect.height() * 0.03f,
            ribbonPaint
        )
    }

    private fun drawSkySash(canvas: Canvas, bodyRect: RectF, state: PlayerState, isInvincible: Boolean, motion: PlayerSecondaryMotionState) {
        ribbonPaint.color = if (isInvincible) Color.rgb(242, 250, 255) else Color.rgb(150, 196, 255)
        accentPaint.color = if (isInvincible) Color.rgb(255, 246, 210) else Color.rgb(232, 236, 255)
        val topY = bodyRect.top + bodyRect.height() * 0.28f
        val bottomY = bodyRect.bottom - bodyRect.height() * if (state == PlayerState.DUCKING) 0.18f else 0.06f
        val sashSwing = motion.costumeSwingPx * 0.42f
        canvas.drawLine(
            bodyRect.centerX() - bodyRect.width() * 0.14f,
            topY,
            bodyRect.centerX() + bodyRect.width() * 0.16f + sashSwing,
            bottomY - motion.costumeTrailLiftPx * 0.12f,
            ribbonPaint
        )
        canvas.drawCircle(
            bodyRect.centerX() - bodyRect.width() * 0.08f,
            topY + bodyRect.height() * 0.03f + motion.headOffsetPx * 0.25f,
            bodyRect.width() * 0.05f,
            accentPaint
        )
    }
}
