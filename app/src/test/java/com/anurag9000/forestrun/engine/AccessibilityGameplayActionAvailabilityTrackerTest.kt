package com.anurag9000.forestrun.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityGameplayActionAvailabilityTrackerTest {
    @Test
    fun `first observation establishes baseline without fabricating a change`() {
        val tracker = AccessibilityGameplayActionAvailabilityTracker()
        val changes = tracker.observe(
            AccessibilityGameplayActionAvailability(
                jumpEnabled = true,
                duckEnabled = true
            )
        )
        assertFalse(changes.jumpChanged)
        assertFalse(changes.duckChanged)
    }

    @Test
    fun `only changed semantic capability is reported`() {
        val tracker = AccessibilityGameplayActionAvailabilityTracker()
        tracker.reset(
            AccessibilityGameplayActionAvailability(
                jumpEnabled = true,
                duckEnabled = true
            )
        )

        val jumpOnly = tracker.observe(
            AccessibilityGameplayActionAvailability(
                jumpEnabled = false,
                duckEnabled = true
            )
        )
        assertTrue(jumpOnly.jumpChanged)
        assertFalse(jumpOnly.duckChanged)

        val duckOnly = tracker.observe(
            AccessibilityGameplayActionAvailability(
                jumpEnabled = false,
                duckEnabled = false
            )
        )
        assertFalse(duckOnly.jumpChanged)
        assertTrue(duckOnly.duckChanged)
    }

    @Test
    fun `unchanged airborne states do not produce repeated invalidations`() {
        val tracker = AccessibilityGameplayActionAvailabilityTracker()
        tracker.reset(
            AccessibilityGameplayActionAvailability(
                jumpEnabled = false,
                duckEnabled = false
            )
        )
        repeat(20) {
            val changes = tracker.observe(
                AccessibilityGameplayActionAvailability(
                    jumpEnabled = false,
                    duckEnabled = false
                )
            )
            assertFalse(changes.jumpChanged)
            assertFalse(changes.duckChanged)
        }
    }

    @Test
    fun `clear requires a new baseline before publishing later changes`() {
        val tracker = AccessibilityGameplayActionAvailabilityTracker()
        tracker.reset(
            AccessibilityGameplayActionAvailability(
                jumpEnabled = true,
                duckEnabled = true
            )
        )
        tracker.clear()
        val baseline = tracker.observe(
            AccessibilityGameplayActionAvailability(
                jumpEnabled = false,
                duckEnabled = false
            )
        )
        assertFalse(baseline.jumpChanged)
        assertFalse(baseline.duckChanged)

        val restored = tracker.observe(
            AccessibilityGameplayActionAvailability(
                jumpEnabled = true,
                duckEnabled = false
            )
        )
        assertTrue(restored.jumpChanged)
        assertFalse(restored.duckChanged)
    }
}
