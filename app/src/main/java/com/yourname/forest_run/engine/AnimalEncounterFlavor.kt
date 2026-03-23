package com.yourname.forest_run.engine

object AnimalEncounterFlavor {

    fun hedgehogWarning(repeatHits: Int): String = when {
        repeatHits >= 3 -> "Still the low thorns. Hop late."
        repeatHits >= 1 -> "Low thorns. Hop late."
        else -> "Watch your step. Low thorns."
    }

    fun hedgehogHit(repeatHits: Int): String = when {
        repeatHits >= 3 -> "Low thorns again."
        repeatHits >= 1 -> "Caught low."
        else -> "Caught low."
    }

    fun hedgehogPass(repeatHits: Int, clearedRead: Boolean): String = when {
        clearedRead && repeatHits >= 2 -> "You read the low thorns this time."
        clearedRead && repeatHits >= 1 -> "Clean over the low thorns."
        clearedRead -> "Cleared the low thorns."
        repeatHits >= 2 -> "You read them this time."
        repeatHits >= 1 -> "Past the thorns."
        else -> "Careful..."
    }
}
