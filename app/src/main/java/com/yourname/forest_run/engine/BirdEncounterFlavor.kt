package com.yourname.forest_run.engine

object BirdEncounterFlavor {

    fun duckWarning(): String = "Down!"

    fun duckHit(repeatHits: Int): String = when {
        repeatHits >= 2 -> "Same low lane."
        repeatHits >= 1 -> "Still too high."
        else -> "Too high."
    }

    fun duckPass(stayedLow: Boolean): String =
        if (stayedLow) "Good duck." else "Low pass."

    fun titWarning(groupSize: Int): String =
        if (groupSize >= 5) "Catch the rhythm." else "Follow the wave."

    fun titHit(repeatHits: Int): String = when {
        repeatHits >= 2 -> "Lost the rhythm again."
        repeatHits >= 1 -> "Lost the rhythm."
        else -> "Missed the wave."
    }

    fun titPass(groupSize: Int): String =
        if (groupSize >= 5) "Held the rhythm." else "In sync."

    fun chickadeeWarning(verticalSpread: Float): String =
        if (verticalSpread >= 120f) "Too fluttery." else "Watch the jitter."

    fun chickadeeHit(repeatHits: Int): String = when {
        repeatHits >= 2 -> "Same flutter rush."
        repeatHits >= 1 -> "Too fluttery again."
        else -> "Too fluttery."
    }

    fun chickadeePass(verticalSpread: Float): String =
        if (verticalSpread >= 120f) "Read the flutter." else "Soft wings."
}
