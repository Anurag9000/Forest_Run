package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimalEncounterFlavorTest {

    @Test
    fun `hedgehog warning lines escalate with repeated hits`() {
        assertEquals("Watch your step. Low thorns.", AnimalEncounterFlavor.hedgehogWarning(0))
        assertEquals("Low thorns. Hop late.", AnimalEncounterFlavor.hedgehogWarning(1))
        assertEquals("Still the low thorns. Hop late.", AnimalEncounterFlavor.hedgehogWarning(3))
    }

    @Test
    fun `hedgehog pass and hit lines reflect prior history`() {
        assertEquals("Careful...", AnimalEncounterFlavor.hedgehogPass(0, false))
        assertEquals("Past the thorns.", AnimalEncounterFlavor.hedgehogPass(1, false))
        assertTrue(AnimalEncounterFlavor.hedgehogPass(2, false).contains("read"))
        assertEquals("Cleared the low thorns.", AnimalEncounterFlavor.hedgehogPass(0, true))
        assertEquals("Clean over the low thorns.", AnimalEncounterFlavor.hedgehogPass(1, true))

        assertEquals("Caught low.", AnimalEncounterFlavor.hedgehogHit(0))
        assertEquals("Caught low.", AnimalEncounterFlavor.hedgehogHit(1))
        assertEquals("Low thorns again.", AnimalEncounterFlavor.hedgehogHit(4))
    }
}
