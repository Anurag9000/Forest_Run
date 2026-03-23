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

    fun titCountIn(groupSize: Int): String =
        if (groupSize >= 5) "One, two, through." else "One, two."

    fun titThroughPrompt(groupSize: Int): String =
        if (groupSize >= 5) "Hold the beat." else "Through the wave."

    fun titHit(repeatHits: Int): String = when {
        repeatHits >= 2 -> "Lost the rhythm again."
        repeatHits >= 1 -> "Missed the count."
        else -> "Missed the beat."
    }

    fun titPass(groupSize: Int, keptBeat: Boolean): String = when {
        keptBeat && groupSize >= 5 -> "Held the whole beat."
        keptBeat -> "Kept the beat."
        groupSize >= 5 -> "Held the rhythm."
        else -> "In sync."
    }

    fun chickadeeWarning(verticalSpread: Float): String =
        if (verticalSpread >= 120f) "Follow the little gap." else "Watch the tiny jitter."

    fun chickadeePocketPrompt(): String = "Soft gap."

    fun chickadeeHit(repeatHits: Int): String = when {
        repeatHits >= 2 -> "Same flutter rush."
        repeatHits >= 1 -> "Lost the little gap."
        else -> "Too fluttery."
    }

    fun chickadeePass(verticalSpread: Float, readPocket: Boolean): String = when {
        readPocket -> "Tiny wings trusted you."
        verticalSpread >= 120f -> "Read the flutter."
        else -> "Soft wings."
    }
}
