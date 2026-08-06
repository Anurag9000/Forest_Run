package com.anurag9000.forestrun.engine

/**
 * Deterministic announcement policy for the custom Canvas accessibility tree.
 *
 * High-priority surface and Bloom changes bypass routine throttling. Continuous
 * score/distance changes are coalesced into distance milestones so TalkBack is
 * not flooded by frame-driven live-region updates.
 */
internal class AccessibilityAnnouncementPolicy(
    private val routineIntervalMs: Long = DEFAULT_ROUTINE_INTERVAL_MS,
    private val distanceStepM: Int = DEFAULT_DISTANCE_STEP_M
) {
    private var previous: AccessibilitySemanticSnapshot? = null
    private var lastRoutineAnnouncementMs: Long = Long.MIN_VALUE
    private var lastDistanceBucket = 0

    init {
        require(routineIntervalMs > 0L) { "routine interval must be positive" }
        require(distanceStepM > 0) { "distance step must be positive" }
    }

    fun next(
        snapshot: AccessibilitySemanticSnapshot,
        nowMs: Long
    ): String? {
        require(nowMs >= 0L) { "announcement time must be non-negative" }
        val old = previous
        if (old == null || old.surface != snapshot.surface) {
            previous = snapshot
            lastDistanceBucket = snapshot.distanceM / distanceStepM
            lastRoutineAnnouncementMs = nowMs
            return surfaceAnnouncement(snapshot)
        }

        val priority = priorityAnnouncement(old, snapshot)
        previous = snapshot
        if (priority != null) return priority

        if (snapshot.surface != AccessibilitySurface.PLAYING) return null
        val bucket = snapshot.distanceM / distanceStepM
        if (bucket <= lastDistanceBucket) return null
        if (!routineIntervalElapsed(nowMs)) return null

        lastDistanceBucket = bucket
        lastRoutineAnnouncementMs = nowMs
        return runStatus(snapshot)
    }

    fun reset() {
        previous = null
        lastRoutineAnnouncementMs = Long.MIN_VALUE
        lastDistanceBucket = 0
    }

    private fun priorityAnnouncement(
        old: AccessibilitySemanticSnapshot,
        current: AccessibilitySemanticSnapshot
    ): String? = when (current.surface) {
        AccessibilitySurface.PLAYING -> when {
            !old.bloomActive && current.bloomActive -> "Bloom active"
            old.bloomActive && !current.bloomActive -> "Bloom ended"
            !old.bloomReady && current.bloomReady -> "Bloom ready"
            else -> null
        }
        AccessibilitySurface.GARDEN -> when {
            current.gardenUnlockedPlants > old.gardenUnlockedPlants ->
                "Garden plant ${current.gardenUnlockedPlants} grown"
            current.wardrobeUnlocked && !old.wardrobeUnlocked ->
                "Wardrobe unlocked"
            else -> null
        }
        AccessibilitySurface.SETTINGS -> when {
            current.reducedMotion != old.reducedMotion ->
                "Reduced motion ${onOff(current.reducedMotion)}"
            current.audioEnabled != old.audioEnabled ->
                "Audio ${onOff(current.audioEnabled)}"
            current.hapticsEnabled != old.hapticsEnabled ->
                "Haptics ${onOff(current.hapticsEnabled)}"
            else -> null
        }
        AccessibilitySurface.MENU,
        AccessibilitySurface.REST -> null
    }

    private fun surfaceAnnouncement(snapshot: AccessibilitySemanticSnapshot): String =
        when (snapshot.surface) {
            AccessibilitySurface.MENU -> "Forest Run menu"
            AccessibilitySurface.SETTINGS -> "Feedback settings"
            AccessibilitySurface.PLAYING -> "Run started. Jump, long jump, or duck"
            AccessibilitySurface.GARDEN ->
                "Garden. ${snapshot.gardenUnlockedPlants} of " +
                    "${snapshot.gardenTotalPlants} plants grown"
            AccessibilitySurface.REST -> snapshot.restQuote.ifBlank {
                "Rest beneath the willow"
            }
        }

    private fun runStatus(snapshot: AccessibilitySemanticSnapshot): String {
        val bloom = when {
            snapshot.bloomActive -> "Bloom active"
            snapshot.bloomReady -> "Bloom ready"
            else -> "Bloom charging"
        }
        return "${snapshot.distanceM} metres, ${snapshot.seeds} Seeds, $bloom"
    }

    private fun routineIntervalElapsed(nowMs: Long): Boolean =
        lastRoutineAnnouncementMs == Long.MIN_VALUE ||
            nowMs >= lastRoutineAnnouncementMs + routineIntervalMs

    private fun onOff(enabled: Boolean): String = if (enabled) "on" else "off"

    companion object {
        private const val DEFAULT_ROUTINE_INTERVAL_MS = 10_000L
        private const val DEFAULT_DISTANCE_STEP_M = 100
    }
}
