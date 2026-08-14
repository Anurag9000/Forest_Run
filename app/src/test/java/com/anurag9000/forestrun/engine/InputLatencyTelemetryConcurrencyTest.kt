package com.anurag9000.forestrun.engine

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputLatencyTelemetryConcurrencyTest {
    @Test
    fun `ui writer render completer and snapshot reader publish coherent bounded telemetry`() {
        val capacity = 128
        val iterations = 2_048
        val telemetry = InputLatencyTelemetry(capacity)
        val renderTimestampNs = AtomicLong()
        val firstFailure = AtomicReference<Throwable?>(null)
        val barrier = CyclicBarrier(3)
        val snapshots = ArrayList<InputLatencySnapshot>(iterations)

        fun awaitPhase() {
            barrier.await(5, TimeUnit.SECONDS)
        }

        fun guarded(block: () -> Unit): Thread = Thread {
            try {
                block()
            } catch (failure: Throwable) {
                firstFailure.compareAndSet(null, failure)
                barrier.reset()
            }
        }

        val uiWriter = guarded {
            repeat(iterations) { index ->
                val touchNs = 1_000_000L + index * 100_000L
                val decisionNs = touchNs + 10_000L + (index % 11) * 100L
                val responseNs = decisionNs + 20_000L + (index % 7) * 100L
                val renderNs = responseNs + 30_000L + (index % 13) * 100L

                telemetry.recordTouchReceived(touchNs)
                telemetry.recordGestureDecision(
                    if (index % 2 == 0) InputGestureKind.JUMP else InputGestureKind.DUCK,
                    decisionNs
                )
                telemetry.recordGameplayResponse(responseNs)
                renderTimestampNs.set(renderNs)

                awaitPhase()
                awaitPhase()
            }
        }
        val renderCompleter = guarded {
            repeat(iterations) {
                awaitPhase()
                telemetry.recordFrameRendered(renderTimestampNs.get())
                awaitPhase()
            }
        }
        val snapshotReader = guarded {
            repeat(iterations) {
                awaitPhase()
                snapshots += telemetry.snapshot()
                awaitPhase()
            }
        }

        uiWriter.start()
        renderCompleter.start()
        snapshotReader.start()
        listOf(uiWriter, renderCompleter, snapshotReader).forEach { it.join(15_000) }

        assertFalse("UI writer did not terminate", uiWriter.isAlive)
        assertFalse("render completer did not terminate", renderCompleter.isAlive)
        assertFalse("snapshot reader did not terminate", snapshotReader.isAlive)
        assertNull("concurrent telemetry operation failed", firstFailure.get())
        assertEquals(iterations, snapshots.size)

        snapshots.forEach { snapshot ->
            assertTrue(snapshot.sampledActions in 0..capacity)
            assertEquals(0L, snapshot.droppedActions)
            assertOrdered(
                snapshot.p50TouchToDecisionNs,
                snapshot.p95TouchToDecisionNs,
                snapshot.p99TouchToDecisionNs
            )
            assertOrdered(
                snapshot.p50DecisionToResponseNs,
                snapshot.p95DecisionToResponseNs,
                snapshot.p99DecisionToResponseNs
            )
            assertOrdered(
                snapshot.p50ResponseToRenderNs,
                snapshot.p95ResponseToRenderNs,
                snapshot.p99ResponseToRenderNs
            )
            assertOrdered(
                snapshot.p50TouchToRenderNs,
                snapshot.p95TouchToRenderNs,
                snapshot.p99TouchToRenderNs
            )
        }

        val finalSnapshot = telemetry.snapshot()
        assertEquals(capacity, finalSnapshot.sampledActions)
        assertEquals(0L, finalSnapshot.droppedActions)
        assertTrue(finalSnapshot.p50TouchToRenderNs > 0L)
        assertTrue(finalSnapshot.p95TouchToRenderNs >= finalSnapshot.p50TouchToRenderNs)
        assertTrue(finalSnapshot.p99TouchToRenderNs >= finalSnapshot.p95TouchToRenderNs)
    }

    private fun assertOrdered(p50: Long, p95: Long, p99: Long) {
        assertTrue(p50 >= 0L)
        assertTrue(p95 >= p50)
        assertTrue(p99 >= p95)
    }
}
