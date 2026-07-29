package com.anurag9000.forestrun.engine

/** Best-effort runtime pressure observed alongside frame timing. */
data class RuntimeWorkloadSnapshot(
    val currentEntities: Int,
    val peakEntities: Int,
    val currentSeedOrbs: Int,
    val peakSeedOrbs: Int,
    val currentParticles: Int,
    val peakParticles: Int,
    val currentDialogueBubbles: Int,
    val peakDialogueBubbles: Int,
    val currentFlavorTexts: Int,
    val peakFlavorTexts: Int
) {
    companion object {
        val EMPTY = RuntimeWorkloadSnapshot(
            currentEntities = 0,
            peakEntities = 0,
            currentSeedOrbs = 0,
            peakSeedOrbs = 0,
            currentParticles = 0,
            peakParticles = 0,
            currentDialogueBubbles = 0,
            peakDialogueBubbles = 0,
            currentFlavorTexts = 0,
            peakFlavorTexts = 0
        )
    }
}

/**
 * Primitive workload counters published from existing manager update loops.
 *
 * Publication allocates nothing and executes on the game thread. Snapshots are
 * intended for out-of-band profiling and may observe adjacent manager updates,
 * which is acceptable for pressure correlation rather than game logic.
 */
object RuntimeWorkloadTelemetry {
    @Volatile private var currentEntities = 0
    @Volatile private var peakEntities = 0
    @Volatile private var currentSeedOrbs = 0
    @Volatile private var peakSeedOrbs = 0
    @Volatile private var currentParticles = 0
    @Volatile private var peakParticles = 0
    @Volatile private var currentDialogueBubbles = 0
    @Volatile private var peakDialogueBubbles = 0
    @Volatile private var currentFlavorTexts = 0
    @Volatile private var peakFlavorTexts = 0

    fun publishEntities(count: Int) {
        currentEntities = count.coerceAtLeast(0)
        if (currentEntities > peakEntities) peakEntities = currentEntities
    }

    fun publishSeedOrbs(count: Int) {
        currentSeedOrbs = count.coerceAtLeast(0)
        if (currentSeedOrbs > peakSeedOrbs) peakSeedOrbs = currentSeedOrbs
    }

    fun publishParticles(count: Int) {
        currentParticles = count.coerceAtLeast(0)
        if (currentParticles > peakParticles) peakParticles = currentParticles
    }

    fun publishDialogueBubbles(count: Int) {
        currentDialogueBubbles = count.coerceAtLeast(0)
        if (currentDialogueBubbles > peakDialogueBubbles) peakDialogueBubbles = currentDialogueBubbles
    }

    fun publishFlavorTexts(count: Int) {
        currentFlavorTexts = count.coerceAtLeast(0)
        if (currentFlavorTexts > peakFlavorTexts) peakFlavorTexts = currentFlavorTexts
    }

    @Synchronized
    fun reset() {
        currentEntities = 0
        peakEntities = 0
        currentSeedOrbs = 0
        peakSeedOrbs = 0
        currentParticles = 0
        peakParticles = 0
        currentDialogueBubbles = 0
        peakDialogueBubbles = 0
        currentFlavorTexts = 0
        peakFlavorTexts = 0
    }

    fun snapshot(): RuntimeWorkloadSnapshot = RuntimeWorkloadSnapshot(
        currentEntities = currentEntities,
        peakEntities = peakEntities,
        currentSeedOrbs = currentSeedOrbs,
        peakSeedOrbs = peakSeedOrbs,
        currentParticles = currentParticles,
        peakParticles = peakParticles,
        currentDialogueBubbles = currentDialogueBubbles,
        peakDialogueBubbles = peakDialogueBubbles,
        currentFlavorTexts = currentFlavorTexts,
        peakFlavorTexts = peakFlavorTexts
    )
}
