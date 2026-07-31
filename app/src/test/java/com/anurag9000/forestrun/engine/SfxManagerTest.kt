package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SfxManagerTest {

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
