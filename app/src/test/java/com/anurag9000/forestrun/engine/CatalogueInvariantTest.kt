package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.CostumeStyle
import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueInvariantTest {
    @Test
    fun `entity catalogue remains complete and uniquely named`() {
        assertEquals(19, EntityType.entries.size)
        assertEquals(
            EntityType.entries.size,
            EntityType.entries.map { it.name }.toSet().size
        )
    }

    @Test
    fun `tracked relationship catalogue remains exactly the six authored bonds`() {
        assertEquals(
            setOf(
                EntityType.CAT,
                EntityType.FOX,
                EntityType.WOLF,
                EntityType.OWL,
                EntityType.EAGLE,
                EntityType.DOG
            ),
            EntityType.entries.filter(RelationshipArcSystem::isTracked).toSet()
        )
    }

    @Test
    fun `biome cycle remains complete readable and covers every entity`() {
        assertEquals(
            listOf(
                Biome.MEADOW,
                Biome.ORCHARD,
                Biome.ANCIENT_GROVE,
                Biome.DUSK_CANYON,
                Biome.NIGHT_FOREST
            ),
            Biome.entries
        )
        assertEquals(
            Biome.entries.size,
            Biome.entries.map { it.displayName }.toSet().size
        )
        assertTrue(Biome.entries.all { it.displayName.isNotBlank() })
        assertTrue(Biome.entries.all { it.ambientLightFactor in 0f..1f })
        assertTrue(Biome.entries.all { it.preferredPool.isNotEmpty() })
        assertTrue(Biome.entries.all { it.preferredPool.size == it.preferredPool.distinct().size })
        assertEquals(
            EntityType.entries.toSet(),
            Biome.entries.flatMap { it.preferredPool }.toSet()
        )
        Biome.entries.forEachIndexed { index, biome ->
            assertEquals(Biome.entries[(index + 1) % Biome.entries.size], Biome.next(biome))
        }
    }

    @Test
    fun `classic costume remains first and authored labels remain unique`() {
        assertEquals(0, CostumeStyle.NONE.ordinal)
        assertEquals("Classic", CostumeStyle.NONE.displayName)
        assertEquals(
            CostumeStyle.entries.size,
            CostumeStyle.entries.map { it.name }.toSet().size
        )
        assertEquals(
            CostumeStyle.entries.size,
            CostumeStyle.entries.map { it.displayName }.toSet().size
        )
        assertTrue(CostumeStyle.entries.all { it.displayName.isNotBlank() })
        assertTrue(CostumeStyle.entries.all { it.unlockLabel.isNotBlank() })
    }

    @Test
    fun `pacifist route order preserves ordinal severity comparisons`() {
        assertEquals(
            listOf(
                PacifistRouteTier.NONE,
                PacifistRouteTier.KIND,
                PacifistRouteTier.MERCIFUL,
                PacifistRouteTier.PEACEFUL
            ),
            PacifistRouteTier.entries
        )
        assertEquals("", PacifistRouteTier.NONE.restLine)
        assertEquals("", PacifistRouteTier.NONE.gardenLine)
        assertTrue(
            PacifistRouteTier.entries
                .filterNot { it == PacifistRouteTier.NONE }
                .all { it.displayName.isNotBlank() && it.restLine.isNotBlank() && it.gardenLine.isNotBlank() }
        )
    }

    @Test
    fun `Garden costs cover every plant and increase monotonically`() {
        assertEquals(9, GardenEconomy.catalogueSize)
        val costs = (0 until GardenEconomy.catalogueSize)
            .map { index -> requireNotNull(GardenEconomy.seedCostForIndex(index)) }

        assertTrue(costs.all { it > 0 })
        assertTrue(costs.zipWithNext().all { (left, right) -> right > left })
        assertNull(GardenEconomy.seedCostForIndex(-1))
        assertNull(GardenEconomy.seedCostForIndex(GardenEconomy.catalogueSize))
    }
}
