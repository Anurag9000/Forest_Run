package com.anurag9000.forestrun.engine

/** Owns near-miss, mercy-heart, and kindness-chain state for a single run. */
class MercySystem {

    companion object {
        const val MAX_HEARTS = 10

        private fun saturatingAdd(value: Int, amount: Int): Int {
            if (value >= Int.MAX_VALUE || amount <= 0) return value.coerceAtLeast(0)
            return if (value > Int.MAX_VALUE - amount) Int.MAX_VALUE else value + amount
        }
    }

    var mercyHearts: Int = 0
        private set
    var nearMisses: Int = 0
        private set
    var kindnessChain: Int = 0
        private set

    fun recordMercyMiss() {
        nearMisses = saturatingAdd(nearMisses, 1)
        kindnessChain = saturatingAdd(kindnessChain, 1)
        if (mercyHearts < MAX_HEARTS) mercyHearts++
    }

    fun recordCleanPass() {
        kindnessChain = saturatingAdd(kindnessChain, 1)
    }

    fun recordSpare() {
        kindnessChain = saturatingAdd(kindnessChain, 2)
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
