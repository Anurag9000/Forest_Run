package com.yourname.forest_run.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BirdEncounterFlavorTest {

    @Test
    fun `duck pass line rewards staying low`() {
        assertEquals("Good duck.", BirdEncounterFlavor.duckPass(stayedLow = true))
        assertEquals("Low pass.", BirdEncounterFlavor.duckPass(stayedLow = false))
    }

    @Test
    fun `tit warning and pass scale with flock size`() {
        assertEquals("Follow the wave.", BirdEncounterFlavor.titWarning(4))
        assertEquals("Catch the rhythm.", BirdEncounterFlavor.titWarning(5))
        assertEquals("In sync.", BirdEncounterFlavor.titPass(4))
        assertEquals("Held the rhythm.", BirdEncounterFlavor.titPass(5))
    }

    @Test
    fun `chickadee lines react to wider flutter spread`() {
        assertTrue(BirdEncounterFlavor.chickadeeWarning(130f).contains("fluttery"))
        assertTrue(BirdEncounterFlavor.chickadeePass(130f).contains("flutter"))
        assertEquals("Watch the jitter.", BirdEncounterFlavor.chickadeeWarning(80f))
        assertEquals("Soft wings.", BirdEncounterFlavor.chickadeePass(80f))
    }
}
