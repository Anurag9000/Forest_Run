package com.yourname.forest_run.engine

object TreeEncounterFlavor {

    fun willowPass(encounters: Int, repeatHits: Int): String = when {
        repeatHits >= 3 -> "You found the hush again."
        repeatHits >= 1 -> "Through the curtain."
        encounters >= 4 -> "The willow left a lane."
        else -> "Duck through the hush."
    }

    fun jacarandaPass(encounters: Int, repeatHits: Int): String = when {
        repeatHits >= 2 -> "Past the whole canopy."
        encounters >= 4 -> "The petals opened a path."
        else -> "Petal hush."
    }

    fun bambooPass(encounters: Int, repeatHits: Int): String = when {
        repeatHits >= 2 -> "Held the narrow line."
        encounters >= 4 -> "Every stalk left just enough room."
        else -> "Thread the grove."
    }

    fun cherryPass(encounters: Int, repeatHits: Int): String = when {
        repeatHits >= 2 -> "You stayed with the gust."
        encounters >= 4 -> "The storm broke around you."
        else -> "Blossom gust."
    }
}
