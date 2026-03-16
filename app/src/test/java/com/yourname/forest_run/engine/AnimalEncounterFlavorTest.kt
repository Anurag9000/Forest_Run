package com.yourname.forest_run.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimalEncounterFlavorTest {

    @Test
    fun `hedgehog warning lines escalate with repeated hits`() {
        assertEquals("Watch your step.", AnimalEncounterFlavor.hedgehogWarning(0))
        assertEquals("Low thorns.", AnimalEncounterFlavor.hedgehogWarning(1))
        assertEquals("Still the low thorns.", AnimalEncounterFlavor.hedgehogWarning(3))
    }

    @Test
    fun `hedgehog pass and hit lines reflect prior history`() {
        assertEquals("Careful...", AnimalEncounterFlavor.hedgehogPass(0))
        assertEquals("Past the thorns.", AnimalEncounterFlavor.hedgehogPass(1))
        assertTrue(AnimalEncounterFlavor.hedgehogPass(2).contains("read"))

        assertEquals("Oof!", AnimalEncounterFlavor.hedgehogHit(0))
        assertEquals("Caught the thorns.", AnimalEncounterFlavor.hedgehogHit(1))
        assertEquals("Thorns again.", AnimalEncounterFlavor.hedgehogHit(4))
    }
}
