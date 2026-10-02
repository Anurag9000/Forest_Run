package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SfxManagerTest {


    @Test
    fun `transient stream ledger is bounded ignores invalid ids and drains exactly once`() {
        val ledger = SoundStreamLedger(capacity = 3)
        ledger.record(0)
        ledger.record(-4)
        ledger.record(11)
        ledger.record(12)
        ledger.record(13)
        ledger.record(14)

        assertEquals(3, ledger.sizeForTests())
        assertEquals(listOf(12, 13, 14), ledger.drain())
        assertEquals(0, ledger.sizeForTests())
        assertTrue(ledger.drain().isEmpty())
    }

    @Test
    fun `stream ledger rejects nonpositive capacity`() {
        var rejected = false
        try {
            SoundStreamLedger(capacity = 0)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun `bloom ready profile is bright and anticipatory`() {
        val profile = buildBloomSfxProfile(SfxManager.BloomSfxEvent.READY, conversionsInBurst = 0)

        assertTrue(profile.volume in 0.6f..0.8f)
        assertTrue(profile.rate > 1f)
    }

    @Test
    fun `bloom convert profile scales upward with burst size`() {
        val small = buildBloomSfxProfile(SfxManager.BloomSfxEvent.CONVERT, conversionsInBurst = 1)
        val large = buildBloomSfxProfile(SfxManager.BloomSfxEvent.CONVERT, conversionsInBurst = 5)

        assertTrue(large.volume > small.volume)
        assertTrue(large.rate > small.rate)
    }

    @Test
    fun `bloom fade profile lands softer than convert profile`() {
        val convert = buildBloomSfxProfile(SfxManager.BloomSfxEvent.CONVERT, conversionsInBurst = 3)
        val fade = buildBloomSfxProfile(SfxManager.BloomSfxEvent.FADE, conversionsInBurst = 3)

        assertTrue(fade.volume < convert.volume)
        assertTrue(fade.rate < convert.rate)
    }

    @Test
    fun `ready optional sample is preferred over fallback`() {
        assertEquals(
            17,
            chooseReadySample(
                primaryId = 17,
                fallbackId = 29,
                primaryReady = true,
                fallbackReady = true
            )
        )
    }

    @Test
    fun `failed optional sample falls back to ready mandatory sample`() {
        assertEquals(
            29,
            chooseReadySample(
                primaryId = 17,
                fallbackId = 29,
                primaryReady = false,
                fallbackReady = true
            )
        )
    }

    @Test
    fun `nonpositive or stale sample identifiers never become playable`() {
        assertEquals(
            29,
            chooseReadySample(
                primaryId = 0,
                fallbackId = 29,
                primaryReady = true,
                fallbackReady = true
            )
        )
        assertEquals(
            0,
            chooseReadySample(
                primaryId = 17,
                fallbackId = -1,
                primaryReady = false,
                fallbackReady = true
            )
        )
    }

    @Test
    fun `no ready sample resolves to silent no-op identifier`() {
        assertEquals(
            0,
            chooseReadySample(
                primaryId = 17,
                fallbackId = 29,
                primaryReady = false,
                fallbackReady = false
            )
        )
    }
}
