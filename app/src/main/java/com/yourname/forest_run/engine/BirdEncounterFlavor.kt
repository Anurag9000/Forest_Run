package com.yourname.forest_run.engine

object BirdEncounterFlavor {

    fun duckCall(): String = "Quack low."

    fun duckAnswerPrompt(): String = "Stay under."

    fun duckHit(repeatHits: Int): String = when {
        repeatHits >= 2 -> "Same quack lane."
        repeatHits >= 1 -> "Missed the answer again."
        else -> "Missed the low answer."
    }

    fun duckPass(answeredQuack: Boolean): String =
        if (answeredQuack) "Answered the quack." else "Stayed under."

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
