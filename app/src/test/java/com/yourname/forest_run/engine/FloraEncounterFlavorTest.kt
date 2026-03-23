package com.yourname.forest_run.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FloraEncounterFlavorTest {

    @Test
    fun `lily flavor shifts from lure to resolved read`() {
        assertEquals("Glow high, trap low.", FloraEncounterFlavor.lilyPass(encounters = 1, repeatHits = 0))
        assertEquals("You read the low trap early.", FloraEncounterFlavor.lilyPass(encounters = 5, repeatHits = 0))
        assertEquals("You left the low lure behind.", FloraEncounterFlavor.lilyPass(encounters = 5, repeatHits = 3))
    }

    @Test
    fun `hyacinth and orchid emphasize timing windows`() {
        assertEquals("Three-beat bloom.", FloraEncounterFlavor.hyacinthPass(encounters = 1, repeatHits = 0))
        assertEquals("Three beats, one jump.", FloraEncounterFlavor.hyacinthPass(encounters = 4, repeatHits = 0))
        assertEquals("You kept the third beat.", FloraEncounterFlavor.hyacinthPass(encounters = 4, repeatHits = 2))
        assertEquals("Find the center thread.", FloraEncounterFlavor.orchidPass(encounters = 1, repeatHits = 0))
        assertEquals("You held the center thread.", FloraEncounterFlavor.orchidPass(encounters = 4, repeatHits = 0))
        assertEquals("Low vine, high branch.", FloraEncounterFlavor.orchidPass(encounters = 2, repeatHits = 2))
    }

    @Test
    fun `eucalyptus and cactus react to repeat hits`() {
        assertEquals("Lean, then whip.", FloraEncounterFlavor.eucalyptusPass(repeatHits = 0))
        assertEquals("Past the lean line.", FloraEncounterFlavor.eucalyptusPass(repeatHits = 1))
        assertEquals("You read the whip early.", FloraEncounterFlavor.eucalyptusPass(repeatHits = 3))
        assertEquals("Sharp read.", FloraEncounterFlavor.cactusPass(encounters = 1, repeatHits = 0, cleanPasses = 0))
        assertEquals("The needles flowered.", FloraEncounterFlavor.cactusPass(encounters = 4, repeatHits = 0, cleanPasses = 3))
        assertEquals("Needle bloom kept.", FloraEncounterFlavor.cactusPass(encounters = 6, repeatHits = 0, cleanPasses = 5))
        assertEquals("Not the thorns again.", FloraEncounterFlavor.cactusPass(encounters = 6, repeatHits = 3, cleanPasses = 0))
    }
}
