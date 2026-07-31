package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DifficultyScalerTest {

    @Test
    fun `spawn distance clamps between early and late bounds`() {
        assertEquals(
            GameConstants.SPAWN_GAP_MAX_PX,
            DifficultyScaler.getSpawnGapPx(0f),
            0.0001f
        )
        assertEquals(
            GameConstants.SPAWN_GAP_MIN_PX,
            DifficultyScaler.getSpawnGapPx(5_000f),
            0.0001f
        )
    }

    @Test
    fun `distance tiered spawn pools unlock harder entities over time`() {
        val early = DifficultyScaler.getSpawnPool(100f, null)
        val mid = DifficultyScaler.getSpawnPool(800f, null)
        val late = DifficultyScaler.getSpawnPool(2_000f, null)

        assertTrue(EntityType.WOLF !in early)
        assertTrue(EntityType.WOLF in mid)
        assertEquals(EntityType.entries.toSet(), late.toSet())
    }

    @Test
    fun `invalid distance uses opening gap and encounter pool`() {
        val openingPool = DifficultyScaler.getSpawnPool(0f, null)
        listOf(
            -1f,
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        ).forEach { invalid ->
            assertEquals(openingPool, DifficultyScaler.getSpawnPool(invalid, null))
            assertEquals(
                GameConstants.SPAWN_GAP_MAX_PX,
                DifficultyScaler.getSpawnGapPx(invalid),
                0.0001f
            )
        }
    }

    @Test
    fun `explicit biome manager remains authoritative`() {
        val manager = BiomeManager().apply { forceDebugBiome(Biome.NIGHT_FOREST) }

        assertEquals(
            Biome.NIGHT_FOREST.preferredPool,
            DifficultyScaler.getSpawnPool(Float.NaN, manager)
        )
    }
}
