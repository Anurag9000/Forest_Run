
package com.yourname.forest_run.entities

/** Exactly one terminal result may be recorded for each spawned entity. */
enum class EncounterOutcome {
    PENDING,
    HIT,
    STUMBLE,
    MERCY,
    CLEAN_PASS,
    BLOOM_CONVERTED,
    DESPAWNED
}
