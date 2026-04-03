package com.yourname.forest_run.engine

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
}
