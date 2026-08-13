package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class InputLatencyTelemetryTest {
    @Test
    fun `complete action records all four latency segments`() {
        val telemetry = InputLatencyTelemetry(capacity = 4)
        telemetry.recordTouchReceived(1_000L)
        telemetry.recordGestureDecision(InputGestureKind.JUMP, 1_100L)
        telemetry.recordGameplayResponse(1_125L)
        telemetry.recordFrameRendered(1_500L)

        val snapshot = telemetry.snapshot()
        assertEquals(1, snapshot.sampledActions)
        assertEquals(0L, snapshot.droppedActions)
        assertEquals(100L, snapshot.p50TouchToDecisionNs)
        assertEquals(25L, snapshot.p50DecisionToResponseNs)
        assertEquals(375L, snapshot.p50ResponseToRenderNs)
        assertEquals(500L, snapshot.p50TouchToRenderNs)
    }

    @Test
    fun `percentiles use the bounded retained sample window`() {
        val telemetry = InputLatencyTelemetry(capacity = 4)
        for (index in 1L..6L) {
            val start = index * 10_000L
            telemetry.recordTouchReceived(start)
            telemetry.recordGestureDecision(InputGestureKind.JUMP, start + index * 10L)
            telemetry.recordGameplayResponse(start + index * 10L + 2L)
            telemetry.recordFrameRendered(start + index * 100L)
        }

        val snapshot = telemetry.snapshot()
        assertEquals(4, snapshot.sampledActions)
        // Capacity retains actions 3,4,5,6 -> decision latencies 30,40,50,60.
        assertEquals(40L, snapshot.p50TouchToDecisionNs)
        assertEquals(60L, snapshot.p95TouchToDecisionNs)
        assertEquals(60L, snapshot.p99TouchToDecisionNs)
        assertEquals(600L, snapshot.p99TouchToRenderNs)
    }

    @Test
    fun `a new touch drops and replaces an unfinished action`() {
        val telemetry = InputLatencyTelemetry()
        telemetry.recordTouchReceived(100L)
        telemetry.recordGestureDecision(InputGestureKind.JUMP, 110L)
        telemetry.recordTouchReceived(200L)
        telemetry.recordGestureDecision(InputGestureKind.DUCK, 210L)
        telemetry.recordGameplayResponse(220L)
        telemetry.recordFrameRendered(250L)

        val snapshot = telemetry.snapshot()
        assertEquals(1, snapshot.sampledActions)
        assertEquals(1L, snapshot.droppedActions)
        assertEquals(50L, snapshot.p50TouchToRenderNs)
    }

    @Test
    fun `out of order timestamps fail closed`() {
        val telemetry = InputLatencyTelemetry()
        telemetry.recordTouchReceived(100L)
        telemetry.recordGestureDecision(InputGestureKind.JUMP, 99L)
        telemetry.recordGameplayResponse(110L)
        telemetry.recordFrameRendered(120L)

        val snapshot = telemetry.snapshot()
        assertEquals(0, snapshot.sampledActions)
        assertEquals(2L, snapshot.droppedActions)
    }

    @Test
    fun `render before gameplay response is ignored rather than completing partial sample`() {
        val telemetry = InputLatencyTelemetry()
        telemetry.recordTouchReceived(100L)
        telemetry.recordGestureDecision(InputGestureKind.DUCK, 110L)
        telemetry.recordFrameRendered(120L)
        assertEquals(0, telemetry.snapshot().sampledActions)

        telemetry.recordGameplayResponse(125L)
        telemetry.recordFrameRendered(140L)
        assertEquals(1, telemetry.snapshot().sampledActions)
        assertEquals(40L, telemetry.snapshot().p50TouchToRenderNs)
    }

    @Test
    fun `cancel removes partial action without inventing dropped latency`() {
        val telemetry = InputLatencyTelemetry()
        telemetry.recordTouchReceived(100L)
        telemetry.recordGestureDecision(InputGestureKind.JUMP, 110L)
        telemetry.cancelPending()
        telemetry.recordFrameRendered(150L)

        val snapshot = telemetry.snapshot()
        assertEquals(0, snapshot.sampledActions)
        assertEquals(0L, snapshot.droppedActions)
    }

    @Test
    fun `negative clock input is rejected and next valid action can recover`() {
        val telemetry = InputLatencyTelemetry()
        telemetry.recordTouchReceived(-1L)
        telemetry.recordTouchReceived(100L)
        telemetry.recordGestureDecision(InputGestureKind.JUMP, 110L)
        telemetry.recordGameplayResponse(120L)
        telemetry.recordFrameRendered(130L)

        val snapshot = telemetry.snapshot()
        assertEquals(1, snapshot.sampledActions)
        assertEquals(1L, snapshot.droppedActions)
        assertEquals(30L, snapshot.p50TouchToRenderNs)
    }

    @Test
    fun `reset clears samples dropped count and pending action`() {
        val telemetry = InputLatencyTelemetry()
        telemetry.recordTouchReceived(100L)
        telemetry.recordGestureDecision(InputGestureKind.JUMP, 110L)
        telemetry.recordGameplayResponse(120L)
        telemetry.recordFrameRendered(130L)
        telemetry.recordTouchReceived(200L)
        telemetry.reset()
        telemetry.recordFrameRendered(300L)

        val snapshot = telemetry.snapshot()
        assertEquals(0, snapshot.sampledActions)
        assertEquals(0L, snapshot.droppedActions)
        assertEquals(0L, snapshot.p99TouchToRenderNs)
    }
}
