package com.anurag9000.forestrun.engine

import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LeitmotifManagerTest {

    @Test
    fun `bloom profile is the loudest state`() {
        val bloom = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.BLOOM,
            GameConstants.BASE_SCROLL_SPEED * 1.4f
        )
        val menu = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.MENU,
            GameConstants.BASE_SCROLL_SPEED
        )
        val rest = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.REST,
            GameConstants.BASE_SCROLL_SPEED
        )

        assertTrue(bloom.targetVolume > menu.targetVolume)
        assertTrue(bloom.targetVolume > rest.targetVolume)
        assertTrue(bloom.tempo >= 1f)
        assertTrue(bloom.motifSignature.shimmer > menu.motifSignature.shimmer)
    }

    @Test
    fun `late run profile is fuller and faster than early run`() {
        val early = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.PLAYING_1,
            GameConstants.BASE_SCROLL_SPEED
        )
        val late = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.PLAYING_3,
            GameConstants.BASE_SCROLL_SPEED * 1.5f
        )

        assertTrue(late.targetVolume > early.targetVolume)
        assertTrue(late.tempo > early.tempo)
        assertTrue(late.motifSignature.leadPresence > early.motifSignature.leadPresence)
        assertTrue(late.motifSignature.cadenceLift > early.motifSignature.cadenceLift)
    }

    @Test
    fun `playback profile remains clamped at extreme scroll values`() {
        val profile = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.PLAYING_3,
            GameConstants.BASE_SCROLL_SPEED * 4f
        )

        assertTrue(profile.tempo in 1f..1.8f)
        assertTrue(profile.targetVolume in 0f..1f)
        assertTrue(profile.motifSignature.pulsePresence in 0f..1f)
    }

    @Test
    fun `bloom profile swells early and settles late while rewarding conversions`() {
        val earlyBloom = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.BLOOM,
            GameConstants.BASE_SCROLL_SPEED * 1.2f,
            BloomMusicSignature(
                secondsRemaining = GameConstants.BLOOM_DURATION_S,
                conversions = 0
            )
        )
        val convertedBloom = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.BLOOM,
            GameConstants.BASE_SCROLL_SPEED * 1.2f,
            BloomMusicSignature(
                secondsRemaining = GameConstants.BLOOM_DURATION_S * 0.65f,
                conversions = 3
            )
        )
        val lateBloom = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.BLOOM,
            GameConstants.BASE_SCROLL_SPEED * 1.2f,
            BloomMusicSignature(
                secondsRemaining = 0.4f,
                conversions = 0
            )
        )

        assertTrue(earlyBloom.tempo > lateBloom.tempo)
        assertTrue(convertedBloom.targetVolume > lateBloom.targetVolume)
        assertTrue(convertedBloom.tempo >= lateBloom.tempo)
        assertTrue(convertedBloom.motifSignature.shimmer > lateBloom.motifSignature.shimmer)
        assertTrue(convertedBloom.motifSignature.cadenceLift > lateBloom.motifSignature.cadenceLift)
    }

    @Test
    fun `menu rest and run states carry distinct motif labels`() {
        val menu = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.MENU,
            GameConstants.BASE_SCROLL_SPEED
        )
        val rest = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.REST,
            GameConstants.BASE_SCROLL_SPEED
        )
        val run = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.PLAYING_2,
            GameConstants.BASE_SCROLL_SPEED * 1.2f
        )

        assertNotEquals(menu.motifSignature.motifLabel, rest.motifSignature.motifLabel)
        assertNotEquals(menu.motifSignature.motifLabel, run.motifSignature.motifLabel)
        assertNotEquals(rest.motifSignature.motifLabel, run.motifSignature.motifLabel)
    }
}
