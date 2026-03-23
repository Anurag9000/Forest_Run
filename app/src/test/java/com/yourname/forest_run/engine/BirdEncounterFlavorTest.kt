package com.yourname.forest_run.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BirdEncounterFlavorTest {

    @Test
    fun `duck lines stage a call and reward answering it`() {
        assertEquals("Quack low.", BirdEncounterFlavor.duckCall())
        assertEquals("Stay under.", BirdEncounterFlavor.duckAnswerPrompt())
        assertEquals("Answered the quack.", BirdEncounterFlavor.duckPass(answeredQuack = true))
        assertEquals("Stayed under.", BirdEncounterFlavor.duckPass(answeredQuack = false))
        assertEquals("Missed the low answer.", BirdEncounterFlavor.duckHit(0))
        assertEquals("Same quack lane.", BirdEncounterFlavor.duckHit(2))
    }

    @Test
    fun `tit warning and pass scale with flock size`() {
        assertEquals("One, two.", BirdEncounterFlavor.titCountIn(4))
        assertEquals("One, two, through.", BirdEncounterFlavor.titCountIn(5))
        assertEquals("Through the wave.", BirdEncounterFlavor.titThroughPrompt(4))
        assertEquals("Hold the beat.", BirdEncounterFlavor.titThroughPrompt(5))
        assertEquals("In sync.", BirdEncounterFlavor.titPass(4, keptBeat = false))
        assertEquals("Held the whole beat.", BirdEncounterFlavor.titPass(5, keptBeat = true))
        assertEquals("Missed the count.", BirdEncounterFlavor.titHit(1))
    }

    @Test
    fun `chickadee lines react to wider flutter spread`() {
        assertTrue(BirdEncounterFlavor.chickadeeWarning(130f).contains("gap"))
        assertTrue(BirdEncounterFlavor.chickadeePass(130f, readPocket = false).contains("flutter"))
        assertEquals("Watch the tiny jitter.", BirdEncounterFlavor.chickadeeWarning(80f))
        assertEquals("Soft wings.", BirdEncounterFlavor.chickadeePass(80f, readPocket = false))
        assertEquals("Soft gap.", BirdEncounterFlavor.chickadeePocketPrompt())
        assertEquals("Tiny wings trusted you.", BirdEncounterFlavor.chickadeePass(80f, readPocket = true))
        assertEquals("Same flutter rush.", BirdEncounterFlavor.chickadeeHit(2))
    }
}
