package com.yourname.forest_run.entities

enum class CostumeStyle(
    val displayName: String,
    val unlockLabel: String
) {
    NONE("Classic", "Always available"),
    FLOWER_CROWN("Flower Crown", "Spare 3 cats or earn Cat Bond"),
    VINE_SCARF("Vine Scarf", "Spare 3 foxes or earn Fox Bond"),
    MOON_CAPE("Moon Cape", "Spare 2 wolves or earn Wolf Bond"),
    BELL_CHARM("Bell Charm", "Earn Dog Bond"),
    LANTERN_PIN("Lantern Pin", "Earn Owl Bond"),
    SKY_SASH("Sky Sash", "Earn Eagle Bond"),
    BLOOM_RIBBON("Bloom Ribbon", "Reach 1500 m or 120 seeds")
}
