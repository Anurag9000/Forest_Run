package com.yourname.forest_run.engine

object FloraEncounterFlavor {

    fun lilyPass(encounters: Int, repeatHits: Int): String = when {
        repeatHits >= 3 -> "You left the low lure behind."
        repeatHits >= 1 -> "Past the low glow."
        encounters >= 4 -> "You read the low trap early."
        else -> "Glow high, trap low."
    }

    fun hyacinthPass(encounters: Int, repeatHits: Int): String = when {
        repeatHits >= 2 -> "You kept the third beat."
        encounters >= 4 -> "Three beats, one jump."
        else -> "Three-beat bloom."
    }

    fun eucalyptusPass(repeatHits: Int): String = when {
        repeatHits >= 3 -> "You read the whip early."
        repeatHits >= 1 -> "Past the lean line."
        else -> "Lean, then whip."
    }

    fun orchidPass(encounters: Int, repeatHits: Int): String = when {
        repeatHits >= 2 -> "Low, then high."
        encounters >= 4 -> "Still found the window."
        else -> "Thread the bloom."
    }

    fun cactusPass(encounters: Int, repeatHits: Int): String = when {
        repeatHits >= 3 -> "Not the thorns again."
        repeatHits >= 1 -> "Read the needles."
        encounters >= 5 -> "The path stayed sharp."
        else -> "Sharp read."
    }
}
