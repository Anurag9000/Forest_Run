package com.yourname.forest_run.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PacifistTrackerTest {

    @Test
    fun `clean pass streak rewards every five passes`() {
        val tracker = PacifistTracker()

        repeat(4) { tracker.recordCleanPass() }
        assertNull(tracker.consumeReward())

        tracker.recordCleanPass()
        val reward = tracker.consumeReward()

        assertNotNull(reward)
        assertEquals("Kindness streak +", reward?.message)
        assertEquals(5, tracker.cleanPassesThisRun)
    }

    @Test
    fun `clean biome transition grants biome friendship reward`() {
        val tracker = PacifistTracker()

        tracker.updateBiome(Biome.MEADOW)
        repeat(3) { tracker.recordCleanPass() }
        tracker.updateBiome(Biome.ORCHARD)

        val reward = tracker.consumeReward()

        assertNotNull(reward)
        assertEquals(2, reward?.seeds)
        assertEquals("Meadow befriended", reward?.message)
    }
}
