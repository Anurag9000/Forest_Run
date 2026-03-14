package com.yourname.forest_run.engine

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
    fun `update enables crossfade near biome boundary`() {
        val manager = BiomeManager()

        manager.update(GameConstants.BIOME_LENGTH_METRES * 0.5f)
        assertEquals(0f, manager.crossfadeAlpha, 0.0001f)

        manager.update(GameConstants.BIOME_LENGTH_METRES * 0.9f)
        assertTrue(manager.crossfadeAlpha > 0f)
        assertEquals(Biome.MEADOW.preferredPool.plus(Biome.ORCHARD.preferredPool).distinct(), manager.entityPool)
    }
}
