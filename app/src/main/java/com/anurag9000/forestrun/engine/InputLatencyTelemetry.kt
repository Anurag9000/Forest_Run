package com.anurag9000.forestrun.engine

import kotlin.math.ceil

enum class InputGestureKind {
    JUMP,
    DUCK
}

data class InputLatencySnapshot(
    val sampledActions: Int,
    val droppedActions: Long,
    val p50TouchToDecisionNs: Long,
    val p95TouchToDecisionNs: Long,
    val p99TouchToDecisionNs: Long,
    val p50DecisionToResponseNs: Long,
    val p95DecisionToResponseNs: Long,
    val p99DecisionToResponseNs: Long,
    val p50ResponseToRenderNs: Long,
    val p95ResponseToRenderNs: Long,
    val p99ResponseToRenderNs: Long,
    val p50TouchToRenderNs: Long,
    val p95TouchToRenderNs: Long,
    val p99TouchToRenderNs: Long
)

/**
 * Low-overhead app/render latency telemetry for gameplay touch actions.
 *
 * This measures timestamps *inside the app process*: touch receipt, gesture
 * classification, gameplay response, and completion of the next rendered frame.
 * It is deliberately not labelled touch-to-photon; display scanout/panel latency
 * requires external physical instrumentation.
 */
class InputLatencyTelemetry(private val capacity: Int = DEFAULT_CAPACITY) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val touchToDecisionNs = LongArray(capacity)
    private val decisionToResponseNs = LongArray(capacity)
    private val responseToRenderNs = LongArray(capacity)
    private val touchToRenderNs = LongArray(capacity)
    private val gestureKinds = ByteArray(capacity)

    private var writeIndex = 0
    private var size = 0
    private var pendingTouchNs = UNSET
    private var pendingDecisionNs = UNSET
    @Volatile private var pendingResponseNs = UNSET
    private var pendingGesture = NO_GESTURE
    private var dropped = 0L

    @Synchronized
    fun recordTouchReceived(nowNs: Long) {
        if (nowNs < 0L) {
            dropped++
            clearPending()
            return
        }
        if (pendingTouchNs != UNSET) dropped++
        pendingTouchNs = nowNs
        pendingDecisionNs = UNSET
        pendingResponseNs = UNSET
        pendingGesture = NO_GESTURE
    }

    @Synchronized
    fun recordGestureDecision(kind: InputGestureKind, nowNs: Long) {
        if (pendingTouchNs == UNSET || pendingDecisionNs != UNSET || nowNs < pendingTouchNs) {
            dropped++
            clearPending()
            return
        }
        pendingDecisionNs = nowNs
        pendingGesture = kind.ordinal.toByte()
    }

    @Synchronized
    fun recordGameplayResponse(nowNs: Long) {
        if (
            pendingTouchNs == UNSET ||
            pendingDecisionNs == UNSET ||
            pendingResponseNs != UNSET ||
            nowNs < pendingDecisionNs
        ) {
            dropped++
            clearPending()
            return
        }
        pendingResponseNs = nowNs
    }

    fun recordFrameRendered(nowNs: Long) {
        // Almost every rendered frame has no pending input response. Avoid
        // contending with the UI thread in that overwhelmingly common case.
        if (pendingResponseNs == UNSET) return
        synchronized(this) {
            if (pendingTouchNs == UNSET || pendingResponseNs == UNSET) return
            if (nowNs < pendingResponseNs) {
                dropped++
                clearPending()
                return
            }
            val index = writeIndex
            touchToDecisionNs[index] = pendingDecisionNs - pendingTouchNs
            decisionToResponseNs[index] = pendingResponseNs - pendingDecisionNs
            responseToRenderNs[index] = nowNs - pendingResponseNs
            touchToRenderNs[index] = nowNs - pendingTouchNs
            gestureKinds[index] = pendingGesture
            writeIndex = (writeIndex + 1) % capacity
            if (size < capacity) size++
            clearPending()
        }
    }

    @Synchronized
    fun cancelPending() {
        clearPending()
    }

    @Synchronized
    fun reset() {
        touchToDecisionNs.fill(0L)
        decisionToResponseNs.fill(0L)
        responseToRenderNs.fill(0L)
        touchToRenderNs.fill(0L)
        gestureKinds.fill(NO_GESTURE)
        writeIndex = 0
        size = 0
        dropped = 0L
        clearPending()
    }

    @Synchronized
    fun snapshot(): InputLatencySnapshot = InputLatencySnapshot(
        sampledActions = size,
        droppedActions = dropped,
        p50TouchToDecisionNs = percentile(touchToDecisionNs, 0.50),
        p95TouchToDecisionNs = percentile(touchToDecisionNs, 0.95),
        p99TouchToDecisionNs = percentile(touchToDecisionNs, 0.99),
        p50DecisionToResponseNs = percentile(decisionToResponseNs, 0.50),
        p95DecisionToResponseNs = percentile(decisionToResponseNs, 0.95),
        p99DecisionToResponseNs = percentile(decisionToResponseNs, 0.99),
        p50ResponseToRenderNs = percentile(responseToRenderNs, 0.50),
        p95ResponseToRenderNs = percentile(responseToRenderNs, 0.95),
        p99ResponseToRenderNs = percentile(responseToRenderNs, 0.99),
        p50TouchToRenderNs = percentile(touchToRenderNs, 0.50),
        p95TouchToRenderNs = percentile(touchToRenderNs, 0.95),
        p99TouchToRenderNs = percentile(touchToRenderNs, 0.99)
    )

    private fun percentile(source: LongArray, fraction: Double): Long {
        if (size == 0) return 0L
        val copy = LongArray(size)
        val oldest = if (size == capacity) writeIndex else 0
        for (index in 0 until size) {
            copy[index] = source[(oldest + index) % capacity]
        }
        copy.sort()
        val rank = ceil(size * fraction).toInt().coerceIn(1, size) - 1
        return copy[rank]
    }

    private fun clearPending() {
        pendingTouchNs = UNSET
        pendingDecisionNs = UNSET
        pendingResponseNs = UNSET
        pendingGesture = NO_GESTURE
    }

    companion object {
        const val DEFAULT_CAPACITY = 256
        private const val UNSET = -1L
        private const val NO_GESTURE: Byte = -1
    }
}

/** Process-local owner shared by the UI input path, render thread, and profiler. */
object InputLatencyTelemetryRegistry {
    private val telemetry = InputLatencyTelemetry()

    fun recordTouchReceived(nowNs: Long) = telemetry.recordTouchReceived(nowNs)
    fun recordGestureDecision(kind: InputGestureKind, nowNs: Long) =
        telemetry.recordGestureDecision(kind, nowNs)
    fun recordGameplayResponse(nowNs: Long) = telemetry.recordGameplayResponse(nowNs)
    fun recordFrameRendered(nowNs: Long) = telemetry.recordFrameRendered(nowNs)
    fun cancelPending() = telemetry.cancelPending()
    fun reset() = telemetry.reset()
    fun snapshot(): InputLatencySnapshot = telemetry.snapshot()
}
