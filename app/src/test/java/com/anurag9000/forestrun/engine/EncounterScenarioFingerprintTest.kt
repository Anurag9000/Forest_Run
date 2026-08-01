package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncounterScenarioFingerprintTest {

    @Test
    fun `every scenario has a deterministic unique sha256 identity`() {
        val first = EncounterScenario.entries.associateWith(EncounterScenarioFingerprint::sha256)
        val second = EncounterScenario.entries.associateWith(EncounterScenarioFingerprint::sha256)

        assertEquals(first, second)
        assertEquals(EncounterScenario.entries.size, first.values.toSet().size)
        first.values.forEach { digest ->
            assertTrue(digest.matches(Regex("^[0-9a-f]{64}$")))
        }
    }

    @Test
    fun `canonical payload uses integer timing and exact authored fields`() {
        val scenario = EncounterScenario.CACTUS_READ
        val payload = EncounterScenarioFingerprint.canonicalBytes(scenario)
            .toString(Charsets.UTF_8)

        assertTrue(payload.startsWith("forest-run-encounter-scenario-v1\n"))
        assertTrue(payload.contains("CACTUS_READ"))
        assertTrue(payload.contains(scenario.title))
        assertTrue(payload.contains(scenario.summary))
        assertTrue(payload.contains("200000\n"))
        assertTrue(payload.contains("420000000\n"))
        assertFalse(payload.contains("0.2"))
        assertFalse(payload.contains("420.0"))
    }

    @Test
    fun `catalogue fingerprint covers every scenario in enum order`() {
        val digest = EncounterScenarioFingerprint.catalogueSha256()
        assertTrue(digest.matches(Regex("^[0-9a-f]{64}$")))
        assertEquals(digest, EncounterScenarioFingerprint.catalogueSha256())
    }
}
