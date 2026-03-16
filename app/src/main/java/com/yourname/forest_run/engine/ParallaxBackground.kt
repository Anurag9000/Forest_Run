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

internal data class ParallaxAtmosphereProfile(
    val worldScale: Float,
    val driftScale: Float,
    val leafCount: Int,
    val petalCount: Int,
    val fireflyCount: Int,
    val ribbonCount: Int,
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
        leafCount = (5 + speedLift * 6f + bloom * 4f).toInt().coerceAtLeast(4),
        petalCount = (3 + bloom * 7f + nightFactor * 2f).toInt().coerceAtLeast(2),
        fireflyCount = (nightFactor * 8f + bloom * 4f).toInt().coerceAtLeast(if (nightFactor > 0.45f) 3 else 0),
        ribbonCount = (3 + speedLift * 2f + bloom * 1.5f).toInt().coerceIn(3, 6),
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
    private var ambienceTime = 0f
    private var currentScrollSpeed = GameConstants.BASE_SCROLL_SPEED

    init {
        groundY = screenHeight * 0.82f

        layers = Array(4) { i ->
            val bmp = buildPlaceholderBitmap(i)
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
            bloomStrength = bloomLevel,
            skyTop = skyOverlayTop.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[0],
            skyBottom = skyOverlayBottom.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[0]
        )

        // Draw back → front
        canvas.save()
        canvas.scale(atmosphere.worldScale, atmosphere.worldScale, screenWidth * 0.5f, groundY * 0.55f)
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

    fun setBloomState(isActive: Boolean, activationLevel: Float) {
        bloomTarget = if (isActive) 1f else 0f
        bloomActivationLevel = activationLevel.coerceIn(0f, 1f)
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

    private fun drawBiomeOverlays(canvas: Canvas, atmosphere: ParallaxAtmosphereProfile) {
        val top = skyOverlayTop.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[0]
        val bottom = skyOverlayBottom.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[0]
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
                Color.argb((atmosphere.canopyShadowAlpha * 0.55f).toInt().coerceIn(0, 100), 8, 14, 10),
                Color.argb(atmosphere.canopyShadowAlpha, 16, 28, 18),
                Color.argb(0, 16, 28, 18)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, groundY - screenHeight * 0.42f, screenWidth.toFloat(), groundY, canopyShadowPaint)
    }

    private fun drawAmbientLife(canvas: Canvas, atmosphere: ParallaxAtmosphereProfile) {
        val foliage = foliageOverlay.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[1]
        val skyBottom = skyOverlayBottom.takeUnless { it == Color.TRANSPARENT } ?: placeholderColours[0]
        val ribbonY = groundY - screenHeight * 0.22f
        repeat(atmosphere.ribbonCount) { index ->
            windRibbonPaint.color = Color.argb(
                (atmosphere.windRibbonAlpha * (0.65f + index * 0.12f)).toInt().coerceIn(0, 140),
                (210 + Color.red(skyBottom) * 0.10f).toInt().coerceAtMost(255),
                (225 + Color.green(skyBottom) * 0.08f).toInt().coerceAtMost(255),
                (214 + Color.blue(skyBottom) * 0.06f).toInt().coerceAtMost(255)
            )
            val startX =
                screenWidth * (0.06f + index * 0.15f) +
                    sin(ambienceTime * (0.7f + index * 0.18f)) * 20f * atmosphere.driftScale
            val endX = startX + screenWidth * (0.18f + index * 0.015f)
            val y = ribbonY + index * screenHeight * 0.036f +
                cos(ambienceTime * (0.9f + index * 0.22f)) * 14f * atmosphere.driftScale
            canvas.drawLine(startX, y, endX, y - 10f * atmosphere.driftScale, windRibbonPaint)
        }

        repeat(atmosphere.leafCount) { index ->
            val laneSpeed = 18f + index * 2.7f + (currentScrollSpeed / GameConstants.BASE_SCROLL_SPEED) * 8f * atmosphere.driftScale
            val drift = (ambienceTime * laneSpeed + index * 37f) % (screenWidth + 80f)
            val x = screenWidth - drift
            val y = groundY - screenHeight * (0.10f + (index % 5) * 0.045f) +
                sin(ambienceTime * 1.1f + index) * 12f * atmosphere.driftScale
            val size = 8f + (index % 3) * 2.5f
            leafPaint.color = Color.argb(
                (64f + atmosphere.nightFactor * 24f + bloomLevel * 18f).toInt().coerceIn(0, 150),
                ((Color.red(foliage) * 0.70f) + 72f).toInt().coerceIn(0, 255),
                ((Color.green(foliage) * 0.82f) + 56f).toInt().coerceIn(0, 255),
                ((Color.blue(foliage) * 0.55f) + 48f + bloomLevel * 20f).toInt().coerceIn(0, 255)
            )
            canvas.drawOval(x, y, x + size * 1.6f, y + size, leafPaint)
        }

        repeat(atmosphere.petalCount) { index ->
            val drift = (ambienceTime * (13f + index * 1.9f) * atmosphere.driftScale + index * 54f) % (screenWidth + 60f)
            val x = screenWidth - drift
            val y = groundY - screenHeight * (0.16f + (index % 4) * 0.05f) +
                cos(ambienceTime * 1.4f + index * 0.7f) * 9f * atmosphere.driftScale
            petalPaint.color = Color.argb(
                (74f + bloomLevel * 34f + atmosphere.nightFactor * 10f).toInt().coerceIn(0, 150),
                255,
                (206f + bloomLevel * 20f).toInt().coerceAtMost(255),
                (226f + bloomLevel * 18f).toInt().coerceAtMost(255)
            )
            canvas.drawOval(x, y, x + 10f, y + 6f, petalPaint)
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

        repeat(2) { index ->
            mistBandPaint.shader = LinearGradient(
                0f,
                groundY - screenHeight * (0.14f - index * 0.03f),
                0f,
                groundY + screenHeight * (0.02f + index * 0.01f),
                intArrayOf(
                    Color.argb(0, 255, 255, 255),
                    Color.argb(
                        (atmosphere.mistBandAlpha * (1f - index * 0.18f)).toInt().coerceIn(0, 110),
                        (220 + Color.red(skyBottom) * 0.10f).toInt().coerceAtMost(255),
                        (224 + Color.green(skyBottom) * 0.08f).toInt().coerceAtMost(255),
                        (218 + Color.blue(skyBottom) * 0.08f).toInt().coerceAtMost(255)
                    ),
                    Color.argb(0, 255, 255, 255)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            val offset = sin(ambienceTime * (0.45f + index * 0.15f)) * 22f * atmosphere.driftScale
            canvas.drawRect(
                offset - 40f,
                groundY - screenHeight * (0.13f - index * 0.03f),
                screenWidth + 40f + offset,
                groundY + screenHeight * (0.01f + index * 0.02f),
                mistBandPaint
            )
        }

        repeat(atmosphere.fireflyCount) { index ->
            val x = screenWidth * (0.10f + ((index * 0.11f) % 0.76f)) + sin(ambienceTime * (1.0f + index * 0.06f) + index) * 16f
            val y = screenHeight * (0.18f + (index % 5) * 0.06f) + cos(ambienceTime * 1.5f + index * 0.8f) * 10f
            fireflyPaint.color = Color.argb(
                (90f + atmosphere.nightFactor * 70f + bloomLevel * 18f).toInt().coerceIn(0, 210),
                248,
                244,
                (162f + bloomLevel * 20f).toInt().coerceAtMost(255)
            )
            canvas.drawCircle(x, y, 3.8f + (index % 2), fireflyPaint)
            fireflyPaint.color = Color.argb(
                (42f + atmosphere.nightFactor * 30f + bloomLevel * 10f).toInt().coerceIn(0, 120),
                248,
                244,
                (170f + bloomLevel * 14f).toInt().coerceAtMost(255)
            )
            canvas.drawCircle(x, y, 8.5f + (index % 3), fireflyPaint)
        }
    }

    private fun drawBloomTransformation(canvas: Canvas) {
        val bloomStrength = bloomLevel.coerceIn(0f, 1f)
        val activationBoost = bloomActivationLevel.coerceIn(0f, 1f)
        if (bloomStrength <= 0.01f && activationBoost <= 0.01f) return

        val pulse = 0.62f + 0.38f * sin(bloomPulse)
        val worldStrength = (bloomStrength * (0.82f + 0.18f * pulse) + activationBoost * 0.45f).coerceIn(0f, 1f)

        bloomSkyPaint.alpha = (40f + 70f * worldStrength).toInt().coerceIn(0, 180)
        bloomHorizonPaint.alpha = (55f + 90f * worldStrength).toInt().coerceIn(0, 210)
        bloomFloorPaint.alpha = (35f + 85f * worldStrength).toInt().coerceIn(0, 190)
        bloomOrbPaint.alpha = (55f + 120f * worldStrength).toInt().coerceIn(0, 220)
        bloomRayPaint.alpha = (25f + 80f * worldStrength).toInt().coerceIn(0, 180)

        canvas.drawRect(skyRect, bloomSkyPaint)
        canvas.drawRect(bloomHorizonRect, bloomHorizonPaint)
        canvas.drawRect(floorRect, bloomFloorPaint)

        val orbY = groundY - screenHeight * 0.20f
        val orbRadius = screenHeight * (0.06f + 0.02f * pulse)
        canvas.drawCircle(screenWidth * 0.18f, orbY, orbRadius, bloomOrbPaint)
        canvas.drawCircle(screenWidth * 0.52f, orbY - screenHeight * 0.05f, orbRadius * 0.78f, bloomOrbPaint)
        canvas.drawCircle(screenWidth * 0.82f, orbY + screenHeight * 0.03f, orbRadius * 0.92f, bloomOrbPaint)

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
    private fun buildPlaceholderBitmap(layerIndex: Int): Bitmap {
        val bmpW = screenWidth * 2
        val bmpH = screenHeight
        val bmp    = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rng    = java.util.Random(layerIndex * 777L + 13L)

        when (layerIndex) {
            0 -> drawSkyLayer(canvas, bmpW, bmpH, rng)
            1 -> drawMidLayer(canvas, bmpW, bmpH, rng)
            2 -> drawGroundLayer(canvas, bmpW, bmpH, rng)
            3 -> drawForegroundLayer(canvas, bmpW, bmpH, rng)
        }
        return bmp
    }

    // ── Layer painters ─────────────────────────────────────────────────────

    private fun drawSkyLayer(canvas: Canvas, w: Int, h: Int, rng: java.util.Random) {
        val groundLine = h * 0.78f

        // Sky gradient — deep sky blue → horizon amber
        val gradPaint = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, groundLine,
                intArrayOf(
                    Color.rgb(15,  35, 90),
                    Color.rgb(60, 100, 170),
                    Color.rgb(130, 180, 230)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), groundLine, gradPaint)

        // Sun — warm glow disc
        val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 225, 100) }
        val sunX     = w * 0.72f
        val sunY     = h * 0.16f
        val sunR     = h * 0.055f
        canvas.drawCircle(sunX, sunY, sunR, sunPaint)
        // Outer halo
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 230, 120) }
        canvas.drawCircle(sunX, sunY, sunR * 1.65f, haloPaint)

        // Cloud puffs — 6 clouds scattered across the wide bitmap
        val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 255, 255, 255) }
        repeat(6) {
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
                intArrayOf(Color.argb(0, 255, 160, 80), Color.argb(140, 255, 130, 50)),
                null, android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, groundLine - h * 0.12f, w.toFloat(), groundLine + h * 0.04f, horizPaint)
    }

    private fun drawMidLayer(canvas: Canvas, w: Int, h: Int, rng: java.util.Random) {
        val groundLine = h * 0.78f

        // Distant mountain range — 3 layers of ridgelines
        val ridgeColours = intArrayOf(
            Color.argb(200, 30,  60, 35),
            Color.argb(220, 22,  80, 42),
            Color.argb(240, 16,  55, 28)
        )
        val ridgeHeights = floatArrayOf(0.42f, 0.52f, 0.62f)

        for (r in 0..2) {
            val ridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ridgeColours[r] }
            val path    = android.graphics.Path()
            path.moveTo(0f, groundLine)

            var x = 0f
            while (x < w + 120f) {
                val peakH = h * (ridgeHeights[r] + rng.nextFloat() * 0.12f)
                path.lineTo(x, peakH)
                x += (60f + rng.nextFloat() * 100f)
            }
            path.lineTo(w.toFloat(), groundLine)
            path.close()
            canvas.drawPath(path, ridgePaint)
        }

        // Detailed tree silhouettes on the nearest ridgeline
        drawTreeSilhouettes(canvas, w, h, bands[1], Color.argb(255, 12, 42, 18))
    }

    private fun drawGroundLayer(canvas: Canvas, w: Int, h: Int, rng: java.util.Random) {
        val groundLine = h * 0.78f

        // Base earth fill
        val earthPaint = Paint().apply { color = Color.rgb(55, 120, 48) }
        canvas.drawRect(0f, groundLine, w.toFloat(), h.toFloat(), earthPaint)

        // Darker path strip (where the player runs)
        val pathPaint = Paint().apply { color = Color.rgb(80, 155, 65) }
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
    }

    private fun drawForegroundLayer(canvas: Canvas, w: Int, h: Int, rng: java.util.Random) {
        val groundLine = h * 0.88f

        // Near bright-grass strip
        val stripPaint = Paint().apply { color = Color.rgb(70, 170, 55) }
        canvas.drawRect(0f, groundLine, w.toFloat(), h.toFloat(), stripPaint)

        // Vivid top edge
        val edgePaint = Paint().apply { color = Color.rgb(110, 210, 80) }
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
