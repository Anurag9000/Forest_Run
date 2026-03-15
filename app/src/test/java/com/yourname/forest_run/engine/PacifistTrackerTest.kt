package com.yourname.forest_run.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PacifistTrackerTest {

    @Test
    fun `befriending a biome emits a friendship reward`() {
        val tracker = PacifistTracker()
        tracker.updateBiome(Biome.MEADOW)
        repeat(3) { tracker.recordCleanPass() }

        tracker.updateBiome(Biome.ORCHARD)
        val reward = tracker.consumeReward()

        requireNotNull(reward)
        assertEquals(Biome.MEADOW, reward.friendBiome)
        assertEquals("Meadow befriended", reward.message)
    }

    @Test
    fun `biome friendship reward is blocked after a hit`() {
        val tracker = PacifistTracker()
        tracker.updateBiome(Biome.MEADOW)
        repeat(4) { tracker.recordCleanPass() }
        tracker.recordHit()

        tracker.updateBiome(Biome.ORCHARD)

        assertNull(tracker.consumeReward())
    }
}
