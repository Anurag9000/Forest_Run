package com.anurag9000.forestrun.engine

/** Overflow- and rollback-safe arithmetic shared by persistent progression owners. */
internal object SafeProgressionArithmetic {
    const val DEFAULT_COUNTER_MAX = Int.MAX_VALUE / 16

    fun saturatingIncrement(
        value: Int,
        maximum: Int = DEFAULT_COUNTER_MAX
    ): Int {
        require(maximum >= 0) { "Counter maximum must be non-negative." }
        val normalized = value.coerceIn(0, maximum)
        return if (normalized >= maximum) maximum else normalized + 1
    }

    fun elapsedAtLeast(
        nowMs: Long,
        earlierMs: Long,
        thresholdMs: Long
    ): Boolean {
        if (nowMs < 0L || earlierMs < 0L || thresholdMs < 0L || nowMs < earlierMs) {
            return false
        }
        return nowMs - earlierMs >= thresholdMs
    }

    fun elapsedOrZero(nowMs: Long, earlierMs: Long): Long {
        if (nowMs < 0L || earlierMs < 0L || nowMs < earlierMs) return 0L
        return nowMs - earlierMs
    }
}
