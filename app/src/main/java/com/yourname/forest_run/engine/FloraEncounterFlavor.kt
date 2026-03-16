package com.yourname.forest_run.engine

object FloraEncounterFlavor {

    fun lilyPass(encounters: Int, repeatHits: Int): String = when {
        repeatHits >= 3 -> "You ignored the lure."
        repeatHits >= 1 -> "Past the lure."
        encounters >= 4 -> "The glow didn't fool you."
        else -> "Moonlit lure."
    }

    fun hyacinthPass(encounters: Int, repeatHits: Int): String = when {
        repeatHits >= 2 -> "You held the whole rhythm."
        encounters >= 4 -> "Three beats, one pass."
        else -> "Brush the petals."
    }

    fun eucalyptusPass(repeatHits: Int): String = when {
        repeatHits >= 3 -> "You read the gust early."
        repeatHits >= 1 -> "Leaves missed you."
        else -> "Leaves whip past."
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
