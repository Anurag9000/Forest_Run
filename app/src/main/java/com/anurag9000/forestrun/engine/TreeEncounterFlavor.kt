package com.anurag9000.forestrun.engine

object TreeEncounterFlavor {

    fun willowPass(encounters: Int, repeatHits: Int): String = when {
        repeatHits >= 3 -> "You found the lane again."
        repeatHits >= 1 -> "Stayed under the curtain."
        encounters >= 4 -> "The willow kept a lane."
        else -> "Duck the willow lane."
    }

    fun jacarandaPass(encounters: Int, repeatHits: Int): String = when {
        repeatHits >= 2 -> "Past the whole bloom."
        encounters >= 4 -> "The petals kept a lane."
        else -> "Under the petal veil."
    }

    fun bambooPass(encounters: Int, repeatHits: Int): String = when {
        repeatHits >= 2 -> "Held the seam."
        encounters >= 4 -> "The seam stayed open."
        else -> "Find the bamboo seam."
    }

    fun cherryPass(encounters: Int, repeatHits: Int): String = when {
        repeatHits >= 2 -> "You held the pressure line."
        encounters >= 4 -> "The storm broke wide."
        else -> "Read the gust band."
    }
}
