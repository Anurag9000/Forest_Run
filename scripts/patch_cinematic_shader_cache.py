#!/usr/bin/env python3
"""Cache immutable cinematic shaders and fixed sanctuary lighting profiles."""

from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def patch_cinematic_renderer() -> None:
    path = Path("app/src/main/java/com/anurag9000/forestrun/engine/CinematicPolish.kt")
    replace_once(
        path,
        '''    private val letterboxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letterboxRect = RectF()

    fun draw(
''',
        '''    private val letterboxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
''',
        "shader cache fields",
    )
    replace_once(
        path,
        '''        val shimmerPulse = 0.55f + 0.45f * sin(elapsedSeconds * (1.2f + profile.shimmerStrength))
        val topLetterbox = (height * profile.letterboxHeightFraction).coerceAtLeast(0f)
        val bottomLetterboxTop = height - topLetterbox

        letterboxPaint.color = Color.argb(profile.letterboxAlpha, 10, 12, 18)
''',
        '''        val shimmerPulse = 0.55f + 0.45f * sin(elapsedSeconds * (1.2f + profile.shimmerStrength))
        val topLetterbox = (height * profile.letterboxHeightFraction).coerceAtLeast(0f)
        val bottomLetterboxTop = height - topLetterbox
        val normalizedCenter = centerYFraction.coerceIn(0.2f, 0.8f)
        ensureShaders(width, height, glowColor, normalizedCenter)

        letterboxPaint.color = Color.argb(profile.letterboxAlpha, 10, 12, 18)
''',
        "prepare cached shaders",
    )
    replace_once(
        path,
        '''        val glowAlpha = (profile.edgeGlowAlpha * (0.82f + shimmerPulse * 0.18f)).toInt().coerceIn(0, 255)
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
''',
        '''        val glowAlpha = (profile.edgeGlowAlpha * (0.82f + shimmerPulse * 0.18f)).toInt().coerceIn(0, 255)
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
''',
        "cached shader draw path",
    )


def patch_lighting_profiles() -> None:
    path = Path("app/src/main/java/com/anurag9000/forestrun/engine/LightingIdentityProfile.kt")
    old = '''internal fun buildSanctuaryLightingIdentity(
    scene: SanctuaryLightingScene
): SanctuaryLightingIdentity = when (scene) {
    SanctuaryLightingScene.MENU -> SanctuaryLightingIdentity(
        canopyColor = Color.rgb(26, 42, 34),
        mistColor = Color.rgb(232, 246, 236),
        fireflyColor = Color.rgb(252, 246, 182),
        lanternOuterColor = Color.rgb(255, 235, 168),
        lanternInnerColor = Color.rgb(255, 242, 196),
        groundGlowColor = Color.rgb(240, 246, 184),
        bloomPatchColor = Color.rgb(255, 242, 196)
    )
    SanctuaryLightingScene.GARDEN -> SanctuaryLightingIdentity(
        canopyColor = Color.rgb(24, 44, 38),
        mistColor = Color.rgb(236, 248, 236),
        fireflyColor = Color.rgb(252, 246, 180),
        lanternOuterColor = Color.rgb(255, 234, 170),
        lanternInnerColor = Color.rgb(255, 242, 192),
        groundGlowColor = Color.rgb(240, 246, 186),
        bloomPatchColor = Color.rgb(255, 240, 186)
    )
    SanctuaryLightingScene.REST -> SanctuaryLightingIdentity(
        canopyColor = Color.rgb(20, 28, 34),
        mistColor = Color.rgb(228, 240, 236),
        fireflyColor = Color.rgb(230, 242, 196),
        lanternOuterColor = Color.rgb(255, 236, 170),
        lanternInnerColor = Color.rgb(255, 242, 192),
        groundGlowColor = Color.rgb(236, 240, 178),
        bloomPatchColor = Color.rgb(238, 236, 186)
    )
}
'''
    new = '''private val menuSanctuaryLighting = SanctuaryLightingIdentity(
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
    replace_once(path, old, new, "fixed sanctuary lighting singletons")


def main() -> None:
    patch_cinematic_renderer()
    patch_lighting_profiles()


if __name__ == "__main__":
    main()
