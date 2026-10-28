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

        assertTrue(payload.startsWith("forest-run-encounter-scenario-v2\n"))
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
            "cf23943b2ce595b05920917a3940616480ff25181c6433156787cdf22497d253",
            EncounterScenarioFingerprint.sha256(EncounterScenario.CACTUS_READ)
        )
        assertEquals(
            "7c48df64352a1df6b6b0a28ebd121ad1123a43c9ee8709f25831825acfe8be34",
            EncounterScenarioFingerprint.traceContractSha256(EncounterScenario.CACTUS_READ)
        )
    }

    @Test
    fun `trace contract canonical payload covers exact authored actions`() {
        val payload = EncounterScenarioFingerprint
            .traceContractCanonicalBytes(EncounterScenario.CACTUS_READ)
            .toString(Charsets.UTF_8)

        assertTrue(payload.startsWith("forest-run-scenario-trace-contract-v1\n"))
        assertTrue(payload.contains("2400000\n"))
        assertTrue(payload.contains("2700000\n"))
        assertTrue(payload.contains("4300000\n"))
        assertTrue(payload.contains("4600000\n"))
        assertTrue(payload.contains("HOLD_JUMP_START"))
        assertTrue(payload.contains("HOLD_JUMP_END"))
    }

    @Test
    fun `wolf scenario fingerprint binds its isolated mercy starting state`() {
        val wolf = EncounterScenario.WOLF_CHARGE
        val payload = EncounterScenarioFingerprint.canonicalBytes(wolf)
            .toString(Charsets.UTF_8)

        assertEquals(8, wolf.startingMercyHearts)
        assertTrue(payload.contains("8\n"))
        assertEquals(
            "a2ad41316226b88568b04e2def0edab77f6a8b4960b8808d0509346a6a10fe31",
            EncounterScenarioFingerprint.sha256(wolf)
        )
    }

    @Test
    fun `catalogue fingerprint covers every scenario in enum order`() {
        val digest = EncounterScenarioFingerprint.catalogueSha256()
        assertTrue(digest.matches(Regex("^[0-9a-f]{64}$")))
        assertEquals(digest, EncounterScenarioFingerprint.catalogueSha256())
    }
}
