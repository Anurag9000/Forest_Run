package com.yourname.forest_run.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class TreeEncounterFlavorTest {

    @Test
    fun `willow and jacaranda reflect familiarity`() {
        assertEquals("Duck through the hush.", TreeEncounterFlavor.willowPass(encounters = 1, repeatHits = 0))
        assertEquals("The petals opened a path.", TreeEncounterFlavor.jacarandaPass(encounters = 4, repeatHits = 0))
    }

    @Test
    fun `bamboo and cherry react to repeat hits`() {
        assertEquals("Held the narrow line.", TreeEncounterFlavor.bambooPass(encounters = 2, repeatHits = 2))
        assertEquals("You stayed with the gust.", TreeEncounterFlavor.cherryPass(encounters = 3, repeatHits = 2))
    }
}
