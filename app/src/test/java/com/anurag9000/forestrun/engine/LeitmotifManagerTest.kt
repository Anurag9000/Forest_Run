package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `non finite and non positive scroll speeds use the authored base profile`() {
        val expected = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.PLAYING_2,
            GameConstants.BASE_SCROLL_SPEED
        )

        listOf(
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            0f,
            -100f
        ).forEach { malformedSpeed ->
            assertEquals(
                expected,
                buildLeitmotifPlaybackProfile(
                    LeitmotifManager.MusicState.PLAYING_2,
                    malformedSpeed
                )
            )
        }
    }

    @Test
    fun `malformed bloom time and negative conversions fail closed`() {
        val expected = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.BLOOM,
            GameConstants.BASE_SCROLL_SPEED,
            BloomMusicSignature(secondsRemaining = 0f, conversions = 0)
        )

        val actual = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.BLOOM,
            Float.NaN,
            BloomMusicSignature(secondsRemaining = Float.NaN, conversions = Int.MIN_VALUE)
        )

        assertEquals(expected, actual)
        assertFiniteProfile(actual)
    }

    @Test
    fun `bloom conversion influence saturates at five`() {
        val capped = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.BLOOM,
            GameConstants.BASE_SCROLL_SPEED * 1.2f,
            BloomMusicSignature(
                secondsRemaining = GameConstants.BLOOM_DURATION_S,
                conversions = 5
            )
        )
        val extreme = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.BLOOM,
            GameConstants.BASE_SCROLL_SPEED * 1.2f,
            BloomMusicSignature(
                secondsRemaining = GameConstants.BLOOM_DURATION_S,
                conversions = Int.MAX_VALUE
            )
        )

        assertEquals(capped, extreme)
        assertFiniteProfile(extreme)
    }

    @Test
    fun `infinite bloom time settles to a finite zero time profile`() {
        val expected = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.BLOOM,
            GameConstants.BASE_SCROLL_SPEED,
            BloomMusicSignature(secondsRemaining = 0f, conversions = 3)
        )
        val actual = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.BLOOM,
            GameConstants.BASE_SCROLL_SPEED,
            BloomMusicSignature(secondsRemaining = Float.POSITIVE_INFINITY, conversions = 3)
        )

        assertEquals(expected, actual)
        assertFiniteProfile(actual)
    }

    private fun assertFiniteProfile(profile: LeitmotifPlaybackProfile) {
        assertTrue(profile.tempo.isFinite())
        assertTrue(profile.tempo in 0.5f..2f)
        assertTrue(profile.targetVolume.isFinite())
        assertTrue(profile.targetVolume in 0f..1f)
        with(profile.motifSignature) {
            assertTrue(leadPresence.isFinite() && leadPresence in 0f..1f)
            assertTrue(pulsePresence.isFinite() && pulsePresence in 0f..1f)
            assertTrue(warmth.isFinite() && warmth in 0f..1f)
            assertTrue(shimmer.isFinite() && shimmer in 0f..1f)
            assertTrue(cadenceLift.isFinite() && cadenceLift in 0f..1f)
        }
    }
}
