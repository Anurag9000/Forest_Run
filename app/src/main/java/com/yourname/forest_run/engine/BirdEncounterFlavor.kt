package com.yourname.forest_run.engine

object BirdEncounterFlavor {

    fun duckWarning(): String = "Down!"

    fun duckPass(stayedLow: Boolean): String =
        if (stayedLow) "Good duck." else "Low pass."

    fun titWarning(groupSize: Int): String =
        if (groupSize >= 5) "Catch the rhythm." else "Follow the wave."

    fun titPass(groupSize: Int): String =
        if (groupSize >= 5) "Held the rhythm." else "In sync."

    fun chickadeeWarning(verticalSpread: Float): String =
        if (verticalSpread >= 120f) "Too fluttery." else "Watch the jitter."

    fun chickadeePass(verticalSpread: Float): String =
        if (verticalSpread >= 120f) "Read the flutter." else "Soft wings."
}
