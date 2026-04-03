package com.yourname.forest_run.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.sin

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

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        profile: CinematicPolishProfile,
        elapsedSeconds: Float,
        glowColor: Int,
        centerYFraction: Float = 0.46f
    ) {
        val shimmerPulse = 0.55f + 0.45f * sin(elapsedSeconds * (1.2f + profile.shimmerStrength))
        val topLetterbox = (height * profile.letterboxHeightFraction).coerceAtLeast(0f)
        val bottomLetterboxTop = height - topLetterbox

        letterboxPaint.color = Color.argb(profile.letterboxAlpha, 10, 12, 18)
        letterboxRect.set(0f, 0f, width, topLetterbox)
        canvas.drawRect(letterboxRect, letterboxPaint)
        letterboxRect.set(0f, bottomLetterboxTop, width, height)
        canvas.drawRect(letterboxRect, letterboxPaint)

        val glowAlpha = (profile.edgeGlowAlpha * (0.82f + shimmerPulse * 0.18f)).toInt().coerceIn(0, 255)
        edgeGlowPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height,
            intArrayOf(
                Color.argb(glowAlpha, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                Color.argb(0, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                Color.argb(glowAlpha / 2, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor))
            ),
            floatArrayOf(0f, centerYFraction.coerceIn(0.2f, 0.8f), 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width, height, edgeGlowPaint)

        val centerAlpha = (profile.centerLiftAlpha * (0.78f + shimmerPulse * 0.22f)).toInt().coerceIn(0, 255)
        val centerY = height * centerYFraction.coerceIn(0.2f, 0.8f)
        centerLiftPaint.shader = LinearGradient(
            0f,
            centerY - height * 0.18f,
            0f,
            centerY + height * 0.22f,
            intArrayOf(
                Color.argb(0, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                Color.argb(centerAlpha, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                Color.argb(0, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor))
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, centerY - height * 0.18f, width, centerY + height * 0.22f, centerLiftPaint)

        vignettePaint.shader = LinearGradient(
            0f,
            0f,
            width,
            height,
            intArrayOf(
                Color.argb(profile.vignetteAlpha, 0, 0, 0),
                Color.argb(profile.vignetteAlpha / 3, 0, 0, 0),
                Color.argb(profile.vignetteAlpha, 0, 0, 0)
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width, height, vignettePaint)
    }
}
