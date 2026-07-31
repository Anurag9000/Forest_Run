package com.anurag9000.forestrun.engine

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
            snapshot = FramePerformanceSnapshot(
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
}
