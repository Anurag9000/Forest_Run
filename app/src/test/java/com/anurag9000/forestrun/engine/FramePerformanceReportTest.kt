package com.anurag9000.forestrun.engine

import kotlin.test.assertFailsWith
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FramePerformanceReportTest {
    @Test
    fun `report emits deterministic escaped JSON without non finite values`() {
        val report = FramePerformanceReport(
            scenario = "Bloom \"stress\"\npass",
            durationMs = 12_345L,
            manufacturer = "Example\\Maker",
            model = "Model\tOne",
            apiLevel = 35,
            refreshRateHz = 120f,
            snapshot = validSnapshot()
        )

        val json = report.toJson()

        assertTrue(json.startsWith("{\n"))
        assertTrue(json.endsWith("}\n"))
        assertTrue(json.contains("\"scenario\": \"Bloom \\\"stress\\\"\\npass\""))
        assertTrue(json.contains("\"manufacturer\": \"Example\\\\Maker\""))
        assertTrue(json.contains("\"model\": \"Model\\tOne\""))
        assertTrue(json.contains("\"p99ProcessingNs\": 14000000"))
        assertTrue(json.contains("\"slowFrameRatio\": 0.016666666666666666"))
        assertFalse(json.contains("NaN"))
        assertFalse(json.contains("Infinity"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank scenario is rejected`() {
        FramePerformanceReport(
            scenario = " ",
            durationMs = 0L,
            manufacturer = "",
            model = "",
            apiLevel = 0,
            refreshRateHz = 60f,
            snapshot = FramePerformanceMonitor(windowSize = 1).snapshot()
        )
    }

    @Test
    fun `contradictory frame evidence is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            report(snapshot = validSnapshot().copy(slowFrames = 721L))
        }
        assertFailsWith<IllegalArgumentException> {
            report(snapshot = validSnapshot().copy(p95ProcessingNs = 4_000_000L))
        }
        assertFailsWith<IllegalArgumentException> {
            report(snapshot = validSnapshot().copy(maxHeapBytes = 1L))
        }
    }

    @Test
    fun `workload current counts cannot exceed peaks`() {
        assertFailsWith<IllegalArgumentException> {
            report(
                workload = RuntimeWorkloadSnapshot.EMPTY.copy(
                    currentParticles = 5,
                    peakParticles = 4
                )
            )
        }
    }

    @Test
    fun `ghost completions failures and latest values require matching maxima`() {
        assertFailsWith<IllegalArgumentException> {
            report(
                ghostIo = GhostIoTelemetrySnapshot.EMPTY.copy(
                    writesStarted = 1L,
                    writesCompleted = 2L
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            report(
                ghostIo = GhostIoTelemetrySnapshot.EMPTY.copy(
                    writesStarted = 2L,
                    writesCompleted = 1L,
                    writesFailed = 2L
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            report(
                ghostIo = GhostIoTelemetrySnapshot.EMPTY.copy(
                    latestWriteDurationNs = 10L,
                    maximumWriteDurationNs = 9L
                )
            )
        }
    }

    private fun report(
        snapshot: FramePerformanceSnapshot = validSnapshot(),
        workload: RuntimeWorkloadSnapshot = RuntimeWorkloadSnapshot.EMPTY,
        ghostIo: GhostIoTelemetrySnapshot = GhostIoTelemetrySnapshot.EMPTY
    ): FramePerformanceReport = FramePerformanceReport(
        scenario = "test",
        durationMs = 1_000L,
        manufacturer = "Example",
        model = "Device",
        apiLevel = 35,
        refreshRateHz = 60f,
        snapshot = snapshot,
        workload = workload,
        ghostIo = ghostIo
    )

    private fun validSnapshot(): FramePerformanceSnapshot = FramePerformanceSnapshot(
        sampledFrames = 600,
        totalFrames = 720L,
        slowFrames = 12L,
        frameBudgetNs = 16_666_666L,
        meanUpdateNs = 2_000_000L,
        meanRenderNs = 4_000_000L,
        meanProcessingNs = 6_000_000L,
        p50ProcessingNs = 5_000_000L,
        p95ProcessingNs = 9_000_000L,
        p99ProcessingNs = 14_000_000L,
        maximumProcessingNs = 18_000_000L,
        usedHeapBytes = 80_000_000L,
        maxHeapBytes = 256_000_000L
    )
}
