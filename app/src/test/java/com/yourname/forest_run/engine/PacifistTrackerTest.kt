package com.yourname.forest_run.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertEquals(PacifistRewardKind.BIOME_FRIENDSHIP, reward.kind)
        assertEquals("Meadow at peace", reward.message)
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

    @Test
    fun `route rewards escalate with merciful play`() {
        val tracker = PacifistTracker()
        repeat(4) { tracker.recordCleanPass() }
        tracker.recordSpare()
        tracker.updateRouteReward(mercyHearts = 2, kindnessChain = 6)
        val kindReward = tracker.consumeReward()

        requireNotNull(kindReward)
        assertEquals(PacifistRouteTier.KIND, kindReward.routeTier)
        assertEquals(PacifistRewardKind.ROUTE_KIND, kindReward.kind)

        tracker.recordSpare()
        repeat(2) { tracker.recordCleanPass() }
        tracker.updateRouteReward(mercyHearts = 3, kindnessChain = 8)
        tracker.consumeReward()
        tracker.updateRouteReward(mercyHearts = 3, kindnessChain = 8)
        val mercifulReward = tracker.consumeReward()

        requireNotNull(mercifulReward)
        assertEquals(PacifistRouteTier.MERCIFUL, mercifulReward.routeTier)
        assertEquals(PacifistRewardKind.ROUTE_MERCIFUL, mercifulReward.kind)
        assertTrue(mercifulReward.points > kindReward.points)
    }
}
