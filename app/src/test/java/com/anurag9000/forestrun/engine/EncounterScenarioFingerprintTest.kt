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
        assertTrue(payload.contains("DUSK_CANYON"))
        assertTrue(payload.contains("200000\n"))
        assertTrue(payload.contains("420000000\n"))
        assertFalse(payload.contains("0.2"))
        assertFalse(payload.contains("420.0"))
    }

    @Test
    fun `cactus fingerprints match independent Python canonical constants`() {
        assertEquals(
            "3246dd15f7e694d387d06430537bf1805e8d57a53a9bcd1bdc5dd13e929b524c",
            EncounterScenarioFingerprint.sha256(EncounterScenario.CACTUS_READ)
        )
        assertEquals(
            "edb682a29079ceaebf9c3e56c2f24362ce3335a0c1432e3803305a5dc2b58430",
            EncounterScenarioFingerprint.traceContractSha256(EncounterScenario.CACTUS_READ)
        )
    }

    @Test
    fun `trace contract canonical payload covers exact authored actions`() {
        val payload = EncounterScenarioFingerprint
            .traceContractCanonicalBytes(EncounterScenario.CACTUS_READ)
            .toString(Charsets.UTF_8)

        assertTrue(payload.startsWith("forest-run-scenario-trace-contract-v1\n"))
        assertTrue(payload.contains("3180000\n"))
        assertTrue(payload.contains("3480000\n"))
        assertTrue(payload.contains("5060000\n"))
        assertTrue(payload.contains("5360000\n"))
        assertTrue(payload.contains("HOLD_JUMP_START"))
        assertTrue(payload.contains("HOLD_JUMP_END"))
    }

    @Test
    fun `catalogue fingerprint covers every scenario in enum order`() {
        val digest = EncounterScenarioFingerprint.catalogueSha256()
        assertTrue(digest.matches(Regex("^[0-9a-f]{64}$")))
        assertEquals(digest, EncounterScenarioFingerprint.catalogueSha256())
    }
}
