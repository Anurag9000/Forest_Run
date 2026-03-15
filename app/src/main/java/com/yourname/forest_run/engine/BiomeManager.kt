package com.yourname.forest_run.engine

import android.graphics.Color
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.utils.MathUtils

/**
 * Drives biome transitions and day/night tinting.
 *
 * - Tracks the current [Biome] and the next one.
 * - Calculates a smooth [crossfadeAlpha] (0→1) over the last 20% of each biome segment.
 * - Provides [currentSkyTop], [currentSkyBottom], [currentGroundColour] as live blended values.
 * - Provides [ambientAlpha]: an alpha value (0..200) for a dark overlay drawn in GameView
 *   that darkens the scene progressively for DUSK and NIGHT biomes.
 * - Provides [entityPool]: the spawn pool for the current moment (blended between biomes
 *   — at >80% through a biome the next biome's pool starts mixing in at 50/50).
 *
 * All colour blending uses component-wise integer lerp so no garbage is created per frame.
 */
class BiomeManager {

    private val CROSSFADE_START_FRACTION = 0.80f  // Crossfade begins when 80% through a biome
    private val BIOME_LEN = GameConstants.BIOME_LENGTH_METRES

    /** Current fully-resolved biome (at start of this biome segment). */
    var currentBiome: Biome = Biome.MEADOW
        private set

    private var nextBiome: Biome = Biome.next(currentBiome)
    private var forcedDebugBiome: Biome? = null

    /**
     * Crossfade progress 0..1 within the transition window.
     * 0 = fully in [currentBiome], 1 = fully in [nextBiome].
     */
    var crossfadeAlpha: Float = 0f
        private set

    // ── Live blended colours ─────────────────────────────────────────────

    var currentSkyTop:    Int = currentBiome.skyTopColour;    private set
    var currentSkyBottom: Int = currentBiome.skyBottomColour; private set
    var currentGround:    Int = currentBiome.groundColour;    private set
    var currentFoliage:   Int = currentBiome.midFoliageColour; private set

    /**
     * Additional dark overlay alpha (0 = bright midday, 200 = deep night).
     * GameView draws a semi-transparent black rect on top of everything except HUD.
     */
    val ambientAlpha: Int get() {
        val base = ((1f - currentBiome.ambientLightFactor) * 200f).toInt()
        val next = ((1f - nextBiome.ambientLightFactor) * 200f).toInt()
        return lerpInt(base, next, crossfadeAlpha)
    }

    // ── Entity pool ───────────────────────────────────────────────────────

    /**
     * Combined spawn pool for the current moment.
     * In the crossfade window the next biome's pool is included.
     */
    val entityPool: List<EntityType> get() {
        return if (crossfadeAlpha > 0f) {
            (currentBiome.preferredPool + nextBiome.preferredPool).distinct()
        } else {
            currentBiome.preferredPool
        }
    }

    // ── Main update ───────────────────────────────────────────────────────

    /**
     * Call every frame from GameView.update() with current total distance.
     */
    fun update(distanceMetres: Float) {
        forcedDebugBiome?.let { forced ->
            currentBiome = forced
            nextBiome = Biome.next(forced)
            crossfadeAlpha = 0f
            currentSkyTop = currentBiome.skyTopColour
            currentSkyBottom = currentBiome.skyBottomColour
            currentGround = currentBiome.groundColour
            currentFoliage = currentBiome.midFoliageColour
            return
        }

        val progressInBiome  = (distanceMetres % BIOME_LEN) / BIOME_LEN  // 0..1

        val newCurrent = Biome.at(distanceMetres)
        if (newCurrent != currentBiome) {
            currentBiome = newCurrent
            nextBiome    = Biome.next(currentBiome)
        }

        // Crossfade window starts at CROSSFADE_START_FRACTION of the biome
        crossfadeAlpha = if (progressInBiome >= CROSSFADE_START_FRACTION) {
            MathUtils.normalise(progressInBiome, CROSSFADE_START_FRACTION, 1f)
        } else {
            0f
        }

        // Blend colours
        currentSkyTop    = blendColour(currentBiome.skyTopColour,        nextBiome.skyTopColour,        crossfadeAlpha)
        currentSkyBottom = blendColour(currentBiome.skyBottomColour,     nextBiome.skyBottomColour,     crossfadeAlpha)
        currentGround    = blendColour(currentBiome.groundColour,        nextBiome.groundColour,        crossfadeAlpha)
        currentFoliage   = blendColour(currentBiome.midFoliageColour,    nextBiome.midFoliageColour,    crossfadeAlpha)
    }

    fun forceDebugBiome(biome: Biome?) {
        forcedDebugBiome = biome
        if (biome != null) {
            currentBiome = biome
            nextBiome = Biome.next(biome)
            crossfadeAlpha = 0f
            currentSkyTop = biome.skyTopColour
            currentSkyBottom = biome.skyBottomColour
            currentGround = biome.groundColour
            currentFoliage = biome.midFoliageColour
        }
    }

    // ── Colour helpers ────────────────────────────────────────────────────

    private fun blendColour(from: Int, to: Int, t: Float): Int {
        val a = lerpInt(Color.alpha(from), Color.alpha(to), t)
        val r = lerpInt(Color.red(from),   Color.red(to),   t)
        val g = lerpInt(Color.green(from), Color.green(to), t)
        val b = lerpInt(Color.blue(from),  Color.blue(to),  t)
        return Color.argb(a, r, g, b)
    }

    private fun lerpInt(a: Int, b: Int, t: Float): Int =
        (a + (b - a) * t).toInt().coerceIn(0, 255)
}
