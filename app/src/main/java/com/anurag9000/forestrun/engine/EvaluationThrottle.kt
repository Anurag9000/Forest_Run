package com.anurag9000.forestrun.engine

/**
 * Allocation-free monotonic gate for expensive derived-state evaluation.
 * Callers own synchronization.
 */
internal class EvaluationThrottle(private val intervalNs: Long) {
    init {
        require(intervalNs >= 0L) { "intervalNs must be non-negative" }
    }

    private var initialized = false
    private var lastEvaluationNs = 0L

    fun tryAcquire(nowNs: Long, force: Boolean = false): Boolean {
        if (!initialized) {
            initialized = true
            lastEvaluationNs = nowNs
            return true
        }

        val clockReset = nowNs < lastEvaluationNs
        val intervalElapsed = elapsedAtLeast(
            nowNs = nowNs,
            previousNs = lastEvaluationNs,
            intervalNs = intervalNs
        )
        if (!force && !clockReset && !intervalElapsed) return false

        lastEvaluationNs = nowNs
        return true
    }

    fun reset() {
        initialized = false
        lastEvaluationNs = 0L
    }

    /** Standard monotonic subtraction remains wrap-safe for intervals below 2^63 ns. */
    private fun elapsedAtLeast(nowNs: Long, previousNs: Long, intervalNs: Long): Boolean =
        nowNs - previousNs >= intervalNs
}
