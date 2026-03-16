package com.yourname.forest_run.engine

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
    }

    @Test
    fun `playback profile remains clamped at extreme scroll values`() {
        val profile = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.PLAYING_3,
            GameConstants.BASE_SCROLL_SPEED * 4f
        )

        assertTrue(profile.tempo in 1f..1.8f)
        assertTrue(profile.targetVolume in 0f..1f)
    }
}
