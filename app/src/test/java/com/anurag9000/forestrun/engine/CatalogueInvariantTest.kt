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
