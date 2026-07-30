package com.anurag9000.forestrun.engine

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
        val spareReward = tracker.consumeReward()
        val mercifulReward = tracker.consumeReward()

        requireNotNull(spareReward)
        assertEquals(PacifistRewardKind.SPARE_STREAK, spareReward.kind)
        requireNotNull(mercifulReward)
        assertEquals(PacifistRouteTier.MERCIFUL, mercifulReward.routeTier)
        assertEquals(PacifistRewardKind.ROUTE_MERCIFUL, mercifulReward.kind)
        assertTrue(mercifulReward.points > kindReward.points)
    }

    @Test
    fun `simultaneous streak and biome rewards are preserved in FIFO order`() {
        val tracker = PacifistTracker()
        tracker.updateBiome(Biome.MEADOW)
        repeat(5) { tracker.recordCleanPass() }
        tracker.updateBiome(Biome.ORCHARD)

        val streak = requireNotNull(tracker.consumeReward())
        val friendship = requireNotNull(tracker.consumeReward())

        assertEquals(PacifistRewardKind.CLEAN_STREAK, streak.kind)
        assertEquals(PacifistRewardKind.BIOME_FRIENDSHIP, friendship.kind)
        assertEquals(Biome.MEADOW, friendship.friendBiome)
        assertNull(tracker.consumeReward())
    }

    @Test
    fun `route reward queues behind an existing streak instead of disappearing`() {
        val tracker = PacifistTracker()
        repeat(5) { tracker.recordCleanPass() }
        tracker.recordSpare()
        tracker.updateRouteReward(mercyHearts = 2, kindnessChain = 5)

        assertEquals(PacifistRewardKind.CLEAN_STREAK, tracker.consumeReward()?.kind)
        assertEquals(PacifistRewardKind.ROUTE_KIND, tracker.consumeReward()?.kind)
        assertNull(tracker.consumeReward())
    }

    @Test
    fun `reset clears counters queue biome state and rewarded tier`() {
        val tracker = PacifistTracker()
        tracker.updateBiome(Biome.MEADOW)
        repeat(5) { tracker.recordCleanPass() }
        tracker.recordSpare()
        tracker.recordHit()
        tracker.updateRouteReward(mercyHearts = 2, kindnessChain = 5)

        tracker.reset()

        assertEquals(0, tracker.cleanPassesThisRun)
        assertEquals(0, tracker.sparedThisRun)
        assertEquals(0, tracker.hitsThisRun)
        assertNull(tracker.consumeReward())
        assertEquals(PacifistRouteTier.NONE, tracker.currentRouteTier(0, 0))
    }

    @Test
    fun `run and biome counters saturate instead of wrapping`() {
        val tracker = PacifistTracker()
        setIntField(tracker, "cleanPassesThisRun", Int.MAX_VALUE)
        setIntField(tracker, "cleanPassesThisBiome", Int.MAX_VALUE)
        setIntField(tracker, "sparedThisRun", Int.MAX_VALUE)
        setIntField(tracker, "sparedThisBiome", Int.MAX_VALUE)
        setIntField(tracker, "hitsThisRun", Int.MAX_VALUE)

        tracker.recordCleanPass()
        tracker.recordSpare()
        tracker.recordHit()

        assertEquals(Int.MAX_VALUE, tracker.cleanPassesThisRun)
        assertEquals(Int.MAX_VALUE, tracker.sparedThisRun)
        assertEquals(Int.MAX_VALUE, tracker.hitsThisRun)
        assertEquals(Int.MAX_VALUE, intField(tracker, "cleanPassesThisBiome"))
        assertEquals(Int.MAX_VALUE, intField(tracker, "sparedThisBiome"))
    }

    @Test
    fun `bounded reward queue never grows without limit`() {
        val tracker = PacifistTracker()
        repeat(100) {
            setIntField(tracker, "cleanPassesThisRun", 4)
            tracker.recordCleanPass()
        }

        var consumed = 0
        while (tracker.consumeReward() != null) consumed++

        assertEquals(16, consumed)
    }

    private fun setIntField(tracker: PacifistTracker, name: String, value: Int) {
        val field = PacifistTracker::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.setInt(tracker, value)
    }

    private fun intField(tracker: PacifistTracker, name: String): Int {
        val field = PacifistTracker::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getInt(tracker)
    }
}
