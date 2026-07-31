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
 * Publication allocates nothing and executes on the single game thread. A
 * lightweight sequence lock prevents an out-of-band snapshot from observing a
 * new current count before its matching peak update. Different workload
 * categories may still represent adjacent manager updates, which is acceptable
 * for pressure correlation rather than gameplay logic.
 */
object RuntimeWorkloadTelemetry {
    @Volatile private var publicationSequence = 0L
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

    fun publishEntities(count: Int) = publish {
        currentEntities = count.coerceAtLeast(0)
        if (currentEntities > peakEntities) peakEntities = currentEntities
    }

    fun publishSeedOrbs(count: Int) = publish {
        currentSeedOrbs = count.coerceAtLeast(0)
        if (currentSeedOrbs > peakSeedOrbs) peakSeedOrbs = currentSeedOrbs
    }

    fun publishParticles(count: Int) = publish {
        currentParticles = count.coerceAtLeast(0)
        if (currentParticles > peakParticles) peakParticles = currentParticles
    }

    fun publishDialogueBubbles(count: Int) = publish {
        currentDialogueBubbles = count.coerceAtLeast(0)
        if (currentDialogueBubbles > peakDialogueBubbles) {
            peakDialogueBubbles = currentDialogueBubbles
        }
    }

    fun publishFlavorTexts(count: Int) = publish {
        currentFlavorTexts = count.coerceAtLeast(0)
        if (currentFlavorTexts > peakFlavorTexts) peakFlavorTexts = currentFlavorTexts
    }

    /** Call only while the game-thread producer is stopped. */
    fun reset() = publish {
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

    fun snapshot(): RuntimeWorkloadSnapshot {
        repeat(MAX_SNAPSHOT_RETRIES) {
            val before = publicationSequence
            if (before and 1L != 0L) return@repeat

            val snapshot = readSnapshot()
            val after = publicationSequence
            if (before == after && after and 1L == 0L) return snapshot
        }

        // A continuously publishing producer may exhaust the small retry budget.
        // Preserve pair invariants in the best-effort fallback publication.
        return normalizePairs(readSnapshot())
    }

    private inline fun publish(update: () -> Unit) {
        val start = publicationSequence
        publicationSequence = start + 1L
        try {
            update()
        } finally {
            publicationSequence = start + 2L
        }
    }

    private fun readSnapshot(): RuntimeWorkloadSnapshot = RuntimeWorkloadSnapshot(
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

    private fun normalizePairs(snapshot: RuntimeWorkloadSnapshot): RuntimeWorkloadSnapshot =
        snapshot.copy(
            peakEntities = maxOf(snapshot.currentEntities, snapshot.peakEntities),
            peakSeedOrbs = maxOf(snapshot.currentSeedOrbs, snapshot.peakSeedOrbs),
            peakParticles = maxOf(snapshot.currentParticles, snapshot.peakParticles),
            peakDialogueBubbles = maxOf(
                snapshot.currentDialogueBubbles,
                snapshot.peakDialogueBubbles
            ),
            peakFlavorTexts = maxOf(snapshot.currentFlavorTexts, snapshot.peakFlavorTexts)
        )

    private const val MAX_SNAPSHOT_RETRIES = 3
}
