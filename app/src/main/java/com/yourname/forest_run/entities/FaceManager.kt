package com.yourname.forest_run.entities

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.sin

/**
 * Lightweight vector face overlay so the runner reads emotionally even when the
 * imported sprite art does not expose separate facial layers.
 */
class FaceManager {

    private var blinkTimer = 0f
    private var blinkCooldown = 1.4f

    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(34, 32, 28)
        style = Paint.Style.FILL
    }
    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 52, 70)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3f
    }
    private val bloomEyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(250, 250, 255)
        style = Paint.Style.FILL
    }

    fun update(deltaTime: Float) {
        blinkTimer += deltaTime
    }

    fun draw(
        canvas: Canvas,
        bodyRect: RectF,
        state: PlayerState,
        velocityY: Float,
        isInvincible: Boolean
    ) {
        val faceCenterX = bodyRect.centerX()
        val faceCenterY = bodyRect.top + bodyRect.height() * 0.34f
        val eyeOffsetX = bodyRect.width() * 0.12f
        val eyeY = faceCenterY
        val eyeW = bodyRect.width() * 0.08f
        val eyeH = bodyRect.height() * 0.05f
        val mouthY = bodyRect.top + bodyRect.height() * 0.47f

        val shouldBlink = state == PlayerState.RUNNING && (blinkTimer % blinkCooldown) < 0.09f
        val eyeHeightFactor = when {
            shouldBlink -> 0.18f
            state == PlayerState.DUCKING -> 0.28f
            state == PlayerState.APEX -> 0.75f
            state == PlayerState.BLOOM -> 1.0f
            state == PlayerState.STUMBLE -> 0.9f
            state == PlayerState.REST -> 0.18f
            velocityY < -450f -> 0.85f
            else -> 0.58f
        }

        val activeEyePaint = if (isInvincible || state == PlayerState.BLOOM) bloomEyePaint else eyePaint
        val actualEyeH = eyeH * eyeHeightFactor
        canvas.drawRoundRect(
            faceCenterX - eyeOffsetX - eyeW,
            eyeY - actualEyeH,
            faceCenterX - eyeOffsetX + eyeW,
            eyeY + actualEyeH,
            eyeW,
            eyeW,
            activeEyePaint
        )
        canvas.drawRoundRect(
            faceCenterX + eyeOffsetX - eyeW,
            eyeY - actualEyeH,
            faceCenterX + eyeOffsetX + eyeW,
            eyeY + actualEyeH,
            eyeW,
            eyeW,
            activeEyePaint
        )

        mouthPaint.color = when {
            state == PlayerState.BLOOM -> Color.rgb(210, 120, 255)
            state == PlayerState.STUMBLE -> Color.rgb(180, 72, 72)
            state == PlayerState.REST -> Color.rgb(110, 80, 120)
            else -> Color.rgb(120, 52, 70)
        }

        when (state) {
            PlayerState.RUNNING -> drawSmile(canvas, faceCenterX, mouthY, bodyRect.width() * 0.12f, 7f)
            PlayerState.JUMP_START -> drawFlatMouth(canvas, faceCenterX, mouthY, bodyRect.width() * 0.10f)
            PlayerState.JUMPING -> drawOpenMouth(canvas, faceCenterX, mouthY - 2f, bodyRect.width() * 0.10f, bodyRect.height() * 0.05f)
            PlayerState.APEX -> drawOpenMouth(canvas, faceCenterX, mouthY - 2f, bodyRect.width() * 0.11f, bodyRect.height() * 0.07f)
            PlayerState.FALLING -> drawOpenMouth(canvas, faceCenterX, mouthY, bodyRect.width() * 0.12f, bodyRect.height() * 0.08f)
            PlayerState.LANDING -> drawSmile(canvas, faceCenterX, mouthY + 2f, bodyRect.width() * 0.11f, 6f)
            PlayerState.DUCKING -> drawFlatMouth(canvas, faceCenterX, mouthY + 1f, bodyRect.width() * 0.12f)
            PlayerState.BLOOM -> drawBloomMouth(canvas, faceCenterX, mouthY + sin(blinkTimer * 8f) * 1.5f, bodyRect.width() * 0.13f)
            PlayerState.STUMBLE -> drawOpenMouth(canvas, faceCenterX, mouthY + 2f, bodyRect.width() * 0.10f, bodyRect.height() * 0.10f)
            PlayerState.REST -> drawFrown(canvas, faceCenterX, mouthY + 4f, bodyRect.width() * 0.11f, 5f)
        }
    }

    private fun drawSmile(canvas: Canvas, cx: Float, y: Float, halfWidth: Float, curve: Float) {
        canvas.drawArc(cx - halfWidth, y - curve, cx + halfWidth, y + curve, 10f, 160f, false, mouthPaint)
    }

    private fun drawFrown(canvas: Canvas, cx: Float, y: Float, halfWidth: Float, curve: Float) {
        canvas.drawArc(cx - halfWidth, y - curve, cx + halfWidth, y + curve, 190f, 160f, false, mouthPaint)
    }

    private fun drawFlatMouth(canvas: Canvas, cx: Float, y: Float, halfWidth: Float) {
        canvas.drawLine(cx - halfWidth, y, cx + halfWidth, y, mouthPaint)
    }

    private fun drawOpenMouth(canvas: Canvas, cx: Float, y: Float, halfWidth: Float, halfHeight: Float) {
        canvas.drawOval(cx - halfWidth, y - halfHeight, cx + halfWidth, y + halfHeight, mouthPaint)
    }

    private fun drawBloomMouth(canvas: Canvas, cx: Float, y: Float, halfWidth: Float) {
        canvas.drawArc(cx - halfWidth, y - 8f, cx + halfWidth, y + 8f, 0f, 180f, false, mouthPaint)
    }
}
