package com.anurag9000.forestrun.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InputLatencyReportTest {
    private fun snapshot() = InputLatencySnapshot(
        sampledActions = 40,
        droppedActions = 0,
        p50TouchToDecisionNs = 12_000_000,
        p95TouchToDecisionNs = 70_000_000,
        p99TouchToDecisionNs = 80_000_000,
        p50DecisionToResponseNs = 100_000,
        p95DecisionToResponseNs = 400_000,
        p99DecisionToResponseNs = 800_000,
        p50ResponseToRenderNs = 7_000_000,
        p95ResponseToRenderNs = 16_000_000,
        p99ResponseToRenderNs = 22_000_000,
        p50TouchToRenderNs = 20_000_000,
        p95TouchToRenderNs = 86_000_000,
        p99TouchToRenderNs = 101_000_000
    )

    @Test
    fun `json is deterministic complete and explicitly not touch to photon`() {
        val report = InputLatencyReport(
            scenario = "INPUT_GESTURES",
            durationMs = 30_000,
            manufacturer = "Example",
            model = "Phone",
            apiLevel = 35,
            refreshRateHz = 120f,
            injectedActions = 40,
            snapshot = snapshot()
        )

        val first = report.toJson()
        assertEquals(first, report.toJson())
        val json = JSONObject(first)
        assertEquals(1, json.getInt("schemaVersion"))
        assertEquals("app_touch_to_posted_frame", json.getString("measurementKind"))
        assertEquals(40, json.getInt("sampledActions"))
        assertEquals(86_000_000L, json.getLong("p95TouchToRenderNs"))
    }

    @Test
    fun `report rejects impossible counts and percentile ordering`() {
        assertThrows(IllegalArgumentException::class.java) {
            InputLatencyReport(
                scenario = "INPUT_GESTURES",
                durationMs = 1,
                manufacturer = "Example",
                model = "Phone",
                apiLevel = 35,
                refreshRateHz = 60f,
                injectedActions = 39,
                snapshot = snapshot()
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            InputLatencyReport(
                scenario = "INPUT_GESTURES",
                durationMs = 1,
                manufacturer = "Example",
                model = "Phone",
                apiLevel = 35,
                refreshRateHz = 60f,
                injectedActions = 40,
                snapshot = snapshot().copy(
                    p95TouchToRenderNs = 10,
                    p99TouchToRenderNs = 5
                )
            )
        }
    }

    @Test
    fun `report rejects unusable device metadata`() {
        val invalidRates = listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)
        invalidRates.forEach { rate ->
            assertThrows(IllegalArgumentException::class.java) {
                InputLatencyReport(
                    scenario = "INPUT_GESTURES",
                    durationMs = 1,
                    manufacturer = "Example",
                    model = "Phone",
                    apiLevel = 35,
                    refreshRateHz = rate,
                    injectedActions = 40,
                    snapshot = snapshot()
                )
            }
        }
    }
}
