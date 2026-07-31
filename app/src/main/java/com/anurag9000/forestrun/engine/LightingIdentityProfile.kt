package com.anurag9000.forestrun.engine

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
    val night = (nightFactor.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, 1f)
    val bloom = (bloomStrength.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, 1f)
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
