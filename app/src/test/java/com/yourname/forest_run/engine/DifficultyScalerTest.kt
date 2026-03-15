package com.yourname.forest_run.engine

import com.yourname.forest_run.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyScalerTest {

    @Test
    fun `spawn interval clamps between early and late bounds`() {
        assertEquals(2.4f, DifficultyScaler.getSpawnInterval(0f), 0.0001f)
        assertEquals(0.9f, DifficultyScaler.getSpawnInterval(5_000f), 0.0001f)
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
}
