package com.anurag9000.forestrun.engine

/**
 * Allocation-free monotonic gate for expensive derived-state evaluation.
 *
 * Callers own synchronization. A forced acquisition also advances the gate so
 * the following frame cannot immediately duplicate the forced evaluation.
 */
internal class EvaluationThrottle(private val intervalNs: Long) {
    init {
        require(intervalNs >= 0L) { "intervalNs must be non-negative" }
    }

    private var lastEvaluationNs: Long = Long.MIN_VALUE

    fun tryAcquire(nowNs: Long, force: Boolean = false): Boolean {
        val clockReset = lastEvaluationNs != Long.MIN_VALUE && nowNs < lastEvaluationNs
        val intervalElapsed = lastEvaluationNs == Long.MIN_VALUE ||
            nowNs - lastEvaluationNs >= intervalNs
        if (!force && !clockReset && !intervalElapsed) return false

        lastEvaluationNs = nowNs
        return true
    }

    fun reset() {
        lastEvaluationNs = Long.MIN_VALUE
    }
}
