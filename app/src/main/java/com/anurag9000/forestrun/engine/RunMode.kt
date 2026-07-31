package com.anurag9000.forestrun.engine

/**
 * Execution policy for one run.
 *
 * Screen state ([AppGameState]) and death flow ([RunState]) describe where the
 * user is; [RunMode] describes which side effects are legal. Deterministic
 * capture/profile/debug runs must never write permanent progression or ghosts.
 */
enum class RunMode(
    val persistsProgress: Boolean,
    val recordsGhost: Boolean,
    val allowsRandomSpawns: Boolean,
    val allowsOrdinaryProgressCues: Boolean,
    val allowsDefaultGhostPlayback: Boolean
) {
    NORMAL(
        persistsProgress = true,
        recordsGhost = true,
        allowsRandomSpawns = true,
        allowsOrdinaryProgressCues = true,
        allowsDefaultGhostPlayback = true
    ),
    DEBUG_SCENARIO(
        persistsProgress = false,
        recordsGhost = false,
        allowsRandomSpawns = false,
        allowsOrdinaryProgressCues = false,
        allowsDefaultGhostPlayback = false
    ),
    SCREENSHOT_CAPTURE(
        persistsProgress = false,
        recordsGhost = false,
        allowsRandomSpawns = false,
        allowsOrdinaryProgressCues = false,
        allowsDefaultGhostPlayback = false
    ),
    PERFORMANCE_PROFILE(
        persistsProgress = false,
        recordsGhost = false,
        allowsRandomSpawns = false,
        allowsOrdinaryProgressCues = false,
        allowsDefaultGhostPlayback = false
    );

    val isDeterministic: Boolean
        get() = this != NORMAL

    companion object {
        /**
         * Resolve a mode for a named deterministic scenario. NORMAL is rejected
         * here so an external intent cannot accidentally re-enable persistence.
         */
        fun forScenario(rawName: String?): RunMode {
            val requested = entries.firstOrNull { it.name == rawName }
            return requested?.takeIf { it.isDeterministic } ?: DEBUG_SCENARIO
        }
    }
}
