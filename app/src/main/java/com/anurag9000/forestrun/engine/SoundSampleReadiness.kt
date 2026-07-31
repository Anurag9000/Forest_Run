package com.anurag9000.forestrun.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * Generation-aware readiness for asynchronous SoundPool loading.
 *
 * SoundPool may deliver a load callback after release. Sample identifiers are
 * local to a pool and may be reused by its replacement, so a stale callback
 * must never make a new generation appear ready.
 */
internal class SoundSampleReadiness {
    internal enum class CompletionResult {
        READY,
        FAILED,
        STALE
    }

    private val readySamples = ConcurrentHashMap.newKeySet<Int>()
    private var activeGeneration = 0L

    @Synchronized
    fun beginGeneration(): Long {
        activeGeneration = nextGeneration(activeGeneration)
        readySamples.clear()
        return activeGeneration
    }

    @Synchronized
    fun complete(
        generation: Long,
        sampleId: Int,
        status: Int
    ): CompletionResult {
        if (generation != activeGeneration) return CompletionResult.STALE
        if (status == 0 && sampleId > 0) {
            readySamples.add(sampleId)
            return CompletionResult.READY
        }
        readySamples.remove(sampleId)
        return CompletionResult.FAILED
    }

    @Synchronized
    fun invalidate() {
        activeGeneration = nextGeneration(activeGeneration)
        readySamples.clear()
    }

    fun isReady(sampleId: Int): Boolean = sampleId > 0 && sampleId in readySamples

    internal fun generationForTests(): Long = synchronized(this) { activeGeneration }

    private fun nextGeneration(current: Long): Long =
        if (current == Long.MAX_VALUE) 1L else current + 1L
}
