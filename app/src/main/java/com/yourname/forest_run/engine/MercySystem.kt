package com.yourname.forest_run.engine

/**
 * Owns near-miss, mercy-heart, and kindness-chain state for a single run.
 */
class MercySystem {

    companion object {
        const val MAX_HEARTS = 10
    }

    var mercyHearts: Int = 0
        private set
    var nearMisses: Int = 0
        private set
    var kindnessChain: Int = 0
        private set

    fun recordMercyMiss() {
        nearMisses++
        kindnessChain++
        mercyHearts = (mercyHearts + 1).coerceAtMost(MAX_HEARTS)
    }

    fun recordCleanPass() {
        kindnessChain++
    }

    fun recordSpare() {
        kindnessChain += 2
    }

    fun recordHit() {
        kindnessChain = 0
    }

    fun reset() {
        mercyHearts = 0
        nearMisses = 0
        kindnessChain = 0
    }
}
