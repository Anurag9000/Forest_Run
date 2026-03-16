package com.yourname.forest_run.engine

enum class PacifistRouteTier(
    val displayName: String,
    val restLine: String,
    val gardenLine: String
) {
    NONE("Unmarked", "", ""),
    KIND(
        "Kind",
        "A little mercy stayed with the run.",
        "The path still sounds calmer after the mercy you left behind."
    ),
    MERCIFUL(
        "Merciful",
        "The run held together through restraint instead of panic.",
        "The garden feels quieter when mercy kept reaching the end of the path."
    ),
    PEACEFUL(
        "Peaceful",
        "The forest came down in peace with you.",
        "The whole garden keeps the hush of a run that crossed it peacefully."
    )
}
