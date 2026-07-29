#!/usr/bin/env python3
"Remove confirmed recurring allocations from Forest Run render/update hot paths."

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parent.parent


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(path: str, old: str, new: str, label: str) -> None:
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    write(path, text.replace(old, new, 1))


def regex_replace_once(path: str, pattern: str, replacement: str, label: str) -> None:
    text = read(path)
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.DOTALL)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    write(path, updated)


LIGHTING = '''package com.anurag9000.forestrun.engine

import android.graphics.Color

data class RunLightingIdentity(
    var canopyNearColor: Int = 0,
    var canopyFarColor: Int = 0,
    var mistColor: Int = 0,
    var horizonGlowColor: Int = 0,
    var glowMoteColor: Int = 0
)

enum class SanctuaryLightingScene {
    MENU,
    GARDEN,
    REST
}

data class SanctuaryLightingIdentity(
    val canopyColor: Int,
    val mistColor: Int,
    val fireflyColor: Int,
    val lanternOuterColor: Int,
    val lanternInnerColor: Int,
    val groundGlowColor: Int,
    val bloomPatchColor: Int
)

internal fun resolveRunLightingIdentity(
    target: RunLightingIdentity,
    nightFactor: Float,
    bloomStrength: Float
): RunLightingIdentity {
    val night = nightFactor.coerceIn(0f, 1f)
    val bloom = bloomStrength.coerceIn(0f, 1f)
    target.canopyNearColor = Color.rgb(
        (18f + bloom * 16f).toInt().coerceAtMost(255),
        (28f + night * 24f + bloom * 10f).toInt().coerceAtMost(255),
        (24f + night * 18f + bloom * 8f).toInt().coerceAtMost(255)
    )
    target.canopyFarColor = Color.rgb(
        (10f + bloom * 10f).toInt().coerceAtMost(255),
        (16f + night * 14f + bloom * 8f).toInt().coerceAtMost(255),
        (14f + night * 12f + bloom * 8f).toInt().coerceAtMost(255)
    )
    target.mistColor = Color.rgb(
        (214f + bloom * 24f).toInt().coerceAtMost(255),
        (226f + night * 10f + bloom * 18f).toInt().coerceAtMost(255),
        (228f + night * 8f + bloom * 10f).toInt().coerceAtMost(255)
    )
    target.horizonGlowColor = Color.rgb(
        (236f + bloom * 18f).toInt().coerceAtMost(255),
        (186f + night * 22f + bloom * 28f).toInt().coerceAtMost(255),
        (118f + bloom * 34f).toInt().coerceAtMost(255)
    )
    target.glowMoteColor = Color.rgb(
        (236f + bloom * 18f).toInt().coerceAtMost(255),
        (226f + bloom * 20f).toInt().coerceAtMost(255),
        (164f + night * 12f + bloom * 44f).toInt().coerceAtMost(255)
    )
    return target
}

internal fun buildRunLightingIdentity(
    nightFactor: Float,
    bloomStrength: Float
): RunLightingIdentity = resolveRunLightingIdentity(
    target = RunLightingIdentity(),
    nightFactor = nightFactor,
    bloomStrength = bloomStrength
)

private val menuSanctuaryLighting = SanctuaryLightingIdentity(
    canopyColor = Color.rgb(26, 42, 34),
    mistColor = Color.rgb(232, 246, 236),
    fireflyColor = Color.rgb(252, 246, 182),
    lanternOuterColor = Color.rgb(255, 235, 168),
    lanternInnerColor = Color.rgb(255, 242, 196),
    groundGlowColor = Color.rgb(240, 246, 184),
    bloomPatchColor = Color.rgb(255, 242, 196)
)
private val gardenSanctuaryLighting = SanctuaryLightingIdentity(
    canopyColor = Color.rgb(24, 44, 38),
    mistColor = Color.rgb(236, 248, 236),
    fireflyColor = Color.rgb(252, 246, 180),
    lanternOuterColor = Color.rgb(255, 234, 170),
    lanternInnerColor = Color.rgb(255, 242, 192),
    groundGlowColor = Color.rgb(240, 246, 186),
    bloomPatchColor = Color.rgb(255, 240, 186)
)
private val restSanctuaryLighting = SanctuaryLightingIdentity(
    canopyColor = Color.rgb(20, 28, 34),
    mistColor = Color.rgb(228, 240, 236),
    fireflyColor = Color.rgb(230, 242, 196),
    lanternOuterColor = Color.rgb(255, 236, 170),
    lanternInnerColor = Color.rgb(255, 242, 192),
    groundGlowColor = Color.rgb(236, 240, 178),
    bloomPatchColor = Color.rgb(238, 236, 186)
)

internal fun buildSanctuaryLightingIdentity(
    scene: SanctuaryLightingScene
): SanctuaryLightingIdentity = when (scene) {
    SanctuaryLightingScene.MENU -> menuSanctuaryLighting
    SanctuaryLightingScene.GARDEN -> gardenSanctuaryLighting
    SanctuaryLightingScene.REST -> restSanctuaryLighting
}
'''

CINEMATIC = '''package com.anurag9000.forestrun.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

internal enum class CinematicScene { MENU, RUN, GARDEN, REST }

internal data class CinematicPolishProfile(
    var vignetteAlpha: Int = 0,
    var edgeGlowAlpha: Int = 0,
    var letterboxAlpha: Int = 0,
    var letterboxHeightFraction: Float = 0f,
    var centerLiftAlpha: Int = 0,
    var shimmerStrength: Float = 0f
)

internal fun resolveCinematicPolishProfile(
    target: CinematicPolishProfile,
    scene: CinematicScene,
    emphasis: Float = 0f,
    bloomStrength: Float = 0f
): CinematicPolishProfile {
    val sceneEmphasis = emphasis.coerceIn(0f, 1f)
    val bloom = bloomStrength.coerceIn(0f, 1f)
    when (scene) {
        CinematicScene.MENU -> {
            target.vignetteAlpha = (66 + sceneEmphasis * 18f).toInt().coerceIn(0, 140)
            target.edgeGlowAlpha = (34 + sceneEmphasis * 22f).toInt().coerceIn(0, 120)
            target.letterboxAlpha = (44 + sceneEmphasis * 20f).toInt().coerceIn(0, 120)
            target.letterboxHeightFraction = 0.062f
            target.centerLiftAlpha = (18 + sceneEmphasis * 14f).toInt().coerceIn(0, 80)
            target.shimmerStrength = 0.42f + sceneEmphasis * 0.18f
        }
        CinematicScene.RUN -> {
            target.vignetteAlpha = (34 + sceneEmphasis * 14f + bloom * 22f).toInt().coerceIn(0, 120)
            target.edgeGlowAlpha = (16 + sceneEmphasis * 12f + bloom * 20f).toInt().coerceIn(0, 92)
            target.letterboxAlpha = (18 + sceneEmphasis * 16f + bloom * 10f).toInt().coerceIn(0, 72)
            target.letterboxHeightFraction = 0.036f + bloom * 0.008f
            target.centerLiftAlpha = (10 + sceneEmphasis * 8f + bloom * 16f).toInt().coerceIn(0, 64)
            target.shimmerStrength = 0.22f + sceneEmphasis * 0.16f + bloom * 0.20f
        }
        CinematicScene.GARDEN -> {
            target.vignetteAlpha = (58 + sceneEmphasis * 20f).toInt().coerceIn(0, 140)
            target.edgeGlowAlpha = (30 + sceneEmphasis * 24f).toInt().coerceIn(0, 120)
            target.letterboxAlpha = (40 + sceneEmphasis * 20f).toInt().coerceIn(0, 120)
            target.letterboxHeightFraction = 0.058f
            target.centerLiftAlpha = (20 + sceneEmphasis * 16f).toInt().coerceIn(0, 84)
            target.shimmerStrength = 0.40f + sceneEmphasis * 0.20f
        }
        CinematicScene.REST -> {
            target.vignetteAlpha = (72 + sceneEmphasis * 22f).toInt().coerceIn(0, 156)
            target.edgeGlowAlpha = (28 + sceneEmphasis * 16f).toInt().coerceIn(0, 96)
            target.letterboxAlpha = (54 + sceneEmphasis * 18f).toInt().coerceIn(0, 120)
            target.letterboxHeightFraction = 0.07f
            target.centerLiftAlpha = (26 + sceneEmphasis * 18f).toInt().coerceIn(0, 90)
            target.shimmerStrength = 0.30f + sceneEmphasis * 0.16f
        }
    }
    return target
}

internal fun buildCinematicPolishProfile(
    scene: CinematicScene,
    emphasis: Float = 0f,
    bloomStrength: Float = 0f
): CinematicPolishProfile = resolveCinematicPolishProfile(
    target = CinematicPolishProfile(),
    scene = scene,
    emphasis = emphasis,
    bloomStrength = bloomStrength
)

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
'''

BLOOM_POWER = '''package com.anurag9000.forestrun.engine

data class BloomPowerPresentationState(
    var tier: Int = 0,
    var playerScaleBoost: Float = 0f,
    var auraAlpha: Int = 0,
    var surgeStrength: Float = 0f
)

object BloomPowerPresentation {

    fun resolveInto(
        target: BloomPowerPresentationState,
        secondsRemaining: Float,
        conversionsInBurst: Int,
        recentSurgeFraction: Float
    ): BloomPowerPresentationState {
        val burst = conversionsInBurst.coerceAtLeast(0)
        val timeFraction = (secondsRemaining / GameConstants.BLOOM_DURATION_S).coerceIn(0f, 1f)
        val surge = recentSurgeFraction.coerceIn(0f, 1f)
        val tier = when {
            burst >= 6 -> 3
            burst >= 3 -> 2
            burst >= 1 -> 1
            else -> 0
        }
        target.tier = tier
        target.playerScaleBoost = (
            tier * 0.016f +
                timeFraction * 0.014f +
                surge * (0.016f + tier * 0.004f)
            ).coerceIn(0f, 0.11f)
        target.auraAlpha = (
            80f +
                tier * 34f +
                timeFraction * 24f +
                surge * 56f
            ).toInt().coerceIn(0, 220)
        target.surgeStrength = (
            0.24f +
                tier * 0.16f +
                surge * 0.34f
            ).coerceIn(0f, 1f)
        return target
    }

    fun resolve(
        secondsRemaining: Float,
        conversionsInBurst: Int,
        recentSurgeFraction: Float
    ): BloomPowerPresentationState = resolveInto(
        target = BloomPowerPresentationState(),
        secondsRemaining = secondsRemaining,
        conversionsInBurst = conversionsInBurst,
        recentSurgeFraction = recentSurgeFraction
    )
}
'''

BLOOM_HUD = '''package com.anurag9000.forestrun.engine

enum class BloomPresentationMode {
    CHARGING,
    READY,
    ACTIVE,
    AFTERGLOW
}

data class BloomHudPresentation(
    var mode: BloomPresentationMode = BloomPresentationMode.CHARGING,
    var labelText: String = "bloom",
    var statusText: String = "0/1",
    var emphasis: Float = 0f
) {
    internal var cachedModeOrdinal: Int = -1
    internal var cachedMeter: Int = Int.MIN_VALUE
    internal var cachedTarget: Int = Int.MIN_VALUE
    internal var cachedSecondsTenths: Int = Int.MIN_VALUE
    internal var cachedTotalConversions: Int = Int.MIN_VALUE
    internal var cachedBurstConversions: Int = Int.MIN_VALUE
}

object BloomPresentation {

    fun resolveInto(
        target: BloomHudPresentation,
        bloomMeter: Int,
        seedTarget: Int,
        isActive: Boolean,
        secondsRemaining: Float,
        totalConversions: Int,
        burstConversions: Int,
        recentAfterglow: Float
    ): BloomHudPresentation {
        val safeTarget = seedTarget.coerceAtLeast(1)
        val safeMeter = bloomMeter.coerceIn(0, safeTarget)
        val safeAfterglow = recentAfterglow.coerceIn(0f, 1f)
        val safeBurstConversions = burstConversions.coerceAtLeast(0)
        val safeTotalConversions = totalConversions.coerceAtLeast(0)
        val secondsTenths = (secondsRemaining.coerceAtLeast(0f) * 10f + 0.5f).toInt()
        val mode = when {
            isActive -> BloomPresentationMode.ACTIVE
            safeAfterglow > 0.01f -> BloomPresentationMode.AFTERGLOW
            safeMeter >= safeTarget - 1 -> BloomPresentationMode.READY
            else -> BloomPresentationMode.CHARGING
        }
        val textChanged =
            target.cachedModeOrdinal != mode.ordinal ||
                target.cachedMeter != safeMeter ||
                target.cachedTarget != safeTarget ||
                target.cachedSecondsTenths != secondsTenths ||
                target.cachedTotalConversions != safeTotalConversions ||
                target.cachedBurstConversions != safeBurstConversions

        if (textChanged) {
            when (mode) {
                BloomPresentationMode.ACTIVE -> {
                    target.labelText = "BLOOM"
                    target.statusText = if (safeTotalConversions > 0) {
                        "${formatTenths(secondsTenths)}  •  $safeTotalConversions converts  •  hold the light"
                    } else {
                        "${formatTenths(secondsTenths)}  •  world open"
                    }
                }
                BloomPresentationMode.AFTERGLOW -> {
                    target.labelText = "AFTERGLOW"
                    target.statusText = if (safeBurstConversions > 0) {
                        "$safeBurstConversions converts  •  the light is still hanging here"
                    } else {
                        "Bloom has eased, but the light has not fully left"
                    }
                }
                BloomPresentationMode.READY -> {
                    target.labelText = "READY"
                    target.statusText = "1 more seed  •  Bloom is waiting"
                }
                BloomPresentationMode.CHARGING -> {
                    target.labelText = "bloom"
                    target.statusText = "$safeMeter/$safeTarget"
                }
            }
            target.cachedModeOrdinal = mode.ordinal
            target.cachedMeter = safeMeter
            target.cachedTarget = safeTarget
            target.cachedSecondsTenths = secondsTenths
            target.cachedTotalConversions = safeTotalConversions
            target.cachedBurstConversions = safeBurstConversions
        }

        target.mode = mode
        target.emphasis = when (mode) {
            BloomPresentationMode.ACTIVE -> {
                val timeFraction =
                    (secondsRemaining / GameConstants.BLOOM_DURATION_S).coerceIn(0f, 1f)
                0.72f + timeFraction * 0.28f
            }
            BloomPresentationMode.AFTERGLOW -> safeAfterglow
            BloomPresentationMode.READY -> 0.76f
            BloomPresentationMode.CHARGING -> safeMeter / safeTarget.toFloat()
        }
        return target
    }

    fun hudPresentation(
        bloomMeter: Int,
        seedTarget: Int,
        isActive: Boolean,
        secondsRemaining: Float,
        totalConversions: Int,
        burstConversions: Int,
        recentAfterglow: Float
    ): BloomHudPresentation = resolveInto(
        target = BloomHudPresentation(),
        bloomMeter = bloomMeter,
        seedTarget = seedTarget,
        isActive = isActive,
        secondsRemaining = secondsRemaining,
        totalConversions = totalConversions,
        burstConversions = burstConversions,
        recentAfterglow = recentAfterglow
    )

    private fun formatTenths(tenths: Int): String =
        "${tenths / 10}.${tenths % 10}s"
}
'''

HOT_PATH_TEST = '''package com.anurag9000.forestrun.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RenderHotPathReuseTest {

    @Test
    fun `mutable presentation resolvers reuse caller-owned state`() {
        val lighting = RunLightingIdentity()
        val cinematic = CinematicPolishProfile()
        val bloomPower = BloomPowerPresentationState()
        val bloomHud = BloomHudPresentation()

        assertSame(
            lighting,
            resolveRunLightingIdentity(lighting, nightFactor = 0.6f, bloomStrength = 0.8f)
        )
        assertSame(
            cinematic,
            resolveCinematicPolishProfile(
                cinematic,
                scene = CinematicScene.RUN,
                emphasis = 0.4f,
                bloomStrength = 0.7f
            )
        )
        assertSame(
            bloomPower,
            BloomPowerPresentation.resolveInto(
                bloomPower,
                secondsRemaining = 4.5f,
                conversionsInBurst = 3,
                recentSurgeFraction = 0.5f
            )
        )
        assertSame(
            bloomHud,
            BloomPresentation.resolveInto(
                bloomHud,
                bloomMeter = 0,
                seedTarget = 8,
                isActive = true,
                secondsRemaining = 4.34f,
                totalConversions = 2,
                burstConversions = 2,
                recentAfterglow = 0f
            )
        )

        assertTrue(lighting.horizonGlowColor != 0)
        assertTrue(cinematic.vignetteAlpha > 0)
        assertEquals(2, bloomPower.tier)
        assertEquals(BloomPresentationMode.ACTIVE, bloomHud.mode)
    }

    @Test
    fun `bloom HUD preserves status string within the same displayed tenth`() {
        val target = BloomHudPresentation()
        BloomPresentation.resolveInto(
            target,
            bloomMeter = 0,
            seedTarget = 8,
            isActive = true,
            secondsRemaining = 4.31f,
            totalConversions = 2,
            burstConversions = 2,
            recentAfterglow = 0f
        )
        val cached = target.statusText

        BloomPresentation.resolveInto(
            target,
            bloomMeter = 0,
            seedTarget = 8,
            isActive = true,
            secondsRemaining = 4.34f,
            totalConversions = 2,
            burstConversions = 2,
            recentAfterglow = 0f
        )
        assertSame(cached, target.statusText)

        BloomPresentation.resolveInto(
            target,
            bloomMeter = 0,
            seedTarget = 8,
            isActive = true,
            secondsRemaining = 4.21f,
            totalConversions = 2,
            burstConversions = 2,
            recentAfterglow = 0f
        )
        assertNotSame(cached, target.statusText)
        assertTrue(target.statusText.contains("4.2s"))
    }

    @Test
    fun `parallax shaders stay stable across unchanged frames`() {
        val background = ParallaxBackground(screenWidth = 320, screenHeight = 180)
        background.applyBiomeColours(
            skyTop = Color.rgb(150, 200, 245),
            skyBottom = Color.rgb(220, 238, 252),
            groundColour = Color.rgb(72, 142, 70),
            foliage = Color.rgb(42, 104, 58)
        )
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        background.update(1f / 60f, GameConstants.BASE_SCROLL_SPEED)
        background.draw(canvas)
        val firstCount = background.dynamicShaderRebuildCountForTest
        assertTrue(firstCount > 0)

        repeat(12) {
            background.update(1f / 60f, GameConstants.BASE_SCROLL_SPEED)
            background.draw(canvas)
        }
        assertEquals(firstCount, background.dynamicShaderRebuildCountForTest)

        background.applyBiomeColours(
            skyTop = Color.rgb(30, 42, 72),
            skyBottom = Color.rgb(68, 76, 106),
            groundColour = Color.rgb(56, 88, 58),
            foliage = Color.rgb(18, 52, 34)
        )
        background.draw(canvas)
        assertTrue(background.dynamicShaderRebuildCountForTest > firstCount)
    }
}
'''


def patch_parallax() -> None:
    path = "app/src/main/java/com/anurag9000/forestrun/engine/ParallaxBackground.kt"
    regex_replace_once(
        path,
        r'''internal data class ParallaxAtmosphereProfile\([\s\S]*?\n}\n\n/\*\*\n \* Manages 4 parallax layers''',
        '''internal data class ParallaxAtmosphereProfile(
    var worldScale: Float = 1f,
    var driftScale: Float = 1f,
    var gustStrength: Float = 0f,
    var worldSwayAmplitude: Float = 4f,
    var leafCount: Int = 0,
    var leafBackfillCount: Int = 0,
    var petalCount: Int = 0,
    var petalTrailCount: Int = 0,
    var fireflyCount: Int = 0,
    var glowMoteCount: Int = 0,
    var ribbonCount: Int = 0,
    var mistBandCount: Int = 0,
    var windRibbonAlpha: Int = 0,
    var mistBandAlpha: Int = 0,
    var canopyShadowAlpha: Int = 0,
    var horizonGlowAlpha: Int = 0,
    var biomeSkyAlpha: Int = 0,
    var foliageWashAlpha: Int = 0,
    var nightFactor: Float = 0f
)

internal fun resolveParallaxAtmosphereProfile(
    target: ParallaxAtmosphereProfile,
    scrollSpeed: Float,
    bloomStrength: Float,
    skyTop: Int,
    skyBottom: Int
): ParallaxAtmosphereProfile {
    val speedRatio = (scrollSpeed / GameConstants.BASE_SCROLL_SPEED).coerceIn(0.7f, 2.1f)
    val skyBrightness = (
        Color.red(skyTop) + Color.green(skyTop) + Color.blue(skyTop) +
            Color.red(skyBottom) + Color.green(skyBottom) + Color.blue(skyBottom)
        ) / 6f
    val nightFactor = (1f - skyBrightness / 255f).coerceIn(0f, 1f)
    val bloom = bloomStrength.coerceIn(0f, 1f)
    val speedLift = (speedRatio - 1f).coerceAtLeast(0f)

    target.worldScale =
        (1f + speedLift * 0.012f + bloom * 0.026f + nightFactor * 0.008f).coerceAtMost(1.065f)
    target.driftScale =
        (1f + speedLift * 0.28f + bloom * 0.18f + nightFactor * 0.10f).coerceAtMost(1.65f)
    target.gustStrength =
        (0.18f + speedLift * 0.24f + bloom * 0.16f + nightFactor * 0.12f).coerceIn(0f, 0.75f)
    target.worldSwayAmplitude =
        (4f + speedLift * 4.5f + bloom * 4f + nightFactor * 2.2f).coerceIn(4f, 16f)
    target.leafCount = (5 + speedLift * 6f + bloom * 4f).toInt().coerceAtLeast(4)
    target.leafBackfillCount =
        (3 + speedLift * 4f + nightFactor * 3f + bloom * 2f).toInt().coerceAtLeast(3)
    target.petalCount = (3 + bloom * 7f + nightFactor * 2f).toInt().coerceAtLeast(2)
    target.petalTrailCount = (2 + bloom * 5f + speedLift * 2.5f).toInt().coerceAtLeast(2)
    target.fireflyCount =
        (nightFactor * 8f + bloom * 4f).toInt().coerceAtLeast(if (nightFactor > 0.45f) 3 else 0)
    target.glowMoteCount =
        (2 + nightFactor * 5f + bloom * 5f).toInt().coerceAtLeast(
            if (bloom > 0.2f || nightFactor > 0.4f) 3 else 1
        )
    target.ribbonCount = (3 + speedLift * 2f + bloom * 1.5f).toInt().coerceIn(3, 6)
    target.mistBandCount = (2 + nightFactor * 2.2f + bloom * 1.4f).toInt().coerceIn(2, 5)
    target.windRibbonAlpha = (18f + speedLift * 34f + bloom * 24f).toInt().coerceIn(0, 110)
    target.mistBandAlpha = (16f + nightFactor * 38f + bloom * 20f).toInt().coerceIn(0, 120)
    target.canopyShadowAlpha =
        (22f + nightFactor * 48f + speedLift * 12f).toInt().coerceIn(0, 120)
    target.horizonGlowAlpha = (36f + bloom * 92f + speedLift * 22f).toInt().coerceIn(0, 180)
    target.biomeSkyAlpha = (28f + nightFactor * 42f).toInt().coerceIn(0, 120)
    target.foliageWashAlpha = (20f + nightFactor * 35f + bloom * 18f).toInt().coerceIn(0, 96)
    target.nightFactor = nightFactor
    return target
}

internal fun buildParallaxAtmosphereProfile(
    scrollSpeed: Float,
    bloomStrength: Float,
    skyTop: Int,
    skyBottom: Int
): ParallaxAtmosphereProfile = resolveParallaxAtmosphereProfile(
    target = ParallaxAtmosphereProfile(),
    scrollSpeed = scrollSpeed,
    bloomStrength = bloomStrength,
    skyTop = skyTop,
    skyBottom = skyBottom
)

/**
 * Manages 4 parallax layers''',
        "mutable atmosphere resolver",
    )

    replace_once(
        path,
        "    private val mistBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }\n",
        "",
        "remove obsolete per-band mist paint",
    )

    replace_once(
        path,
        '''    private val glowMotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
''',
        '''    private val glowMotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val reusableAtmosphere = ParallaxAtmosphereProfile()
    private val reusableLighting = RunLightingIdentity()
    private val horizonGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val mistBandPaints = Array(5) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    }
    private val skyShaderColors = IntArray(2)
    private val canopyShaderColors = IntArray(3)
    private val mistShaderColors = IntArray(3)
    private val horizonShaderColors = intArrayOf(
        Color.argb(0, 255, 214, 146),
        Color.argb(255, 255, 206, 132)
    )
    private val canopyPositions = floatArrayOf(0f, 0.5f, 1f)
    private val mistPositions = floatArrayOf(0f, 0.55f, 1f)
    private var staticAtmosphereShadersReady = false
    private var cachedSkyTopShaderColor = Int.MIN_VALUE
    private var cachedSkyBottomShaderColor = Int.MIN_VALUE
    private var cachedCanopyNearShaderColor = Int.MIN_VALUE
    private var cachedCanopyFarShaderColor = Int.MIN_VALUE
    private var cachedMistShaderColor = Int.MIN_VALUE
    internal var dynamicShaderRebuildCountForTest: Int = 0
        private set
''',
        "reusable parallax hot-path fields",
    )

    replace_once(
        path,
        '''    fun draw(canvas: Canvas) {
        val atmosphere = buildParallaxAtmosphereProfile(
            scrollSpeed = currentScrollSpeed,
            bloomStrength = maxOf(bloomLevel, bloomAfterglowLevel * 0.48f),
            skyTop = skyOverlayTop.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[0],
            skyBottom = skyOverlayBottom.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[0]
        )
        val gustPulse = 0.55f + 0.45f * sin(ambienceTime * (0.78f + atmosphere.gustStrength * 0.55f))
''',
        '''    fun draw(canvas: Canvas) {
        val topColor =
            skyOverlayTop.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[0]
        val bottomColor =
            skyOverlayBottom.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[0]
        val bloomStrength = maxOf(bloomLevel, bloomAfterglowLevel * 0.48f)
        val atmosphere = resolveParallaxAtmosphereProfile(
            target = reusableAtmosphere,
            scrollSpeed = currentScrollSpeed,
            bloomStrength = bloomStrength,
            skyTop = topColor,
            skyBottom = bottomColor
        )
        val lighting = resolveRunLightingIdentity(
            target = reusableLighting,
            nightFactor = atmosphere.nightFactor,
            bloomStrength = bloomStrength
        )
        ensureDynamicAtmosphereShaders(topColor, bottomColor, lighting)
        val gustPulse = 0.55f + 0.45f * sin(ambienceTime * (0.78f + atmosphere.gustStrength * 0.55f))
''',
        "single atmosphere and lighting resolution",
    )
    replace_once(
        path,
        '''        drawBiomeOverlays(canvas, atmosphere)
        drawAmbientLife(canvas, atmosphere)
        drawBloomTransformation(canvas)
''',
        '''        drawBiomeOverlays(canvas, atmosphere, lighting)
        drawAmbientLife(canvas, atmosphere, lighting)
        drawBloomTransformation(canvas, lighting)
''',
        "pass reusable atmosphere state",
    )

    replace_once(
        path,
        '''    private fun drawBiomeOverlays(canvas: Canvas, atmosphere: ParallaxAtmosphereProfile) {
        val top = skyOverlayTop.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[0]
        val bottom = skyOverlayBottom.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[0]
        val lighting = buildRunLightingIdentity(
            nightFactor = atmosphere.nightFactor,
            bloomStrength = maxOf(bloomLevel, bloomAfterglowLevel * 0.48f)
        )
        skyOverlayPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            groundY,
            intArrayOf(
                Color.argb(atmosphere.biomeSkyAlpha, Color.red(top), Color.green(top), Color.blue(top)),
                Color.argb((atmosphere.biomeSkyAlpha * 1.35f).toInt().coerceAtMost(180), Color.red(bottom), Color.green(bottom), Color.blue(bottom))
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(skyRect, skyOverlayPaint)
''',
        '''    private fun drawBiomeOverlays(
        canvas: Canvas,
        atmosphere: ParallaxAtmosphereProfile,
        lighting: RunLightingIdentity
    ) {
        skyOverlayPaint.alpha =
            (atmosphere.biomeSkyAlpha * 1.35f).toInt().coerceIn(0, 180)
        canvas.drawRect(skyRect, skyOverlayPaint)
''',
        "reuse sky shader",
    )

    replace_once(
        path,
        '''        canopyShadowPaint.shader = LinearGradient(
            0f,
            groundY - screenHeight * 0.42f,
            0f,
            groundY,
            intArrayOf(
                Color.argb(
                    (atmosphere.canopyShadowAlpha * 0.55f).toInt().coerceIn(0, 100),
                    Color.red(lighting.canopyFarColor),
                    Color.green(lighting.canopyFarColor),
                    Color.blue(lighting.canopyFarColor)
                ),
                Color.argb(
                    atmosphere.canopyShadowAlpha,
                    Color.red(lighting.canopyNearColor),
                    Color.green(lighting.canopyNearColor),
                    Color.blue(lighting.canopyNearColor)
                ),
                Color.argb(
                    0,
                    Color.red(lighting.canopyNearColor),
                    Color.green(lighting.canopyNearColor),
                    Color.blue(lighting.canopyNearColor)
                )
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, groundY - screenHeight * 0.42f, screenWidth.toFloat(), groundY, canopyShadowPaint)
''',
        '''        canopyShadowPaint.alpha = atmosphere.canopyShadowAlpha
        canvas.drawRect(
            0f,
            groundY - screenHeight * 0.42f,
            screenWidth.toFloat(),
            groundY,
            canopyShadowPaint
        )
''',
        "reuse canopy shader",
    )

    replace_once(
        path,
        '''    private fun drawAmbientLife(canvas: Canvas, atmosphere: ParallaxAtmosphereProfile) {
        val foliage = foliageOverlay.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[1]
        val skyBottom = skyOverlayBottom.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[0]
        val lighting = buildRunLightingIdentity(
            nightFactor = atmosphere.nightFactor,
            bloomStrength = maxOf(bloomLevel, bloomAfterglowLevel * 0.48f)
        )
''',
        '''    private fun drawAmbientLife(
        canvas: Canvas,
        atmosphere: ParallaxAtmosphereProfile,
        lighting: RunLightingIdentity
    ) {
        val foliage = foliageOverlay.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[1]
        val skyBottom = skyOverlayBottom.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[0]
''',
        "reuse ambient lighting",
    )

    replace_once(
        path,
        '''        if (atmosphere.horizonGlowAlpha > 0) {
            val glowPaint = Paint().apply {
                shader = LinearGradient(
                    0f,
                    groundY - screenHeight * 0.14f,
                    0f,
                    groundY + screenHeight * 0.02f,
                    intArrayOf(
                        Color.argb(0, 255, 214, 146),
                        Color.argb(atmosphere.horizonGlowAlpha, 255, 206, 132)
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, groundY - screenHeight * 0.14f, screenWidth.toFloat(), groundY + screenHeight * 0.02f, glowPaint)
        }
''',
        '''        if (atmosphere.horizonGlowAlpha > 0) {
            horizonGlowPaint.alpha = atmosphere.horizonGlowAlpha
            canvas.drawRect(
                0f,
                groundY - screenHeight * 0.14f,
                screenWidth.toFloat(),
                groundY + screenHeight * 0.02f,
                horizonGlowPaint
            )
        }
''',
        "reuse horizon glow paint",
    )

    regex_replace_once(
        path,
        r'''        repeat\(atmosphere\.mistBandCount\) \{ index ->\n            mistBandPaint\.shader = LinearGradient\([\s\S]*?\n        \}\n\n        repeat\(atmosphere\.fireflyCount\)''',
        '''        repeat(atmosphere.mistBandCount) { index ->
            val mistPaint = mistBandPaints[index]
            mistPaint.alpha =
                (atmosphere.mistBandAlpha * (1f - index * 0.14f)).toInt().coerceIn(0, 110)
            val offset = sin(ambienceTime * (0.42f + index * 0.11f)) *
                (18f + index * 5f + atmosphere.gustStrength * 16f) * atmosphere.driftScale
            canvas.drawRect(
                offset - 40f,
                groundY - screenHeight * (0.15f - index * 0.028f),
                screenWidth + 40f + offset,
                groundY + screenHeight * (0.012f + index * 0.022f),
                mistPaint
            )
        }

        repeat(atmosphere.fireflyCount)''',
        "reuse mist band shaders",
    )

    replace_once(
        path,
        '''    private fun drawBloomTransformation(canvas: Canvas) {
        val bloomStrength = bloomLevel.coerceIn(0f, 1f)
        val activationBoost = bloomActivationLevel.coerceIn(0f, 1f)
        val afterglowStrength = bloomAfterglowLevel.coerceIn(0f, 1f)
        if (bloomStrength <= 0.01f && activationBoost <= 0.01f && afterglowStrength <= 0.01f) return
        val lighting = buildRunLightingIdentity(
            nightFactor = buildParallaxAtmosphereProfile(
                scrollSpeed = currentScrollSpeed,
                bloomStrength = maxOf(bloomLevel, bloomAfterglowLevel * 0.48f),
                skyTop = skyOverlayTop.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[0],
                skyBottom = skyOverlayBottom.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[0]
            ).nightFactor,
            bloomStrength = maxOf(bloomStrength, afterglowStrength * 0.48f)
        )
''',
        '''    private fun drawBloomTransformation(
        canvas: Canvas,
        lighting: RunLightingIdentity
    ) {
        val bloomStrength = bloomLevel.coerceIn(0f, 1f)
        val activationBoost = bloomActivationLevel.coerceIn(0f, 1f)
        val afterglowStrength = bloomAfterglowLevel.coerceIn(0f, 1f)
        if (bloomStrength <= 0.01f && activationBoost <= 0.01f && afterglowStrength <= 0.01f) return
''',
        "reuse bloom transformation lighting",
    )

    replace_once(
        path,
        '''    // ── Phase 24: Rich bitmap builder ─────────────────────────────────────
''',
        '''    private fun ensureDynamicAtmosphereShaders(
        skyTop: Int,
        skyBottom: Int,
        lighting: RunLightingIdentity
    ) {
        var rebuilt = false
        if (!staticAtmosphereShadersReady) {
            horizonGlowPaint.shader = LinearGradient(
                0f,
                groundY - screenHeight * 0.14f,
                0f,
                groundY + screenHeight * 0.02f,
                horizonShaderColors,
                null,
                Shader.TileMode.CLAMP
            )
            staticAtmosphereShadersReady = true
            rebuilt = true
        }

        val quantizedTop = quantizedRgb(skyTop)
        val quantizedBottom = quantizedRgb(skyBottom)
        if (quantizedTop != cachedSkyTopShaderColor ||
            quantizedBottom != cachedSkyBottomShaderColor
        ) {
            skyShaderColors[0] = Color.argb(
                189,
                Color.red(quantizedTop),
                Color.green(quantizedTop),
                Color.blue(quantizedTop)
            )
            skyShaderColors[1] = Color.argb(
                255,
                Color.red(quantizedBottom),
                Color.green(quantizedBottom),
                Color.blue(quantizedBottom)
            )
            skyOverlayPaint.shader = LinearGradient(
                0f,
                0f,
                0f,
                groundY,
                skyShaderColors,
                null,
                Shader.TileMode.CLAMP
            )
            cachedSkyTopShaderColor = quantizedTop
            cachedSkyBottomShaderColor = quantizedBottom
            rebuilt = true
        }

        val quantizedNear = quantizedRgb(lighting.canopyNearColor)
        val quantizedFar = quantizedRgb(lighting.canopyFarColor)
        if (quantizedNear != cachedCanopyNearShaderColor ||
            quantizedFar != cachedCanopyFarShaderColor
        ) {
            canopyShaderColors[0] = Color.argb(
                140,
                Color.red(quantizedFar),
                Color.green(quantizedFar),
                Color.blue(quantizedFar)
            )
            canopyShaderColors[1] = Color.argb(
                255,
                Color.red(quantizedNear),
                Color.green(quantizedNear),
                Color.blue(quantizedNear)
            )
            canopyShaderColors[2] = Color.argb(
                0,
                Color.red(quantizedNear),
                Color.green(quantizedNear),
                Color.blue(quantizedNear)
            )
            canopyShadowPaint.shader = LinearGradient(
                0f,
                groundY - screenHeight * 0.42f,
                0f,
                groundY,
                canopyShaderColors,
                canopyPositions,
                Shader.TileMode.CLAMP
            )
            cachedCanopyNearShaderColor = quantizedNear
            cachedCanopyFarShaderColor = quantizedFar
            rebuilt = true
        }

        val quantizedMist = quantizedRgb(lighting.mistColor)
        if (quantizedMist != cachedMistShaderColor) {
            mistShaderColors[0] = Color.argb(
                0,
                Color.red(quantizedMist),
                Color.green(quantizedMist),
                Color.blue(quantizedMist)
            )
            mistShaderColors[1] = Color.argb(
                255,
                Color.red(quantizedMist),
                Color.green(quantizedMist),
                Color.blue(quantizedMist)
            )
            mistShaderColors[2] = mistShaderColors[0]
            for (index in mistBandPaints.indices) {
                mistBandPaints[index].shader = LinearGradient(
                    0f,
                    groundY - screenHeight * (0.16f - index * 0.028f),
                    0f,
                    groundY + screenHeight * (0.02f + index * 0.014f),
                    mistShaderColors,
                    mistPositions,
                    Shader.TileMode.CLAMP
                )
            }
            cachedMistShaderColor = quantizedMist
            rebuilt = true
        }

        if (rebuilt) dynamicShaderRebuildCountForTest++
    }

    private fun quantizedRgb(color: Int): Int = Color.rgb(
        Color.red(color) and 0xF8,
        Color.green(color) and 0xF8,
        Color.blue(color) and 0xF8
    )

    // ── Phase 24: Rich bitmap builder ─────────────────────────────────────
''',
        "dynamic shader cache helpers",
    )


def patch_game_view() -> None:
    path = "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
    replace_once(
        path,
        '''    private val cinematicOverlay = CinematicOverlayRenderer()
''',
        '''    private val cinematicOverlay = CinematicOverlayRenderer()
    private val reusableRunLighting = RunLightingIdentity()
    private val reusableRunCinematicProfile = CinematicPolishProfile()
    private val reusableBloomPowerState = BloomPowerPresentationState()
    private val reusableBloomHudPresentation = BloomHudPresentation()
''',
        "GameView reusable presentation fields",
    )
    replace_once(
        path,
        '''            val powerState = BloomPowerPresentation.resolve(
                secondsRemaining = gameState.bloomSecondsRemaining,
                conversionsInBurst = liveBurstConversions,
                recentSurgeFraction = bloomPowerSurgeFraction()
            )
''',
        '''            val powerState = BloomPowerPresentation.resolveInto(
                target = reusableBloomPowerState,
                secondsRemaining = gameState.bloomSecondsRemaining,
                conversionsInBurst = liveBurstConversions,
                recentSurgeFraction = bloomPowerSurgeFraction()
            )
''',
        "reuse Bloom power state",
    )
    replace_once(
        path,
        '''            val runLighting = buildRunLightingIdentity(
                nightFactor = nightFactor,
                bloomStrength = if (gameState.isBloomActive) 1f else bloomAfterglow * 0.55f
            )
''',
        '''            val runLighting = resolveRunLightingIdentity(
                target = reusableRunLighting,
                nightFactor = nightFactor,
                bloomStrength = if (gameState.isBloomActive) 1f else bloomAfterglow * 0.55f
            )
''',
        "reuse run lighting",
    )
    replace_once(
        path,
        '''                profile = buildCinematicPolishProfile(
                    scene = CinematicScene.RUN,
                    emphasis = ((motif.cadenceLift + motif.shimmer) * 0.5f).coerceIn(0f, 1f),
                    bloomStrength = if (gameState.isBloomActive) 1f else bloomAfterglow * 0.55f
                ),
''',
        '''                profile = resolveCinematicPolishProfile(
                    target = reusableRunCinematicProfile,
                    scene = CinematicScene.RUN,
                    emphasis = ((motif.cadenceLift + motif.shimmer) * 0.5f).coerceIn(0f, 1f),
                    bloomStrength = if (gameState.isBloomActive) 1f else bloomAfterglow * 0.55f
                ),
''',
        "reuse run cinematic profile",
    )
    replace_once(
        path,
        '''                    bloomPresentation = BloomPresentation.hudPresentation(
                        bloomMeter = gameState.bloomMeter,
                        seedTarget = gameState.bloomSeedTarget,
                        isActive = gameState.isBloomActive,
                        secondsRemaining = gameState.bloomSecondsRemaining,
                        totalConversions = gameState.bloomConversionsThisRun,
                        burstConversions = bloomLastBurstConversions,
                        recentAfterglow = bloomAfterglow
                    ),
''',
        '''                    bloomPresentation = BloomPresentation.resolveInto(
                        target = reusableBloomHudPresentation,
                        bloomMeter = gameState.bloomMeter,
                        seedTarget = gameState.bloomSeedTarget,
                        isActive = gameState.isBloomActive,
                        secondsRemaining = gameState.bloomSecondsRemaining,
                        totalConversions = gameState.bloomConversionsThisRun,
                        burstConversions = bloomLastBurstConversions,
                        recentAfterglow = bloomAfterglow
                    ),
''',
        "reuse Bloom HUD state",
    )
    replace_once(path, "progressTier(gameState.mercyHearts, intArrayOf(2, 4, 6))",
                 "progressTier(gameState.mercyHearts, 2, 4, 6)", "mercy threshold array")
    replace_once(path, "progressTier(gameState.kindnessChain, intArrayOf(3, 5, 8))",
                 "progressTier(gameState.kindnessChain, 3, 5, 8)", "kindness threshold array")
    replace_once(path, "progressTier(gameState.cleanPassesThisRun, intArrayOf(4, 8, 12))",
                 "progressTier(gameState.cleanPassesThisRun, 4, 8, 12)", "clean threshold array")
    replace_once(
        path,
        '''    private fun progressTier(value: Int, thresholds: IntArray): Int =
        thresholds.count { value >= it }
''',
        '''    private fun progressTier(
        value: Int,
        firstThreshold: Int,
        secondThreshold: Int,
        thirdThreshold: Int
    ): Int = when {
        value >= thirdThreshold -> 3
        value >= secondThreshold -> 2
        value >= firstThreshold -> 1
        else -> 0
    }
''',
        "allocation-free progress tier",
    )


def patch_ui_cinematic_profiles() -> None:
    cinematic_import = "import com.anurag9000.forestrun.engine.buildCinematicPolishProfile\n"
    reusable_imports = (
        "import com.anurag9000.forestrun.engine.CinematicPolishProfile\n"
        "import com.anurag9000.forestrun.engine.resolveCinematicPolishProfile\n"
    )

    menu = "app/src/main/java/com/anurag9000/forestrun/ui/MainMenuScreen.kt"
    replace_once(menu, cinematic_import, reusable_imports, "menu cinematic imports")
    replace_once(
        menu,
        '''    private val cinematicOverlay = CinematicOverlayRenderer()
''',
        '''    private val cinematicOverlay = CinematicOverlayRenderer()
    private val cinematicProfile = CinematicPolishProfile()
    private val menuLighting = buildSanctuaryLightingIdentity(SanctuaryLightingScene.MENU)
    private val launchCueRect = RectF()
''',
        "menu reusable presentation fields",
    )
    replace_once(menu, "        val menuLighting = buildSanctuaryLightingIdentity(SanctuaryLightingScene.MENU)\n", "",
                 "remove per-draw menu lighting lookup")
    replace_once(
        menu,
        '''            profile = buildCinematicPolishProfile(
                scene = CinematicScene.MENU,
                emphasis = when (phase) {
                    Phase.IDLE -> 0.36f
                    Phase.STANDING_UP -> 0.58f
                    Phase.READY -> 0.74f
                }
            ),
''',
        '''            profile = resolveCinematicPolishProfile(
                target = cinematicProfile,
                scene = CinematicScene.MENU,
                emphasis = when (phase) {
                    Phase.IDLE -> 0.36f
                    Phase.STANDING_UP -> 0.58f
                    Phase.READY -> 0.74f
                }
            ),
''',
        "reuse menu cinematic profile",
    )
    replace_once(
        menu,
        '''            val laneRight = cw * (0.58f + 0.22f * phaseT)
            launchCuePaint.color = Color.argb((70 + phaseT * 54).toInt(), 244, 238, 172)
            canvas.drawRoundRect(RectF(laneLeft, laneTop, laneRight, laneBottom), 20f, 20f, launchCuePaint)
            launchCueBorderPaint.color = Color.argb((96 + phaseT * 64).toInt(), 252, 244, 208)
            canvas.drawRoundRect(RectF(laneLeft, laneTop, laneRight, laneBottom), 20f, 20f, launchCueBorderPaint)
''',
        '''            val laneRight = cw * (0.58f + 0.22f * phaseT)
            launchCueRect.set(laneLeft, laneTop, laneRight, laneBottom)
            launchCuePaint.color = Color.argb((70 + phaseT * 54).toInt(), 244, 238, 172)
            canvas.drawRoundRect(launchCueRect, 20f, 20f, launchCuePaint)
            launchCueBorderPaint.color = Color.argb((96 + phaseT * 64).toInt(), 252, 244, 208)
            canvas.drawRoundRect(launchCueRect, 20f, 20f, launchCueBorderPaint)
''',
        "reuse menu launch cue rectangle",
    )
    replace_once(
        menu,
        "        val lighting = buildSanctuaryLightingIdentity(SanctuaryLightingScene.MENU)\n",
        "        val lighting = menuLighting\n",
        "reuse menu lighting",
    )

    garden = "app/src/main/java/com/anurag9000/forestrun/ui/GardenScreen.kt"
    replace_once(garden, cinematic_import, reusable_imports, "garden cinematic imports")
    replace_once(
        garden,
        '''    private val cinematicOverlay = CinematicOverlayRenderer()
''',
        '''    private val cinematicOverlay = CinematicOverlayRenderer()
    private val cinematicProfile = CinematicPolishProfile()
    private val gardenLighting = buildSanctuaryLightingIdentity(SanctuaryLightingScene.GARDEN)
''',
        "garden reusable presentation fields",
    )
    replace_once(garden, "        val gardenLighting = buildSanctuaryLightingIdentity(SanctuaryLightingScene.GARDEN)\n", "",
                 "remove per-draw garden lighting lookup")
    replace_once(
        garden,
        '''            profile = buildCinematicPolishProfile(
                scene = CinematicScene.GARDEN,
                emphasis = traceEmphasis
            ),
''',
        '''            profile = resolveCinematicPolishProfile(
                target = cinematicProfile,
                scene = CinematicScene.GARDEN,
                emphasis = traceEmphasis
            ),
''',
        "reuse garden cinematic profile",
    )
    replace_once(
        garden,
        "        val lighting = buildSanctuaryLightingIdentity(SanctuaryLightingScene.GARDEN)\n",
        "        val lighting = gardenLighting\n",
        "reuse garden lighting",
    )

    rest = "app/src/main/java/com/anurag9000/forestrun/ui/GameOverScreen.kt"
    replace_once(rest, cinematic_import, reusable_imports, "rest cinematic imports")
    replace_once(
        rest,
        '''    private val cinematicOverlay = CinematicOverlayRenderer()
''',
        '''    private val cinematicOverlay = CinematicOverlayRenderer()
    private val cinematicProfile = CinematicPolishProfile()
    private val restLighting = buildSanctuaryLightingIdentity(SanctuaryLightingScene.REST)
    private val restChipRect = RectF()
    private val arrivalBadgeRect = RectF()
    private val homeCharacterRect = RectF()
''',
        "rest reusable presentation fields",
    )
    replace_once(rest, "        val restLighting = buildSanctuaryLightingIdentity(SanctuaryLightingScene.REST)\n", "",
                 "remove per-draw rest lighting lookup")
    replace_once(
        rest,
        '''            profile = buildCinematicPolishProfile(
                scene = CinematicScene.REST,
                emphasis = restEmphasis
            ),
''',
        '''            profile = resolveCinematicPolishProfile(
                target = cinematicProfile,
                scene = CinematicScene.REST,
                emphasis = restEmphasis
            ),
''',
        "reuse rest cinematic profile",
    )
    replace_once(
        rest,
        '''        val chipRect = RectF(cx - 58f, ty - 26f, cx + 58f, ty - 2f)
        restChipPaint.alpha = (216f * stageAlpha).toInt().coerceIn(0, 255)
        restChipBorderPaint.alpha = (196f * stageAlpha).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(chipRect, 12f, 12f, restChipPaint)
        canvas.drawRoundRect(chipRect, 12f, 12f, restChipBorderPaint)
        canvas.drawText("REST", cx, chipRect.centerY() - (restChipTextPaint.descent() + restChipTextPaint.ascent()) / 2f, restChipTextPaint)
''',
        '''        restChipRect.set(cx - 58f, ty - 26f, cx + 58f, ty - 2f)
        restChipPaint.alpha = (216f * stageAlpha).toInt().coerceIn(0, 255)
        restChipBorderPaint.alpha = (196f * stageAlpha).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(restChipRect, 12f, 12f, restChipPaint)
        canvas.drawRoundRect(restChipRect, 12f, 12f, restChipBorderPaint)
        canvas.drawText(
            "REST",
            cx,
            restChipRect.centerY() -
                (restChipTextPaint.descent() + restChipTextPaint.ascent()) / 2f,
            restChipTextPaint
        )
''',
        "reuse rest chip rectangle",
    )
    replace_once(
        rest,
        '''            val badgeWidth = panelW * 0.34f
            val badgeRect = RectF(cx - badgeWidth / 2f, ty - 16f, cx + badgeWidth / 2f, ty + 10f)
            canvas.drawRoundRect(badgeRect, 16f, 16f, badgePaint)
            canvas.drawRoundRect(badgeRect, 16f, 16f, badgeBorderPaint)
            val labelY = badgeRect.centerY() - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
''',
        '''            val badgeWidth = panelW * 0.34f
            arrivalBadgeRect.set(
                cx - badgeWidth / 2f,
                ty - 16f,
                cx + badgeWidth / 2f,
                ty + 10f
            )
            canvas.drawRoundRect(arrivalBadgeRect, 16f, 16f, badgePaint)
            canvas.drawRoundRect(arrivalBadgeRect, 16f, 16f, badgeBorderPaint)
            val labelY = arrivalBadgeRect.centerY() -
                (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
''',
        "reuse arrival badge rectangle",
    )
    replace_once(
        rest,
        '''            val homeWidth = panelW * 0.36f
            val homeRect = RectF(cx - homeWidth / 2f, ty - 14f, cx + homeWidth / 2f, ty + 10f)
            canvas.drawRoundRect(homeRect, 14f, 14f, restChipPaint)
            canvas.drawRoundRect(homeRect, 14f, 14f, restChipBorderPaint)
            val labelY = homeRect.centerY() - (restChipTextPaint.descent() + restChipTextPaint.ascent()) / 2f
''',
        '''            val homeWidth = panelW * 0.36f
            homeCharacterRect.set(
                cx - homeWidth / 2f,
                ty - 14f,
                cx + homeWidth / 2f,
                ty + 10f
            )
            canvas.drawRoundRect(homeCharacterRect, 14f, 14f, restChipPaint)
            canvas.drawRoundRect(homeCharacterRect, 14f, 14f, restChipBorderPaint)
            val labelY = homeCharacterRect.centerY() -
                (restChipTextPaint.descent() + restChipTextPaint.ascent()) / 2f
''',
        "reuse home character rectangle",
    )


def main() -> None:
    write("app/src/main/java/com/anurag9000/forestrun/engine/LightingIdentityProfile.kt", LIGHTING)
    write("app/src/main/java/com/anurag9000/forestrun/engine/CinematicPolish.kt", CINEMATIC)
    write("app/src/main/java/com/anurag9000/forestrun/engine/BloomPowerPresentation.kt", BLOOM_POWER)
    write("app/src/main/java/com/anurag9000/forestrun/engine/BloomPresentation.kt", BLOOM_HUD)
    patch_parallax()
    patch_game_view()
    patch_ui_cinematic_profiles()
    write(
        "app/src/test/java/com/anurag9000/forestrun/engine/RenderHotPathReuseTest.kt",
        HOT_PATH_TEST,
    )


if __name__ == "__main__":
    main()
