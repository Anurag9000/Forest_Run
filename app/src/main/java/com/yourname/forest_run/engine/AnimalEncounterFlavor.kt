package com.yourname.forest_run.engine

object AnimalEncounterFlavor {

    fun hedgehogWarning(repeatHits: Int): String = when {
        repeatHits >= 3 -> "Still the low thorns."
        repeatHits >= 1 -> "Low thorns."
        else -> "Watch your step."
    }

    fun hedgehogHit(repeatHits: Int): String = when {
        repeatHits >= 3 -> "Thorns again."
        repeatHits >= 1 -> "Caught the thorns."
        else -> "Oof!"
    }

    fun hedgehogPass(repeatHits: Int): String = when {
        repeatHits >= 2 -> "You read them this time."
        repeatHits >= 1 -> "Past the thorns."
        else -> "Careful..."
    }
}
