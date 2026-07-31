package com.anurag9000.forestrun.engine

import android.graphics.Color
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.utils.MathUtils

/** Drives biome transitions, blended palettes, and biome-specific encounter pools. */
class BiomeManager {

    companion object {
        private const val CROSSFADE_START_FRACTION = 0.80f
    }

    private val biomeLength = GameConstants.BIOME_LENGTH_METRES

    var currentBiome: Biome = Biome.MEADOW
        private set

    private var nextBiome: Biome = Biome.next(currentBiome)
    private var forcedDebugBiome: Biome? = null

    var crossfadeAlpha: Float = 0f
        private set

    var currentSkyTop: Int = currentBiome.skyTopColour
        private set
    var currentSkyBottom: Int = currentBiome.skyBottomColour
        private set
    var currentGround: Int = currentBiome.groundColour
        private set
    var currentFoliage: Int = currentBiome.midFoliageColour
        private set

    val ambientAlpha: Int
        get() {
            val base = ((1f - currentBiome.ambientLightFactor) * 200f).toInt()
            val next = ((1f - nextBiome.ambientLightFactor) * 200f).toInt()
            return lerpInt(base, next, crossfadeAlpha)
        }

    val entityPool: List<EntityType>
        get() = if (crossfadeAlpha > 0f) {
            (currentBiome.preferredPool + nextBiome.preferredPool).distinct()
        } else {
            currentBiome.preferredPool
        }

    fun update(distanceMetres: Float) {
        forcedDebugBiome?.let { forced ->
            applyBiome(forced, crossfade = 0f)
            return
        }

        val safeDistance = distanceMetres.takeIf { it.isFinite() && it >= 0f } ?: 0f
        val progressInBiome = if (biomeLength.isFinite() && biomeLength > 0f) {
            ((safeDistance % biomeLength) / biomeLength).coerceIn(0f, 1f)
        } else {
            0f
        }

        val resolvedBiome = Biome.at(safeDistance)
        if (resolvedBiome != currentBiome) {
            currentBiome = resolvedBiome
            nextBiome = Biome.next(resolvedBiome)
        }

        crossfadeAlpha = if (progressInBiome >= CROSSFADE_START_FRACTION) {
            MathUtils.normalise(progressInBiome, CROSSFADE_START_FRACTION, 1f)
                .takeIf { it.isFinite() }
                ?.coerceIn(0f, 1f)
                ?: 0f
        } else {
            0f
        }

        updateBlendedColours()
    }

    fun forceDebugBiome(biome: Biome?) {
        forcedDebugBiome = biome
        if (biome != null) applyBiome(biome, crossfade = 0f)
    }

    private fun applyBiome(biome: Biome, crossfade: Float) {
        currentBiome = biome
        nextBiome = Biome.next(biome)
        crossfadeAlpha = crossfade.coerceIn(0f, 1f)
        updateBlendedColours()
    }

    private fun updateBlendedColours() {
        currentSkyTop = blendColour(currentBiome.skyTopColour, nextBiome.skyTopColour, crossfadeAlpha)
        currentSkyBottom = blendColour(currentBiome.skyBottomColour, nextBiome.skyBottomColour, crossfadeAlpha)
        currentGround = blendColour(currentBiome.groundColour, nextBiome.groundColour, crossfadeAlpha)
        currentFoliage = blendColour(currentBiome.midFoliageColour, nextBiome.midFoliageColour, crossfadeAlpha)
    }

    private fun blendColour(from: Int, to: Int, t: Float): Int {
        val safeT = t.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val a = lerpInt(Color.alpha(from), Color.alpha(to), safeT)
        val r = lerpInt(Color.red(from), Color.red(to), safeT)
        val g = lerpInt(Color.green(from), Color.green(to), safeT)
        val b = lerpInt(Color.blue(from), Color.blue(to), safeT)
        return Color.argb(a, r, g, b)
    }

    private fun lerpInt(a: Int, b: Int, t: Float): Int =
        (a + (b - a) * t).toInt().coerceIn(0, 255)
}
