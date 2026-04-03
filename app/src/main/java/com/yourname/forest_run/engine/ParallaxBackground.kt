package com.yourname.forest_run.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

internal enum class SceneSkyFeature { SUN, FILTERED_SUN, MOON }
internal enum class SceneRidgeStyle { HILLS, ORCHARD_ROWS, GROVE_SPIRES, CANYON_MESAS, NIGHT_PINES }
internal enum class SceneGroundAccent { FLOWERS, PETALS, FERNS, STONES, GLOW_MUSHROOMS }

internal data class BiomeSceneArtProfile(
    val skyFeature: SceneSkyFeature,
    val ridgeStyle: SceneRidgeStyle,
    val groundAccent: SceneGroundAccent,
    val featureColor: Int,
    val accentGlowColor: Int,
    val ridgeColors: IntArray
)

internal fun buildBiomeSceneArtProfile(biome: Biome): BiomeSceneArtProfile = when (biome) {
    Biome.MEADOW -> BiomeSceneArtProfile(
        skyFeature = SceneSkyFeature.SUN,
        ridgeStyle = SceneRidgeStyle.HILLS,
        groundAccent = SceneGroundAccent.FLOWERS,
        featureColor = Color.rgb(236, 198, 108),
        accentGlowColor = Color.rgb(255, 232, 166),
        ridgeColors = intArrayOf(
            Color.argb(196, 70, 136, 92),
            Color.argb(220, 58, 118, 72),
            Color.argb(240, 40, 94, 56)
        )
    )
    Biome.ORCHARD -> BiomeSceneArtProfile(
        skyFeature = SceneSkyFeature.SUN,
        ridgeStyle = SceneRidgeStyle.ORCHARD_ROWS,
        groundAccent = SceneGroundAccent.PETALS,
        featureColor = Color.rgb(244, 188, 148),
        accentGlowColor = Color.rgb(255, 228, 196),
        ridgeColors = intArrayOf(
            Color.argb(192, 148, 98, 118),
            Color.argb(218, 126, 80, 104),
            Color.argb(240, 98, 58, 86)
        )
    )
    Biome.ANCIENT_GROVE -> BiomeSceneArtProfile(
        skyFeature = SceneSkyFeature.FILTERED_SUN,
        ridgeStyle = SceneRidgeStyle.GROVE_SPIRES,
        groundAccent = SceneGroundAccent.FERNS,
        featureColor = Color.rgb(110, 170, 106),
        accentGlowColor = Color.rgb(186, 232, 168),
        ridgeColors = intArrayOf(
            Color.argb(204, 28, 70, 48),
            Color.argb(226, 20, 54, 34),
            Color.argb(246, 14, 38, 24)
        )
    )
    Biome.DUSK_CANYON -> BiomeSceneArtProfile(
        skyFeature = SceneSkyFeature.SUN,
        ridgeStyle = SceneRidgeStyle.CANYON_MESAS,
        groundAccent = SceneGroundAccent.STONES,
        featureColor = Color.rgb(255, 164, 94),
        accentGlowColor = Color.rgb(255, 210, 146),
        ridgeColors = intArrayOf(
            Color.argb(204, 148, 88, 52),
            Color.argb(226, 124, 66, 36),
            Color.argb(244, 92, 42, 24)
        )
    )
    Biome.NIGHT_FOREST -> BiomeSceneArtProfile(
        skyFeature = SceneSkyFeature.MOON,
        ridgeStyle = SceneRidgeStyle.NIGHT_PINES,
        groundAccent = SceneGroundAccent.GLOW_MUSHROOMS,
        featureColor = Color.rgb(198, 216, 255),
        accentGlowColor = Color.rgb(212, 244, 255),
        ridgeColors = intArrayOf(
            Color.argb(206, 22, 34, 70),
            Color.argb(224, 16, 24, 52),
            Color.argb(244, 10, 16, 36)
        )
    )
}

internal data class ParallaxAtmosphereProfile(
    val worldScale: Float,
    val driftScale: Float,
    val gustStrength: Float,
    val worldSwayAmplitude: Float,
    val leafCount: Int,
    val leafBackfillCount: Int,
    val petalCount: Int,
    val petalTrailCount: Int,
    val fireflyCount: Int,
    val glowMoteCount: Int,
    val ribbonCount: Int,
    val mistBandCount: Int,
    val windRibbonAlpha: Int,
    val mistBandAlpha: Int,
    val canopyShadowAlpha: Int,
    val horizonGlowAlpha: Int,
    val biomeSkyAlpha: Int,
    val foliageWashAlpha: Int,
    val nightFactor: Float
)

internal fun buildParallaxAtmosphereProfile(
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

    return ParallaxAtmosphereProfile(
        worldScale = (1f + speedLift * 0.012f + bloom * 0.026f + nightFactor * 0.008f).coerceAtMost(1.065f),
        driftScale = (1f + speedLift * 0.28f + bloom * 0.18f + nightFactor * 0.10f).coerceAtMost(1.65f),
        gustStrength = (0.18f + speedLift * 0.24f + bloom * 0.16f + nightFactor * 0.12f).coerceIn(0f, 0.75f),
        worldSwayAmplitude = (4f + speedLift * 4.5f + bloom * 4f + nightFactor * 2.2f).coerceIn(4f, 16f),
        leafCount = (5 + speedLift * 6f + bloom * 4f).toInt().coerceAtLeast(4),
        leafBackfillCount = (3 + speedLift * 4f + nightFactor * 3f + bloom * 2f).toInt().coerceAtLeast(3),
        petalCount = (3 + bloom * 7f + nightFactor * 2f).toInt().coerceAtLeast(2),
        petalTrailCount = (2 + bloom * 5f + speedLift * 2.5f).toInt().coerceAtLeast(2),
        fireflyCount = (nightFactor * 8f + bloom * 4f).toInt().coerceAtLeast(if (nightFactor > 0.45f) 3 else 0),
        glowMoteCount = (2 + nightFactor * 5f + bloom * 5f).toInt().coerceAtLeast(if (bloom > 0.2f || nightFactor > 0.4f) 3 else 1),
        ribbonCount = (3 + speedLift * 2f + bloom * 1.5f).toInt().coerceIn(3, 6),
        mistBandCount = (2 + nightFactor * 2.2f + bloom * 1.4f).toInt().coerceIn(2, 5),
        windRibbonAlpha = (18f + speedLift * 34f + bloom * 24f).toInt().coerceIn(0, 110),
        mistBandAlpha = (16f + nightFactor * 38f + bloom * 20f).toInt().coerceIn(0, 120),
        canopyShadowAlpha = (22f + nightFactor * 48f + speedLift * 12f).toInt().coerceIn(0, 120),
        horizonGlowAlpha = (36f + bloom * 92f + speedLift * 22f).toInt().coerceIn(0, 180),
        biomeSkyAlpha = (28f + nightFactor * 42f).toInt().coerceIn(0, 120),
        foliageWashAlpha = (20f + nightFactor * 35f + bloom * 18f).toInt().coerceIn(0, 96),
        nightFactor = nightFactor
    )
}

/**
 * Manages 4 parallax layers that together create the illusion of a deep,
 * living forest scrolling past the player.
 *
 * ── Layer order (back → front) ───────────────────────────────────────────────
 *  0  Sky + distant mountains  –  10% game speed
 *  1  Mid-range tree silhouettes – 30% game speed  (biome-swappable)
 *  2  Ground path / grass         – 100% game speed (matches entity scroll)
 *  3  Near foreground strip        – 150% game speed (creates depth)
 *
 * Phase 4 (now): placeholder solid-colour bitmaps.
 * Phase 24: real pixel-art bitmaps loaded from assets.
 *
 * @param screenWidth  Device screen width in pixels.
 * @param screenHeight Device screen height in pixels.
 */
class ParallaxBackground(
    private val screenWidth: Int,
    private val screenHeight: Int
) {

    // -----------------------------------------------------------------------
    // Layer configuration
    // -----------------------------------------------------------------------

    /** Fraction of game scroll speed each layer moves at. */
    private val speedFractions = floatArrayOf(0.10f, 0.30f, 1.00f, 1.50f)

    /**
     * Placeholder colours that approximate the 4 depth zones.
     * Phase 24 replaces these with real bitmaps from assets.
     */
    private val placeholderColours = intArrayOf(
        Color.rgb( 30,  50,  90),   // Layer 0: deep blue-grey sky
        Color.rgb( 22,  70,  38),   // Layer 1: dark forest silhouettes
        Color.rgb( 42, 100,  40),   // Layer 2: mid green ground
        Color.rgb( 60, 130,  55)    // Layer 3: bright near-foreground strip
    )

    /**
     * Fractional screen height each layer occupies (top → bottom).
     * These define the "band" for placeholder drawing.
     * Layer 0 = top 65%, Layer 1 = 50-85%, Layer 2 = ground, Layer 3 = bottom strip.
     */
    private data class LayerBand(val topFrac: Float, val bottomFrac: Float)
    private val bands = listOf(
        LayerBand(0.00f, 0.78f),    // sky
        LayerBand(0.35f, 0.90f),    // mid-tree silhouettes
        LayerBand(0.72f, 0.92f),    // ground path
        LayerBand(0.88f, 1.00f)     // near foreground strip
    )

    // -----------------------------------------------------------------------
    // Parallax layers (bitmap-based, will swap in Phase 24)
    // -----------------------------------------------------------------------
    private val layers: Array<ParallaxLayer>

    // -----------------------------------------------------------------------
    // Floor
    // -----------------------------------------------------------------------
    /** Y pixel of the ground surface — entities and player land here. */
    val groundY: Float

    private val floorPaint = Paint().apply {
        color = Color.rgb(55, 140, 55)
        style = Paint.Style.FILL
    }
    private val floorRect = RectF()
    private val skyRect = RectF()
    private val bloomHorizonRect = RectF()

    // -----------------------------------------------------------------------
    // Accent paints for layer blending
    // -----------------------------------------------------------------------
    private val layerPaint = Paint()
    private val bloomSkyPaint = Paint().apply { color = Color.argb(0, 255, 208, 120) }
    private val bloomHorizonPaint = Paint().apply { color = Color.argb(0, 255, 170, 120) }
    private val bloomFloorPaint = Paint().apply { color = Color.argb(0, 170, 255, 145) }
    private val bloomOrbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0, 255, 238, 188)
        style = Paint.Style.FILL
    }
    private val bloomRayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0, 255, 228, 162)
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }
    private var bloomTarget = 0f
    private var bloomLevel = 0f
    private var bloomPulse = 0f
    private var bloomActivationLevel = 0f
    private var bloomAfterglowLevel = 0f
    private var ambienceTime = 0f
    private var currentScrollSpeed = GameConstants.BASE_SCROLL_SPEED
    private var sceneBiome: Biome = Biome.MEADOW

    init {
        groundY = screenHeight * 0.82f

        layers = Array(4) { i ->
            val bmp = buildPlaceholderBitmap(i, sceneBiome)
            ParallaxLayer(bmp, speedFractions[i])
        }

        // Position the floor rect
        floorRect.set(0f, groundY, screenWidth.toFloat(), screenHeight.toFloat())
        skyRect.set(0f, 0f, screenWidth.toFloat(), groundY)
        bloomHorizonRect.set(0f, groundY - screenHeight * 0.18f, screenWidth.toFloat(), groundY + screenHeight * 0.05f)
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    fun update(deltaTime: Float, gameScrollSpeed: Float) {
        ambienceTime += deltaTime
        currentScrollSpeed = gameScrollSpeed
        for (layer in layers) layer.update(deltaTime, gameScrollSpeed)
        val blendSpeed = if (bloomTarget > bloomLevel) 4.5f else 2.8f
        bloomLevel += (bloomTarget - bloomLevel) * (blendSpeed * deltaTime).coerceAtMost(1f)
        if (bloomLevel > 0.01f || bloomActivationLevel > 0.01f) {
            bloomPulse += deltaTime * 3.4f
        }
    }

    fun draw(canvas: Canvas) {
        val atmosphere = buildParallaxAtmosphereProfile(
            scrollSpeed = currentScrollSpeed,
            bloomStrength = maxOf(bloomLevel, bloomAfterglowLevel * 0.48f),
            skyTop = skyOverlayTop.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[0],
            skyBottom = skyOverlayBottom.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[0]
        )
        val gustPulse = 0.55f + 0.45f * sin(ambienceTime * (0.78f + atmosphere.gustStrength * 0.55f))
        val worldSwayX = sin(ambienceTime * 0.92f) * atmosphere.worldSwayAmplitude * gustPulse
        val worldSwayY = cos(ambienceTime * 0.64f) * atmosphere.worldSwayAmplitude * 0.18f * gustPulse
        val gustScale = 1f + atmosphere.gustStrength * 0.012f * gustPulse

        // Draw back → front
        canvas.save()
        canvas.translate(worldSwayX, worldSwayY)
        canvas.scale(
            atmosphere.worldScale * gustScale,
            atmosphere.worldScale * (1f - atmosphere.gustStrength * 0.004f * gustPulse),
            screenWidth * 0.5f,
            groundY * 0.55f
        )
        for (layer in layers) layer.draw(canvas)

        // Draw the floor band on top of layer 2/3 (solid, not scrolled)
        canvas.drawRect(floorRect, floorPaint)
        canvas.restore()
        drawBiomeOverlays(canvas, atmosphere)
        drawAmbientLife(canvas, atmosphere)
        drawBloomTransformation(canvas)
    }

    /**
     * Swap layer 1 (the biome-specific mid-tree silhouette layer) to a new bitmap.
     * Called by BiomeManager on every 500m transition (Phase 13).
     */
    fun swapBiomeLayer(bitmap: Bitmap) {
        layers[1] = ParallaxLayer(bitmap, speedFractions[1])
    }

    fun applyBiomeScene(biome: Biome) {
        if (sceneBiome == biome) return
        sceneBiome = biome
        for (index in layers.indices) {
            val previousX = layers[index].x
            val rebuilt = ParallaxLayer(buildPlaceholderBitmap(index, biome), speedFractions[index])
            rebuilt.x = previousX
            layers[index] = rebuilt
        }
    }

    /**
     * Push live-blended biome colours into the placeholder layer paints.
     * Called every frame from GameView with values from [BiomeManager].
     *
     * @param skyTop       Sky top gradient colour (applied to layer 0 background).
     * @param skyBottom    Sky bottom / horizon colour.
     * @param groundColour Ground strip colour (layer 3).
     * @param foliage      Mid-foliage colour (layer 1 silhouettes).
     */
    fun applyBiomeColours(skyTop: Int, skyBottom: Int, groundColour: Int, foliage: Int) {
        // Recolour placeholder bitmaps is expensive — instead we store tint values
        // and draw a colour-mode overlay on each layer during draw().
        // For Phase 13, we tint the floor paint and the sky overlay directly.
        floorPaint.color = groundColour

        // Sky overlay paint — used in draw() to tint layer 0
        skyOverlayTop    = skyTop
        skyOverlayBottom = skyBottom
        foliageOverlay   = foliage
    }

    fun setBloomState(isActive: Boolean, activationLevel: Float, afterglowLevel: Float = 0f) {
        bloomTarget = if (isActive) 1f else 0f
        bloomActivationLevel = activationLevel.coerceIn(0f, 1f)
        bloomAfterglowLevel = afterglowLevel.coerceIn(0f, 1f)
    }

    /** Tint values set by [applyBiomeColours], applied during draw(). */
    private var skyOverlayTop:    Int = Color.TRANSPARENT
    private var skyOverlayBottom: Int = Color.TRANSPARENT
    private var foliageOverlay:   Int = Color.TRANSPARENT

    private val skyOverlayPaint = Paint().apply { alpha = 120 }
    private val foliageWashPaint = Paint().apply { style = Paint.Style.FILL }
    private val windRibbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val mistBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val canopyShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val petalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fireflyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowMotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private fun drawBiomeOverlays(canvas: Canvas, atmosphere: ParallaxAtmosphereProfile) {
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

        val foliage = foliageOverlay.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[1]
        foliageWashPaint.color = Color.argb(
            atmosphere.foliageWashAlpha,
            Color.red(foliage),
            Color.green(foliage),
            Color.blue(foliage)
        )
        canvas.drawRect(0f, groundY - screenHeight * 0.28f, screenWidth.toFloat(), groundY + screenHeight * 0.02f, foliageWashPaint)

        canopyShadowPaint.shader = LinearGradient(
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
    }

    private fun drawAmbientLife(canvas: Canvas, atmosphere: ParallaxAtmosphereProfile) {
        val foliage = foliageOverlay.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[1]
        val skyBottom = skyOverlayBottom.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[0]
        val lighting = buildRunLightingIdentity(
            nightFactor = atmosphere.nightFactor,
            bloomStrength = maxOf(bloomLevel, bloomAfterglowLevel * 0.48f)
        )
        val ribbonY = groundY - screenHeight * 0.22f
        val gustPulse = 0.55f + 0.45f * sin(ambienceTime * (0.78f + atmosphere.gustStrength * 0.55f))
        repeat(atmosphere.ribbonCount) { index ->
            windRibbonPaint.color = Color.argb(
                (atmosphere.windRibbonAlpha * (0.7f + index * 0.12f + atmosphere.gustStrength * 0.22f)).toInt().coerceIn(0, 160),
                (210 + Color.red(skyBottom) * 0.10f).toInt().coerceAtMost(255),
                (225 + Color.green(skyBottom) * 0.08f).toInt().coerceAtMost(255),
                (214 + Color.blue(skyBottom) * 0.06f).toInt().coerceAtMost(255)
            )
            windRibbonPaint.strokeWidth = 5f + index * 0.8f + atmosphere.gustStrength * 2.4f
            val startX =
                screenWidth * (0.06f + index * 0.15f) +
                    sin(ambienceTime * (0.7f + index * 0.18f)) * 20f * atmosphere.driftScale
            val endX = startX + screenWidth * (0.18f + index * 0.015f + atmosphere.gustStrength * 0.02f)
            val y = ribbonY + index * screenHeight * 0.036f +
                cos(ambienceTime * (0.9f + index * 0.22f)) * 14f * atmosphere.driftScale
            val midX = startX + (endX - startX) * 0.52f
            val crestY = y - (10f + atmosphere.gustStrength * 12f) * atmosphere.driftScale * gustPulse
            canvas.drawLine(startX, y, midX, crestY, windRibbonPaint)
            canvas.drawLine(midX, crestY, endX, y - 6f * atmosphere.driftScale, windRibbonPaint)
        }

        repeat(atmosphere.leafCount) { index ->
            val laneSpeed = 18f + index * 2.7f + (currentScrollSpeed / GameConstants.BASE_SCROLL_SPEED) * 8f * atmosphere.driftScale
            val drift = (ambienceTime * laneSpeed + index * 37f) % (screenWidth + 80f)
            val x = screenWidth - drift
            val y = groundY - screenHeight * (0.10f + (index % 5) * 0.045f) +
                sin(ambienceTime * 1.1f + index) * (12f + atmosphere.gustStrength * 10f) * atmosphere.driftScale
            val size = 8f + (index % 3) * 2.5f
            leafPaint.color = Color.argb(
                (64f + atmosphere.nightFactor * 24f + bloomLevel * 18f).toInt().coerceIn(0, 150),
                ((Color.red(foliage) * 0.70f) + 72f).toInt().coerceIn(0, 255),
                ((Color.green(foliage) * 0.82f) + 56f).toInt().coerceIn(0, 255),
                ((Color.blue(foliage) * 0.55f) + 48f + bloomLevel * 20f).toInt().coerceIn(0, 255)
            )
            canvas.drawOval(x, y, x + size * 1.6f, y + size, leafPaint)
        }

        repeat(atmosphere.leafBackfillCount) { index ->
            val laneSpeed = 11f + index * 1.9f + atmosphere.driftScale * 5f
            val drift = (ambienceTime * laneSpeed + index * 61f) % (screenWidth + 64f)
            val x = screenWidth - drift
            val y = groundY - screenHeight * (0.22f + (index % 4) * 0.052f) +
                cos(ambienceTime * 0.82f + index * 0.9f) * (16f + atmosphere.gustStrength * 11f) * atmosphere.driftScale
            val size = 5.5f + (index % 3) * 1.6f
            leafPaint.color = Color.argb(
                (42f + atmosphere.nightFactor * 18f + bloomLevel * 14f).toInt().coerceIn(0, 110),
                ((Color.red(foliage) * 0.58f) + 56f).toInt().coerceIn(0, 255),
                ((Color.green(foliage) * 0.74f) + 48f).toInt().coerceIn(0, 255),
                ((Color.blue(foliage) * 0.46f) + 38f + bloomLevel * 14f).toInt().coerceIn(0, 255)
            )
            canvas.drawOval(x, y, x + size * 1.35f, y + size * 0.78f, leafPaint)
        }

        repeat(atmosphere.petalCount) { index ->
            val drift = (ambienceTime * (13f + index * 1.9f) * atmosphere.driftScale + index * 54f) % (screenWidth + 60f)
            val x = screenWidth - drift
            val y = groundY - screenHeight * (0.16f + (index % 4) * 0.05f) +
                cos(ambienceTime * 1.4f + index * 0.7f) * (9f + atmosphere.gustStrength * 8f) * atmosphere.driftScale
            petalPaint.color = Color.argb(
                (74f + bloomLevel * 34f + atmosphere.nightFactor * 10f).toInt().coerceIn(0, 150),
                255,
                (206f + bloomLevel * 20f).toInt().coerceAtMost(255),
                (226f + bloomLevel * 18f).toInt().coerceAtMost(255)
            )
            canvas.drawOval(x, y, x + 10f, y + 6f, petalPaint)
        }

        repeat(atmosphere.petalTrailCount) { index ->
            val drift = (ambienceTime * (9.5f + index * 1.35f) * atmosphere.driftScale + index * 43f) % (screenWidth + 52f)
            val x = screenWidth - drift
            val y = groundY - screenHeight * (0.24f + (index % 5) * 0.038f) +
                sin(ambienceTime * 1.18f + index * 0.55f) * (12f + atmosphere.gustStrength * 9f) * atmosphere.driftScale
            petalPaint.color = Color.argb(
                (38f + bloomLevel * 26f + atmosphere.nightFactor * 12f).toInt().coerceIn(0, 110),
                255,
                (188f + bloomLevel * 24f).toInt().coerceAtMost(255),
                (214f + bloomLevel * 20f).toInt().coerceAtMost(255)
            )
            canvas.drawOval(x, y, x + 7.5f, y + 4.2f, petalPaint)
        }

        if (atmosphere.horizonGlowAlpha > 0) {
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

        repeat(atmosphere.mistBandCount) { index ->
            mistBandPaint.shader = LinearGradient(
                0f,
                groundY - screenHeight * (0.16f - index * 0.028f),
                0f,
                groundY + screenHeight * (0.02f + index * 0.014f),
                intArrayOf(
                    Color.argb(0, 255, 255, 255),
                    Color.argb(
                        (atmosphere.mistBandAlpha * (1f - index * 0.14f)).toInt().coerceIn(0, 110),
                        Color.red(lighting.mistColor),
                        Color.green(lighting.mistColor),
                        Color.blue(lighting.mistColor)
                    ),
                    Color.argb(0, 255, 255, 255)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            val offset = sin(ambienceTime * (0.42f + index * 0.11f)) *
                (18f + index * 5f + atmosphere.gustStrength * 16f) * atmosphere.driftScale
            canvas.drawRect(
                offset - 40f,
                groundY - screenHeight * (0.15f - index * 0.028f),
                screenWidth + 40f + offset,
                groundY + screenHeight * (0.012f + index * 0.022f),
                mistBandPaint
            )
        }

        repeat(atmosphere.fireflyCount) { index ->
            val x = screenWidth * (0.10f + ((index * 0.11f) % 0.76f)) + sin(ambienceTime * (1.0f + index * 0.06f) + index) * 16f
            val y = screenHeight * (0.18f + (index % 5) * 0.06f) +
                cos(ambienceTime * 1.5f + index * 0.8f) * (10f + atmosphere.gustStrength * 6f)
            fireflyPaint.color = Color.argb(
                (90f + atmosphere.nightFactor * 70f + bloomLevel * 18f).toInt().coerceIn(0, 210),
                Color.red(lighting.glowMoteColor),
                Color.green(lighting.glowMoteColor),
                Color.blue(lighting.glowMoteColor)
            )
            canvas.drawCircle(x, y, 3.8f + (index % 2), fireflyPaint)
            fireflyPaint.color = Color.argb(
                (42f + atmosphere.nightFactor * 30f + bloomLevel * 10f).toInt().coerceIn(0, 120),
                Color.red(lighting.glowMoteColor),
                Color.green(lighting.glowMoteColor),
                Color.blue(lighting.glowMoteColor)
            )
            canvas.drawCircle(x, y, 8.5f + (index % 3), fireflyPaint)
        }

        repeat(atmosphere.glowMoteCount) { index ->
            val x = screenWidth * (0.08f + ((index * 0.17f) % 0.82f)) +
                cos(ambienceTime * (0.58f + index * 0.04f) + index) * (20f + atmosphere.gustStrength * 12f) * atmosphere.driftScale
            val y = groundY - screenHeight * (0.06f + (index % 4) * 0.032f) +
                sin(ambienceTime * (0.74f + index * 0.06f) + index * 0.7f) * (14f + atmosphere.gustStrength * 8f)
            glowMotePaint.color = Color.argb(
                (26f + atmosphere.nightFactor * 18f + bloomLevel * 34f).toInt().coerceIn(0, 120),
                Color.red(lighting.glowMoteColor),
                Color.green(lighting.glowMoteColor),
                Color.blue(lighting.glowMoteColor)
            )
            canvas.drawCircle(x, y, 5.5f + (index % 3) * 1.8f, glowMotePaint)
            glowMotePaint.color = Color.argb(
                (12f + atmosphere.nightFactor * 12f + bloomLevel * 22f).toInt().coerceIn(0, 72),
                Color.red(lighting.glowMoteColor),
                Color.green(lighting.glowMoteColor),
                Color.blue(lighting.glowMoteColor)
            )
            canvas.drawCircle(x, y, 12f + (index % 2) * 3.5f, glowMotePaint)
        }
    }

    private fun drawBloomTransformation(canvas: Canvas) {
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

        val pulse = 0.62f + 0.38f * sin(bloomPulse)
        val worldStrength = (
            bloomStrength * (0.82f + 0.18f * pulse) +
                activationBoost * 0.45f +
                afterglowStrength * (0.22f + 0.10f * pulse)
            ).coerceIn(0f, 1f)

        bloomSkyPaint.color = Color.argb((40f + 70f * worldStrength).toInt().coerceIn(0, 180), 255, 208, 120)
        bloomHorizonPaint.color = Color.argb(
            (55f + 90f * worldStrength).toInt().coerceIn(0, 210),
            Color.red(lighting.horizonGlowColor),
            Color.green(lighting.horizonGlowColor),
            Color.blue(lighting.horizonGlowColor)
        )
        bloomFloorPaint.color = Color.argb((35f + 85f * worldStrength).toInt().coerceIn(0, 190), 170, 255, 145)
        bloomOrbPaint.color = Color.argb(
            (55f + 120f * worldStrength).toInt().coerceIn(0, 220),
            Color.red(lighting.glowMoteColor),
            Color.green(lighting.glowMoteColor),
            Color.blue(lighting.glowMoteColor)
        )
        bloomRayPaint.color = Color.argb(
            (25f + 80f * worldStrength).toInt().coerceIn(0, 180),
            Color.red(lighting.horizonGlowColor),
            Color.green(lighting.horizonGlowColor),
            Color.blue(lighting.horizonGlowColor)
        )

        canvas.drawRect(skyRect, bloomSkyPaint)
        canvas.drawRect(bloomHorizonRect, bloomHorizonPaint)
        canvas.drawRect(floorRect, bloomFloorPaint)

        val orbY = groundY - screenHeight * 0.20f
        val orbRadius = screenHeight * (0.06f + 0.02f * pulse)
        canvas.drawCircle(screenWidth * 0.18f, orbY, orbRadius, bloomOrbPaint)
        canvas.drawCircle(screenWidth * 0.52f, orbY - screenHeight * 0.05f, orbRadius * 0.78f, bloomOrbPaint)
        canvas.drawCircle(screenWidth * 0.82f, orbY + screenHeight * 0.03f, orbRadius * 0.92f, bloomOrbPaint)

        if (afterglowStrength > 0.01f) {
            repeat(5) { index ->
                val lanePulse = 0.5f + 0.5f * sin(bloomPulse * (0.8f + index * 0.08f) + index)
                bloomOrbPaint.alpha = (28f + 52f * afterglowStrength * lanePulse).toInt().coerceIn(0, 120)
                val moteX = screenWidth * (0.14f + index * 0.18f) + sin(ambienceTime * (0.45f + index * 0.05f)) * 18f
                val moteY = groundY - screenHeight * (0.10f + (index % 3) * 0.035f)
                canvas.drawCircle(moteX, moteY, screenHeight * (0.012f + index * 0.0018f), bloomOrbPaint)
            }
        }

        val rayTop = groundY - screenHeight * 0.34f
        val rayBottom = groundY + screenHeight * 0.02f
        canvas.drawLine(screenWidth * 0.16f, rayTop, screenWidth * 0.10f, rayBottom, bloomRayPaint)
        canvas.drawLine(screenWidth * 0.38f, rayTop - 30f, screenWidth * 0.33f, rayBottom, bloomRayPaint)
        canvas.drawLine(screenWidth * 0.62f, rayTop - 20f, screenWidth * 0.68f, rayBottom, bloomRayPaint)
        canvas.drawLine(screenWidth * 0.84f, rayTop, screenWidth * 0.90f, rayBottom, bloomRayPaint)
    }

    // ── Phase 24: Rich bitmap builder ─────────────────────────────────────

    /**
     * Builds a detailed 2× wide bitmap for a given layer index.
     *
     * Layer 0: Sky gradient + sun/moon + clouds
     * Layer 1: Mountain/hill silhouettes — biome-aware (falls back to generic forest)
     * Layer 2: Ground path — grass tufts + pebble dots
     * Layer 3: Near foreground strip — bright grass with colour accent edge
     */
    private fun buildPlaceholderBitmap(layerIndex: Int, biome: Biome): Bitmap {
        val bmpW = screenWidth * 2
        val bmpH = screenHeight
        val bmp    = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rng    = java.util.Random(layerIndex * 777L + 13L)
        val profile = buildBiomeSceneArtProfile(biome)

        when (layerIndex) {
            0 -> drawSkyLayer(canvas, bmpW, bmpH, rng, biome, profile)
            1 -> drawMidLayer(canvas, bmpW, bmpH, rng, biome, profile)
            2 -> drawGroundLayer(canvas, bmpW, bmpH, rng, biome, profile)
            3 -> drawForegroundLayer(canvas, bmpW, bmpH, rng, biome, profile)
        }
        return bmp
    }

    // ── Layer painters ─────────────────────────────────────────────────────

    private fun drawSkyLayer(canvas: Canvas, w: Int, h: Int, rng: java.util.Random, biome: Biome, profile: BiomeSceneArtProfile) {
        val groundLine = h * 0.78f

        // Sky gradient — deep sky blue → horizon amber
        val gradPaint = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, groundLine,
                intArrayOf(
                    biome.skyTopColour,
                    blendColor(biome.skyTopColour, biome.skyBottomColour, 0.55f),
                    biome.skyBottomColour
                ),
                floatArrayOf(0f, 0.55f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), groundLine, gradPaint)

        when (profile.skyFeature) {
            SceneSkyFeature.SUN, SceneSkyFeature.FILTERED_SUN -> {
                val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = profile.featureColor }
                val sunX = when (biome) {
                    Biome.MEADOW -> w * 0.72f
                    Biome.ORCHARD -> w * 0.64f
                    Biome.DUSK_CANYON -> w * 0.80f
                    else -> w * 0.58f
                }
                val sunY = if (profile.skyFeature == SceneSkyFeature.FILTERED_SUN) h * 0.12f else h * 0.16f
                val sunR = h * if (biome == Biome.DUSK_CANYON) 0.065f else 0.055f
                canvas.drawCircle(sunX, sunY, sunR, sunPaint)
                val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(72, Color.red(profile.accentGlowColor), Color.green(profile.accentGlowColor), Color.blue(profile.accentGlowColor)) }
                canvas.drawCircle(sunX, sunY, sunR * 1.65f, haloPaint)
                if (profile.skyFeature == SceneSkyFeature.FILTERED_SUN) {
                    val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(54, Color.red(profile.accentGlowColor), Color.green(profile.accentGlowColor), Color.blue(profile.accentGlowColor)) }
                    repeat(4) { index ->
                        val bx = sunX - h * 0.22f + index * h * 0.11f
                        canvas.drawRect(bx, sunY, bx + h * 0.03f, groundLine - h * 0.18f, beamPaint)
                    }
                }
            }
            SceneSkyFeature.MOON -> {
                val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = profile.featureColor }
                val moonX = w * 0.76f
                val moonY = h * 0.15f
                val moonR = h * 0.05f
                canvas.drawCircle(moonX, moonY, moonR, moonPaint)
                val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = biome.skyTopColour }
                canvas.drawCircle(moonX + moonR * 0.36f, moonY - moonR * 0.10f, moonR * 0.92f, cutPaint)
                val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 220, 236, 255) }
                repeat(14) { index ->
                    val sx = rng.nextFloat() * w
                    val sy = h * (0.05f + rng.nextFloat() * 0.28f)
                    canvas.drawCircle(sx, sy, 1.5f + (index % 2), starPaint)
                }
            }
        }

        // Cloud puffs — 6 clouds scattered across the wide bitmap
        val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (biome == Biome.NIGHT_FOREST) Color.argb(76, 220, 228, 255) else Color.argb(190, 255, 255, 255)
        }
        repeat(if (biome == Biome.DUSK_CANYON) 3 else 6) {
            val cx   = rng.nextFloat() * w
            val cy   = h * (0.08f + rng.nextFloat() * 0.28f)
            val crw  = h * (0.05f + rng.nextFloat() * 0.10f)
            val crh  = crw * 0.5f
            for (puff in -2..2) {
                canvas.drawOval(
                    cx + puff * crw * 0.65f - crw,
                    cy - crh * (0.5f + 0.3f * Math.abs(puff)),
                    cx + puff * crw * 0.65f + crw,
                    cy + crh,
                    cloudPaint
                )
            }
        }

        // Horizon glow
        val horizPaint = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, groundLine - h * 0.12f, 0f, groundLine + h * 0.04f,
                intArrayOf(
                    Color.argb(0, Color.red(profile.accentGlowColor), Color.green(profile.accentGlowColor), Color.blue(profile.accentGlowColor)),
                    Color.argb(140, Color.red(profile.featureColor), Color.green(profile.featureColor), Color.blue(profile.featureColor))
                ),
                null, android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, groundLine - h * 0.12f, w.toFloat(), groundLine + h * 0.04f, horizPaint)
    }

    private fun drawMidLayer(canvas: Canvas, w: Int, h: Int, rng: java.util.Random, biome: Biome, profile: BiomeSceneArtProfile) {
        val groundLine = h * 0.78f

        when (profile.ridgeStyle) {
            SceneRidgeStyle.HILLS -> drawRollingHills(canvas, w, h, groundLine, rng, profile.ridgeColors)
            SceneRidgeStyle.ORCHARD_ROWS -> drawOrchardRows(canvas, w, h, groundLine, rng, profile.ridgeColors)
            SceneRidgeStyle.GROVE_SPIRES -> drawGroveSpires(canvas, w, h, groundLine, rng, profile.ridgeColors)
            SceneRidgeStyle.CANYON_MESAS -> drawCanyonMesas(canvas, w, h, groundLine, rng, profile.ridgeColors)
            SceneRidgeStyle.NIGHT_PINES -> drawNightPines(canvas, w, h, groundLine, rng, profile.ridgeColors)
        }

        if (biome != Biome.DUSK_CANYON) {
            drawTreeSilhouettes(canvas, w, h, bands[1], Color.argb(255, 12, 42, 18))
        }
    }

    private fun drawGroundLayer(canvas: Canvas, w: Int, h: Int, rng: java.util.Random, biome: Biome, profile: BiomeSceneArtProfile) {
        val groundLine = h * 0.78f

        // Base earth fill
        val earthPaint = Paint().apply { color = blendColor(biome.groundColour, Color.rgb(46, 68, 34), 0.22f) }
        canvas.drawRect(0f, groundLine, w.toFloat(), h.toFloat(), earthPaint)

        // Darker path strip (where the player runs)
        val pathPaint = Paint().apply { color = blendColor(biome.groundColour, Color.WHITE, 0.12f) }
        canvas.drawRect(0f, groundLine, w.toFloat(), groundLine + h * 0.06f, pathPaint)

        // Grass tufts
        val tuftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 190, 75); style = Paint.Style.STROKE; strokeWidth = 3f
        }
        var tx = 0f
        while (tx < w) {
            val ty = groundLine + rng.nextFloat() * h * 0.03f
            val th = h * 0.015f + rng.nextFloat() * h * 0.02f
            canvas.drawLine(tx, ty, tx - 6f + rng.nextFloat() * 12f, ty - th, tuftPaint)
            tx += 14f + rng.nextFloat() * 22f
        }

        // Small pebble dots
        val pebblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(130, 110, 80) }
        repeat(60) {
            val px = rng.nextFloat() * w
            val py = groundLine + 4f + rng.nextFloat() * h * 0.04f
            val pr = 3f + rng.nextFloat() * 5f
            canvas.drawOval(px - pr, py - pr * 0.5f, px + pr, py + pr * 0.5f, pebblePaint)
        }

        // Exposed roots / cracks
        val rootPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(60, 40, 20); style = Paint.Style.STROKE; strokeWidth = 2f
        }
        repeat(8) {
            val rx = rng.nextFloat() * w
            val ry = groundLine + h * 0.015f
            canvas.drawLine(rx, ry, rx + rng.nextFloat() * 40f - 20f, ry + h * 0.03f, rootPaint)
        }

        when (profile.groundAccent) {
            SceneGroundAccent.FLOWERS -> repeat(18) { index ->
                val fx = w * (0.04f + index * 0.055f)
                val fy = groundLine + h * 0.03f + (index % 3) * 6f
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (index % 2 == 0) Color.rgb(255, 214, 228) else Color.rgb(248, 236, 164) }
                canvas.drawCircle(fx, fy, 4f, paint)
            }
            SceneGroundAccent.PETALS -> repeat(26) { index ->
                val px = w * (0.03f + index * 0.038f)
                val py = groundLine + h * 0.028f + (index % 4) * 4f
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 255, 210, 228) }
                canvas.drawOval(px - 4f, py - 2f, px + 4f, py + 2f, paint)
            }
            SceneGroundAccent.FERNS -> repeat(12) { index ->
                val fx = w * (0.05f + index * 0.075f)
                val fy = groundLine + h * 0.014f
                val fernPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(82, 148, 88); style = Paint.Style.STROKE; strokeWidth = 2.5f }
                canvas.drawLine(fx, fy + 12f, fx, fy - 10f, fernPaint)
                canvas.drawLine(fx, fy, fx - 8f, fy - 6f, fernPaint)
                canvas.drawLine(fx, fy + 3f, fx + 8f, fy - 4f, fernPaint)
            }
            SceneGroundAccent.STONES -> repeat(18) { index ->
                val sx = w * (0.05f + index * 0.05f)
                val sy = groundLine + h * 0.032f + (index % 2) * 3f
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(172, 118, 84) }
                canvas.drawOval(sx - 6f, sy - 3f, sx + 6f, sy + 3f, paint)
            }
            SceneGroundAccent.GLOW_MUSHROOMS -> repeat(10) { index ->
                val mx = w * (0.07f + index * 0.09f)
                val my = groundLine + h * 0.03f
                val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(168, 192, 206); strokeWidth = 2f }
                val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 178, 220, 255) }
                canvas.drawLine(mx, my + 8f, mx, my, stemPaint)
                canvas.drawOval(mx - 7f, my - 4f, mx + 7f, my + 4f, capPaint)
            }
        }
    }

    private fun drawForegroundLayer(canvas: Canvas, w: Int, h: Int, rng: java.util.Random, biome: Biome, profile: BiomeSceneArtProfile) {
        val groundLine = h * 0.88f

        // Near bright-grass strip
        val stripPaint = Paint().apply { color = blendColor(biome.groundColour, Color.rgb(112, 164, 88), 0.24f) }
        canvas.drawRect(0f, groundLine, w.toFloat(), h.toFloat(), stripPaint)

        // Vivid top edge
        val edgePaint = Paint().apply { color = blendColor(profile.accentGlowColor, biome.groundColour, 0.28f) }
        canvas.drawRect(0f, groundLine, w.toFloat(), groundLine + 7f, edgePaint)

        // Large foreground blade tufts
        val bladePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(120, 220, 85); style = Paint.Style.STROKE; strokeWidth = 4f
        }
        var bx = 0f
        while (bx < w) {
            val bh = h * 0.025f + rng.nextFloat() * h * 0.04f
            canvas.drawLine(bx, groundLine, bx + rng.nextFloat() * 16f - 8f, groundLine - bh, bladePaint)
            bx += 18f + rng.nextFloat() * 28f
        }

        if (profile.groundAccent == SceneGroundAccent.STONES) {
            val rockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(118, 88, 62) }
            repeat(9) { index ->
                val x = w * (0.08f + index * 0.1f)
                canvas.drawOval(x - 10f, groundLine - 4f, x + 10f, groundLine + 10f, rockPaint)
            }
        }
    }

    private fun drawRollingHills(canvas: Canvas, w: Int, h: Int, groundLine: Float, rng: java.util.Random, ridgeColors: IntArray) {
        val ridgeHeights = floatArrayOf(0.46f, 0.56f, 0.64f)
        drawRidgelineSeries(canvas, w, h, groundLine, rng, ridgeColors, ridgeHeights, peakVariance = 0.08f, segmentMin = 120f, segmentMax = 220f)
    }

    private fun drawOrchardRows(canvas: Canvas, w: Int, h: Int, groundLine: Float, rng: java.util.Random, ridgeColors: IntArray) {
        drawRidgelineSeries(canvas, w, h, groundLine, rng, ridgeColors, floatArrayOf(0.48f, 0.58f, 0.66f), 0.06f, 90f, 180f)
        val treePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 202, 132, 164) }
        val trunkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(78, 48, 34) }
        repeat(14) { index ->
            val x = w * (0.06f + index * 0.07f)
            val y = groundLine - h * (0.08f + (index % 2) * 0.03f)
            canvas.drawRect(x - 3f, y + 10f, x + 3f, groundLine, trunkPaint)
            canvas.drawOval(x - 18f, y - 10f, x + 18f, y + 16f, treePaint)
        }
    }

    private fun drawGroveSpires(canvas: Canvas, w: Int, h: Int, groundLine: Float, rng: java.util.Random, ridgeColors: IntArray) {
        drawRidgelineSeries(canvas, w, h, groundLine, rng, ridgeColors, floatArrayOf(0.40f, 0.50f, 0.60f), 0.10f, 80f, 140f)
        val trunkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 20, 44, 24) }
        repeat(18) { index ->
            val x = w * (0.04f + index * 0.055f)
            val top = groundLine - h * (0.22f + (index % 3) * 0.08f)
            canvas.drawRect(x - 4f, top, x + 4f, groundLine, trunkPaint)
        }
    }

    private fun drawCanyonMesas(canvas: Canvas, w: Int, h: Int, groundLine: Float, rng: java.util.Random, ridgeColors: IntArray) {
        val mesaPaints = ridgeColors.map { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = it } }
        mesaPaints.forEachIndexed { index, paint ->
            repeat(6 - index) { mesa ->
                val left = w * (0.04f + mesa * 0.16f) + index * 22f
                val top = groundLine - h * (0.16f + index * 0.09f + (mesa % 2) * 0.03f)
                canvas.drawRect(left, top, left + w * 0.10f, groundLine, paint)
            }
        }
    }

    private fun drawNightPines(canvas: Canvas, w: Int, h: Int, groundLine: Float, rng: java.util.Random, ridgeColors: IntArray) {
        drawRidgelineSeries(canvas, w, h, groundLine, rng, ridgeColors, floatArrayOf(0.44f, 0.54f, 0.64f), 0.05f, 120f, 220f)
        val pinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(228, 16, 34, 38) }
        repeat(18) { index ->
            val cx = w * (0.04f + index * 0.055f)
            val baseY = groundLine
            val topY = groundLine - h * (0.12f + (index % 4) * 0.03f)
            val path = android.graphics.Path()
            path.moveTo(cx, topY)
            path.lineTo(cx - 18f, baseY)
            path.lineTo(cx + 18f, baseY)
            path.close()
            canvas.drawPath(path, pinePaint)
        }
    }

    private fun drawRidgelineSeries(
        canvas: Canvas,
        w: Int,
        h: Int,
        groundLine: Float,
        rng: java.util.Random,
        ridgeColors: IntArray,
        ridgeHeights: FloatArray,
        peakVariance: Float,
        segmentMin: Float,
        segmentMax: Float
    ) {
        for (r in ridgeColors.indices) {
            val ridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ridgeColors[r] }
            val path = android.graphics.Path()
            path.moveTo(0f, groundLine)
            var x = 0f
            while (x < w + 120f) {
                val peakH = h * (ridgeHeights[r] + rng.nextFloat() * peakVariance)
                path.lineTo(x, peakH)
                x += segmentMin + rng.nextFloat() * (segmentMax - segmentMin)
            }
            path.lineTo(w.toFloat(), groundLine)
            path.close()
            canvas.drawPath(path, ridgePaint)
        }
    }

    private fun blendColor(from: Int, to: Int, t: Float): Int {
        val clamped = t.coerceIn(0f, 1f)
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * clamped).toInt().coerceIn(0, 255)
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * clamped).toInt().coerceIn(0, 255)
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * clamped).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    // ── Existing tree-silhouette helper (unchanged API) ─────────────────────

    /**
     * Draws a row of simple rounded \"tree crown\" silhouettes across the bitmap.
     */
    private fun drawTreeSilhouettes(
        canvas: Canvas,
        bmpW: Int,
        bmpH: Int,
        band: LayerBand,
        colour: Int
    ) {
        val treePaint = Paint().apply {
            this.color = colour
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val trunkPaint = Paint().apply {
            this.color = Color.rgb(30, 20, 10)
            style = Paint.Style.FILL
        }

        val groundLine = bmpH * band.bottomFrac
        var tx = 80f
        val rng = java.util.Random(42L)

        while (tx < bmpW + 120f) {
            val crownR = 55f + rng.nextFloat() * 60f
            val crownY = groundLine - crownR * 1.8f + rng.nextFloat() * 30f
            val trunkW = 14f + rng.nextFloat() * 8f

            canvas.drawRect(
                tx - trunkW / 2f,
                crownY + crownR * 0.6f,
                tx + trunkW / 2f,
                groundLine,
                trunkPaint
            )
            canvas.drawOval(
                tx - crownR, crownY - crownR * 0.6f,
                tx + crownR, crownY + crownR,
                treePaint
            )
            tx += 90f + rng.nextFloat() * 80f
        }
    }
}
