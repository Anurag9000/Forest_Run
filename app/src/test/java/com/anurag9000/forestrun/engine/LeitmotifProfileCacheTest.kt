package com.anurag9000.forestrun.engine

import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class LeitmotifProfileCacheTest {

    @Test
    fun `menu and rest profiles reuse immutable instances`() {
        assertSame(
            buildLeitmotifPlaybackProfile(
                LeitmotifManager.MusicState.MENU,
                GameConstants.BASE_SCROLL_SPEED
            ),
            buildLeitmotifPlaybackProfile(
                LeitmotifManager.MusicState.MENU,
                GameConstants.BASE_SCROLL_SPEED * 1.8f
            )
        )
        assertSame(
            buildLeitmotifPlaybackProfile(
                LeitmotifManager.MusicState.REST,
                GameConstants.BASE_SCROLL_SPEED
            ),
            buildLeitmotifPlaybackProfile(
                LeitmotifManager.MusicState.REST,
                GameConstants.BASE_SCROLL_SPEED * 1.8f
            )
        )
    }

    @Test
    fun `run profiles remain derived from live speed`() {
        val early = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.PLAYING_1,
            GameConstants.BASE_SCROLL_SPEED
        )
        val faster = buildLeitmotifPlaybackProfile(
            LeitmotifManager.MusicState.PLAYING_1,
            GameConstants.BASE_SCROLL_SPEED * 1.4f
        )

        assertNotSame(early, faster)
    }
}
