package com.anurag9000.forestrun.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

internal enum class CinematicScene { MENU, RUN, GARDEN, REST }

internal data class CinematicPolishProfile(
    val vignetteAlpha: Int,
    val edgeGlowAlpha: Int,
    val letterboxAlpha: Int,
    val letterboxHeightFraction: Float,
    val centerLiftAlpha: Int,
    val shimmerStrength: Float
)

internal fun buildCinematicPolishProfile(
    scene: CinematicScene,
    emphasis: Float = 0f,
    bloomStrength: Float = 0f
): CinematicPolishProfile {
    val sceneEmphasis = emphasis.coerceIn(0f, 1f)
    val bloom = bloomStrength.coerceIn(0f, 1f)
    return when (scene) {
        CinematicScene.MENU -> CinematicPolishProfile(
            vignetteAlpha = (66 + sceneEmphasis * 18f).toInt().coerceIn(0, 140),
            edgeGlowAlpha = (34 + sceneEmphasis * 22f).toInt().coerceIn(0, 120),
            letterboxAlpha = (44 + sceneEmphasis * 20f).toInt().coerceIn(0, 120),
            letterboxHeightFraction = 0.062f,
            centerLiftAlpha = (18 + sceneEmphasis * 14f).toInt().coerceIn(0, 80),
            shimmerStrength = 0.42f + sceneEmphasis * 0.18f
        )
        CinematicScene.RUN -> CinematicPolishProfile(
            vignetteAlpha = (34 + sceneEmphasis * 14f + bloom * 22f).toInt().coerceIn(0, 120),
            edgeGlowAlpha = (16 + sceneEmphasis * 12f + bloom * 20f).toInt().coerceIn(0, 92),
            letterboxAlpha = (18 + sceneEmphasis * 16f + bloom * 10f).toInt().coerceIn(0, 72),
            letterboxHeightFraction = 0.036f + bloom * 0.008f,
            centerLiftAlpha = (10 + sceneEmphasis * 8f + bloom * 16f).toInt().coerceIn(0, 64),
            shimmerStrength = 0.22f + sceneEmphasis * 0.16f + bloom * 0.20f
        )
        CinematicScene.GARDEN -> CinematicPolishProfile(
            vignetteAlpha = (58 + sceneEmphasis * 20f).toInt().coerceIn(0, 140),
            edgeGlowAlpha = (30 + sceneEmphasis * 24f).toInt().coerceIn(0, 120),
            letterboxAlpha = (40 + sceneEmphasis * 20f).toInt().coerceIn(0, 120),
            letterboxHeightFraction = 0.058f,
            centerLiftAlpha = (20 + sceneEmphasis * 16f).toInt().coerceIn(0, 84),
            shimmerStrength = 0.40f + sceneEmphasis * 0.20f
        )
        CinematicScene.REST -> CinematicPolishProfile(
            vignetteAlpha = (72 + sceneEmphasis * 22f).toInt().coerceIn(0, 156),
            edgeGlowAlpha = (28 + sceneEmphasis * 16f).toInt().coerceIn(0, 96),
            letterboxAlpha = (54 + sceneEmphasis * 18f).toInt().coerceIn(0, 120),
            letterboxHeightFraction = 0.07f,
            centerLiftAlpha = (26 + sceneEmphasis * 18f).toInt().coerceIn(0, 90),
            shimmerStrength = 0.30f + sceneEmphasis * 0.16f
        )
    }
}

internal class CinematicOverlayRenderer {
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerLiftPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letterboxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letterboxRect = RectF()
    private var shaderWidth = Float.NaN
    private var shaderHeight = Float.NaN
    private var shaderGlowColor = 0
    private var shaderCenterYFraction = Float.NaN
    private val edgeColors = IntArray(3)
    private val centerColors = IntArray(3)
    private val vignetteColors = intArrayOf(
        Color.argb(255, 0, 0, 0),
        Color.argb(85, 0, 0, 0),
        Color.argb(255, 0, 0, 0)
    )
    private val edgePositions = floatArrayOf(0f, 0.46f, 1f)
    private val centerPositions = floatArrayOf(0f, 0.5f, 1f)
    private val vignettePositions = floatArrayOf(0f, 0.52f, 1f)
    internal var shaderRebuildCountForTest: Int = 0
        private set

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        profile: CinematicPolishProfile,
        elapsedSeconds: Float,
        glowColor: Int,
        centerYFraction: Float = 0.46f
    ) {
        val shimmerPulse = cinematicShimmerPulse(
            elapsedSeconds = elapsedSeconds,
            shimmerStrength = profile.shimmerStrength,
            reducedMotion = FeedbackSettings.reducedMotion
        )
        val topLetterbox = (height * profile.letterboxHeightFraction).coerceAtLeast(0f)
        val bottomLetterboxTop = height - topLetterbox
        val normalizedCenter = centerYFraction.coerceIn(0.2f, 0.8f)
        ensureShaders(width, height, glowColor, normalizedCenter)

        letterboxPaint.color = Color.argb(profile.letterboxAlpha, 10, 12, 18)
        letterboxRect.set(0f, 0f, width, topLetterbox)
        canvas.drawRect(letterboxRect, letterboxPaint)
        letterboxRect.set(0f, bottomLetterboxTop, width, height)
        canvas.drawRect(letterboxRect, letterboxPaint)

        val glowAlpha = (profile.edgeGlowAlpha * (0.82f + shimmerPulse * 0.18f)).toInt().coerceIn(0, 255)
        edgeGlowPaint.alpha = glowAlpha
        canvas.drawRect(0f, 0f, width, height, edgeGlowPaint)

        val centerAlpha = (profile.centerLiftAlpha * (0.78f + shimmerPulse * 0.22f)).toInt().coerceIn(0, 255)
        val centerY = height * normalizedCenter
        centerLiftPaint.alpha = centerAlpha
        canvas.drawRect(0f, centerY - height * 0.18f, width, centerY + height * 0.22f, centerLiftPaint)

        vignettePaint.alpha = profile.vignetteAlpha
        canvas.drawRect(0f, 0f, width, height, vignettePaint)
    }

    private fun ensureShaders(
        width: Float,
        height: Float,
        glowColor: Int,
        centerYFraction: Float
    ) {
        if (width == shaderWidth &&
            height == shaderHeight &&
            glowColor == shaderGlowColor &&
            centerYFraction == shaderCenterYFraction
        ) return

        val red = Color.red(glowColor)
        val green = Color.green(glowColor)
        val blue = Color.blue(glowColor)
        edgeColors[0] = Color.argb(255, red, green, blue)
        edgeColors[1] = Color.argb(0, red, green, blue)
        edgeColors[2] = Color.argb(128, red, green, blue)
        centerColors[0] = Color.argb(0, red, green, blue)
        centerColors[1] = Color.argb(255, red, green, blue)
        centerColors[2] = Color.argb(0, red, green, blue)
        edgePositions[1] = centerYFraction

        edgeGlowPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height,
            edgeColors,
            edgePositions,
            Shader.TileMode.CLAMP
        )
        val centerY = height * centerYFraction
        centerLiftPaint.shader = LinearGradient(
            0f,
            centerY - height * 0.18f,
            0f,
            centerY + height * 0.22f,
            centerColors,
            centerPositions,
            Shader.TileMode.CLAMP
        )
        vignettePaint.shader = LinearGradient(
            0f,
            0f,
            width,
            height,
            vignetteColors,
            vignettePositions,
            Shader.TileMode.CLAMP
        )

        shaderWidth = width
        shaderHeight = height
        shaderGlowColor = glowColor
        shaderCenterYFraction = centerYFraction
        shaderRebuildCountForTest++
    }
}
