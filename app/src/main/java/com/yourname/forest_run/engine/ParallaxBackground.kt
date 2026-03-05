package com.yourname.forest_run.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

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

    // -----------------------------------------------------------------------
    // Accent paints for layer blending
    // -----------------------------------------------------------------------
    private val layerPaint = Paint()

    init {
        groundY = screenHeight * 0.82f

        layers = Array(4) { i ->
            val bmp = buildPlaceholderBitmap(i)
            ParallaxLayer(bmp, speedFractions[i])
        }

        // Position the floor rect
        floorRect.set(0f, groundY, screenWidth.toFloat(), screenHeight.toFloat())
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    fun update(deltaTime: Float, gameScrollSpeed: Float) {
        for (layer in layers) layer.update(deltaTime, gameScrollSpeed)
    }

    fun draw(canvas: Canvas) {
        // Draw back → front
        for (layer in layers) layer.draw(canvas)

        // Draw the floor band on top of layer 2/3 (solid, not scrolled)
        canvas.drawRect(floorRect, floorPaint)
    }

    /**
     * Swap layer 1 (the biome-specific mid-tree silhouette layer) to a new bitmap.
     * Called by BiomeManager on every 500m transition (Phase 13).
     */
    fun swapBiomeLayer(bitmap: Bitmap) {
        layers[1] = ParallaxLayer(bitmap, speedFractions[1])
    }

    // -----------------------------------------------------------------------
    // Placeholder bitmap builder
    // -----------------------------------------------------------------------

    /**
     * Creates a simple gradient/solid-colour bitmap for the given layer index.
     * Width = 2× screen width so we always have a full copy ready when looping.
     * This is replaced in Phase 24 with real hand-drawn assets.
     */
    private fun buildPlaceholderBitmap(layerIndex: Int): Bitmap {
        val bmpW = screenWidth * 2    // 2× wide for seamless loop
        val bmpH = screenHeight

        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val band = bands[layerIndex]
        val colour = placeholderColours[layerIndex]

        val topY    = bmpH * band.topFrac
        val bottomY = bmpH * band.bottomFrac

        // Layer 0: sky, draw a vertical gradient manually (top=navy, bottom=dusky)
        if (layerIndex == 0) {
            // Fill entire bitmap as sky background first
            canvas.drawColor(Color.rgb(20, 35, 70))
            // Lighter horizon band
            val horizonPaint = Paint().apply { color = Color.rgb(50, 80, 140); alpha = 180 }
            canvas.drawRect(0f, bmpH * 0.55f, bmpW.toFloat(), bmpH * 0.78f, horizonPaint)
        }

        // All layers: draw their colour band
        val p = Paint().apply {
            color = colour
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, topY, bmpW.toFloat(), bottomY, p)

        // Layer 1: add simple tree-silhouette bumps
        if (layerIndex == 1) {
            drawTreeSilhouettes(canvas, bmpW, bmpH, band, Color.rgb(15, 50, 25))
        }

        // Layer 3: add slightly lighter near-grass texture line at top
        if (layerIndex == 3) {
            val topLine = Paint().apply { color = Color.rgb(90, 180, 70); style = Paint.Style.FILL }
            canvas.drawRect(0f, topY, bmpW.toFloat(), topY + 6f, topLine)
        }

        return bmp
    }

    /**
     * Draws a row of simple rounded "tree crown" silhouettes across the bitmap.
     * These are pure placeholder shapes — Phase 24 replaces with pixel art.
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
        val rng = java.util.Random(42L)   // seeded so it's deterministic

        while (tx < bmpW + 120f) {
            val crownR = 55f + rng.nextFloat() * 60f
            val crownY = groundLine - crownR * 1.8f + rng.nextFloat() * 30f
            val trunkW = 14f + rng.nextFloat() * 8f
            val trunkH = crownR * 0.8f

            // Trunk
            canvas.drawRect(
                tx - trunkW / 2f,
                crownY + crownR * 0.6f,
                tx + trunkW / 2f,
                groundLine,
                trunkPaint
            )
            // Crown (ellipse)
            canvas.drawOval(
                tx - crownR, crownY - crownR * 0.6f,
                tx + crownR, crownY + crownR,
                treePaint
            )

            tx += 90f + rng.nextFloat() * 80f
        }
    }
}
