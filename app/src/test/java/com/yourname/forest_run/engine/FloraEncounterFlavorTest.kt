package com.yourname.forest_run.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FloraEncounterFlavorTest {

    @Test
    fun `lily flavor shifts from lure to resolved read`() {
        assertEquals("Moonlit lure.", FloraEncounterFlavor.lilyPass(encounters = 1, repeatHits = 0))
        assertEquals("You ignored the lure.", FloraEncounterFlavor.lilyPass(encounters = 5, repeatHits = 3))
    }

    @Test
    fun `hyacinth and orchid emphasize timing windows`() {
        assertEquals("Three beats, one pass.", FloraEncounterFlavor.hyacinthPass(encounters = 4, repeatHits = 0))
        assertEquals("Low, then high.", FloraEncounterFlavor.orchidPass(encounters = 2, repeatHits = 2))
    }

    @Test
    fun `eucalyptus and cactus react to repeat hits`() {
        assertEquals("You read the gust early.", FloraEncounterFlavor.eucalyptusPass(repeatHits = 3))
        assertEquals("Not the thorns again.", FloraEncounterFlavor.cactusPass(encounters = 6, repeatHits = 3))
    }
}
