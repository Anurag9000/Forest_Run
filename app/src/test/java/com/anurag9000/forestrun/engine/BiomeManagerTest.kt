package com.anurag9000.forestrun.engine

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BiomeManagerTest {

    @Test
    fun `at cycles through biome sequence every segment`() {
        assertEquals(Biome.MEADOW, Biome.at(0f))
        assertEquals(Biome.ORCHARD, Biome.at(GameConstants.BIOME_LENGTH_METRES))
        assertEquals(Biome.ANCIENT_GROVE, Biome.at(GameConstants.BIOME_LENGTH_METRES * 2))
        assertEquals(Biome.DUSK_CANYON, Biome.at(GameConstants.BIOME_LENGTH_METRES * 3))
        assertEquals(Biome.NIGHT_FOREST, Biome.at(GameConstants.BIOME_LENGTH_METRES * 4))
        assertEquals(Biome.MEADOW, Biome.at(GameConstants.BIOME_LENGTH_METRES * 5))
    }

    @Test
    fun `invalid and negative distances resolve to opening biome`() {
        assertEquals(Biome.MEADOW, Biome.at(-1f))
        assertEquals(Biome.MEADOW, Biome.at(Float.NaN))
        assertEquals(Biome.MEADOW, Biome.at(Float.POSITIVE_INFINITY))
        assertEquals(Biome.MEADOW, Biome.at(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun `very large finite distance remains inside cycle`() {
        assertTrue(Biome.at(Float.MAX_VALUE) in Biome.entries)
    }

    @Test
    fun `update enables crossfade near biome boundary`() {
        val manager = BiomeManager()

        manager.update(GameConstants.BIOME_LENGTH_METRES * 0.5f)
        assertEquals(0f, manager.crossfadeAlpha, 0.0001f)

        manager.update(GameConstants.BIOME_LENGTH_METRES * 0.9f)
        assertTrue(manager.crossfadeAlpha in 0f..1f)
        assertTrue(manager.crossfadeAlpha > 0f)
        assertEquals(
            Biome.MEADOW.preferredPool.plus(Biome.ORCHARD.preferredPool).distinct(),
            manager.entityPool
        )
    }

    @Test
    fun `palette and ambient handoff are continuous across every cyclic boundary`() {
        val manager = BiomeManager()

        for (segment in 1..Biome.entries.size) {
            val boundary = GameConstants.BIOME_LENGTH_METRES * segment
            val justBefore = Math.nextDown(boundary)
            val expectedNext = Biome.at(boundary)

            manager.update(justBefore)
            assertTrue("crossfade must be almost complete before boundary $segment", manager.crossfadeAlpha > 0.999f)
            assertColourNear(expectedNext.skyTopColour, manager.currentSkyTop)
            assertColourNear(expectedNext.skyBottomColour, manager.currentSkyBottom)
            assertColourNear(expectedNext.groundColour, manager.currentGround)
            assertColourNear(expectedNext.midFoliageColour, manager.currentFoliage)
            val expectedAmbient = ((1f - expectedNext.ambientLightFactor) * 200f).toInt()
            assertTrue(kotlin.math.abs(manager.ambientAlpha - expectedAmbient) <= 1)

            manager.update(boundary)
            assertEquals(expectedNext, manager.currentBiome)
            assertEquals(0f, manager.crossfadeAlpha, 0f)
            assertEquals(expectedNext.skyTopColour, manager.currentSkyTop)
            assertEquals(expectedNext.skyBottomColour, manager.currentSkyBottom)
            assertEquals(expectedNext.groundColour, manager.currentGround)
            assertEquals(expectedNext.midFoliageColour, manager.currentFoliage)
            assertEquals(expectedAmbient, manager.ambientAlpha)
        }
    }

    @Test
    fun `invalid update restores finite opening palette and no crossfade`() {
        val manager = BiomeManager()
        manager.update(GameConstants.BIOME_LENGTH_METRES * 3.9f)
        assertTrue(manager.crossfadeAlpha > 0f)

        manager.update(Float.NaN)

        assertEquals(Biome.MEADOW, manager.currentBiome)
        assertEquals(0f, manager.crossfadeAlpha, 0f)
        assertEquals(Biome.MEADOW.skyTopColour, manager.currentSkyTop)
        assertEquals(Biome.MEADOW.skyBottomColour, manager.currentSkyBottom)
        assertEquals(Biome.MEADOW.groundColour, manager.currentGround)
        assertEquals(Biome.MEADOW.midFoliageColour, manager.currentFoliage)
    }

    @Test
    fun `debug override remains stable until explicitly cleared`() {
        val manager = BiomeManager()
        manager.forceDebugBiome(Biome.NIGHT_FOREST)

        manager.update(Float.NaN)
        assertEquals(Biome.NIGHT_FOREST, manager.currentBiome)
        assertEquals(0f, manager.crossfadeAlpha, 0f)

        manager.forceDebugBiome(null)
        manager.update(0f)
        assertEquals(Biome.MEADOW, manager.currentBiome)
    }

    private fun assertColourNear(expected: Int, actual: Int) {
        assertTrue(kotlin.math.abs(Color.alpha(expected) - Color.alpha(actual)) <= 1)
        assertTrue(kotlin.math.abs(Color.red(expected) - Color.red(actual)) <= 1)
        assertTrue(kotlin.math.abs(Color.green(expected) - Color.green(actual)) <= 1)
        assertTrue(kotlin.math.abs(Color.blue(expected) - Color.blue(actual)) <= 1)
    }
}
