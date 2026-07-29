package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeWorkloadTelemetryTest {
    @Test
    fun `publish tracks current and peak independently for every workload`() {
        RuntimeWorkloadTelemetry.reset()

        RuntimeWorkloadTelemetry.publishEntities(3)
        RuntimeWorkloadTelemetry.publishEntities(1)
        RuntimeWorkloadTelemetry.publishSeedOrbs(2)
        RuntimeWorkloadTelemetry.publishSeedOrbs(0)
        RuntimeWorkloadTelemetry.publishParticles(120)
        RuntimeWorkloadTelemetry.publishParticles(40)
        RuntimeWorkloadTelemetry.publishDialogueBubbles(4)
        RuntimeWorkloadTelemetry.publishDialogueBubbles(2)
        RuntimeWorkloadTelemetry.publishFlavorTexts(5)
        RuntimeWorkloadTelemetry.publishFlavorTexts(1)

        assertEquals(
            RuntimeWorkloadSnapshot(
                currentEntities = 1,
                peakEntities = 3,
                currentSeedOrbs = 0,
                peakSeedOrbs = 2,
                currentParticles = 40,
                peakParticles = 120,
                currentDialogueBubbles = 2,
                peakDialogueBubbles = 4,
                currentFlavorTexts = 1,
                peakFlavorTexts = 5
            ),
            RuntimeWorkloadTelemetry.snapshot()
        )
    }

    @Test
    fun `negative counts clamp to zero without corrupting peaks`() {
        RuntimeWorkloadTelemetry.reset()
        RuntimeWorkloadTelemetry.publishEntities(7)
        RuntimeWorkloadTelemetry.publishEntities(-50)
        RuntimeWorkloadTelemetry.publishParticles(-1)

        val snapshot = RuntimeWorkloadTelemetry.snapshot()
        assertEquals(0, snapshot.currentEntities)
        assertEquals(7, snapshot.peakEntities)
        assertEquals(0, snapshot.currentParticles)
        assertEquals(0, snapshot.peakParticles)
    }

    @Test
    fun `reset clears current and peak pressure`() {
        RuntimeWorkloadTelemetry.publishSeedOrbs(3)
        RuntimeWorkloadTelemetry.publishDialogueBubbles(2)
        RuntimeWorkloadTelemetry.reset()

        assertEquals(RuntimeWorkloadSnapshot.EMPTY, RuntimeWorkloadTelemetry.snapshot())
    }
}
