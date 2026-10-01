package com.anurag9000.forestrun.engine

/** Current enablement of the semantic gameplay controls exposed to services. */
internal data class AccessibilityGameplayActionAvailability(
    val jumpEnabled: Boolean,
    val duckEnabled: Boolean
)

internal data class AccessibilityGameplayActionAvailabilityChanges(
    val jumpChanged: Boolean,
    val duckChanged: Boolean
)

/**
 * Remembers only semantic action availability, not animation state.
 *
 * Accessibility services need a content-change event when an action becomes
 * available/unavailable, but JUMP_START -> JUMPING -> APEX transitions with the
 * same capabilities should not emit redundant tree churn.
 */
internal class AccessibilityGameplayActionAvailabilityTracker {
    private var previous: AccessibilityGameplayActionAvailability? = null

    fun observe(
        current: AccessibilityGameplayActionAvailability
    ): AccessibilityGameplayActionAvailabilityChanges {
        val before = previous
        previous = current
        return if (before == null) {
            AccessibilityGameplayActionAvailabilityChanges(
                jumpChanged = false,
                duckChanged = false
            )
        } else {
            AccessibilityGameplayActionAvailabilityChanges(
                jumpChanged = before.jumpEnabled != current.jumpEnabled,
                duckChanged = before.duckEnabled != current.duckEnabled
            )
        }
    }

    fun reset(current: AccessibilityGameplayActionAvailability) {
        previous = current
    }

    fun clear() {
        previous = null
    }
}
