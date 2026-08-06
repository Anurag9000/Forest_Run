package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityAnnouncementPolicyTest {
    @Test
    fun surfaceChangesAnnounceImmediately() {
        val policy = AccessibilityAnnouncementPolicy()

        assertEquals(
            "Forest Run menu",
            policy.next(snapshot(AccessibilitySurface.MENU), 0L)
        )
        assertEquals(
            "Run started. Jump, long jump, or duck",
            policy.next(snapshot(AccessibilitySurface.PLAYING), 1L)
        )
        assertEquals(
            "Garden. 2 of 9 plants grown",
            policy.next(
                snapshot(
                    AccessibilitySurface.GARDEN,
                    gardenUnlockedPlants = 2
                ),
                2L
            )
        )
        assertEquals(
            "Rest beneath the willow",
            policy.next(snapshot(AccessibilitySurface.REST), 3L)
        )
    }

    @Test
    fun routineRunStatusRequiresDistanceBucketAndInterval() {
        val policy = AccessibilityAnnouncementPolicy(
            routineIntervalMs = 10_000L,
            distanceStepM = 100
        )
        policy.next(snapshot(AccessibilitySurface.PLAYING), 0L)

        assertNull(
            policy.next(
                snapshot(AccessibilitySurface.PLAYING, distanceM = 99, score = 999),
                20_000L
            )
        )
        assertNull(
            policy.next(
                snapshot(AccessibilitySurface.PLAYING, distanceM = 100),
                9_999L
            )
        )
        assertEquals(
            "101 metres, 3 Seeds, Bloom charging",
            policy.next(
                snapshot(
                    AccessibilitySurface.PLAYING,
                    distanceM = 101,
                    seeds = 3
                ),
                10_000L
            )
        )
        assertNull(
            policy.next(
                snapshot(AccessibilitySurface.PLAYING, distanceM = 199),
                30_000L
            )
        )
    }

    @Test
    fun bloomTransitionsBypassRoutineThrottleWithoutResettingDistanceMilestone() {
        val policy = AccessibilityAnnouncementPolicy(
            routineIntervalMs = 10_000L,
            distanceStepM = 100
        )
        policy.next(snapshot(AccessibilitySurface.PLAYING), 0L)

        assertEquals(
            "Bloom ready",
            policy.next(
                snapshot(AccessibilitySurface.PLAYING, bloomReady = true),
                1L
            )
        )
        assertEquals(
            "Bloom active",
            policy.next(
                snapshot(
                    AccessibilitySurface.PLAYING,
                    bloomReady = true,
                    bloomActive = true
                ),
                2L
            )
        )
        assertEquals(
            "Bloom ended",
            policy.next(
                snapshot(AccessibilitySurface.PLAYING, distanceM = 100),
                3L
            )
        )
        assertEquals(
            "100 metres, 0 Seeds, Bloom charging",
            policy.next(
                snapshot(AccessibilitySurface.PLAYING, distanceM = 100),
                10_000L
            )
        )
    }

    @Test
    fun gardenAndSettingsAnnounceOnlyMeaningfulStateChanges() {
        val policy = AccessibilityAnnouncementPolicy()
        policy.next(
            snapshot(
                AccessibilitySurface.GARDEN,
                gardenUnlockedPlants = 1
            ),
            0L
        )
        assertEquals(
            "Garden plant 2 grown",
            policy.next(
                snapshot(
                    AccessibilitySurface.GARDEN,
                    gardenUnlockedPlants = 2
                ),
                1L
            )
        )
        assertEquals(
            "Wardrobe unlocked",
            policy.next(
                snapshot(
                    AccessibilitySurface.GARDEN,
                    gardenUnlockedPlants = 2,
                    wardrobeUnlocked = true
                ),
                2L
            )
        )

        policy.next(snapshot(AccessibilitySurface.SETTINGS), 3L)
        assertEquals(
            "Reduced motion on",
            policy.next(
                snapshot(
                    AccessibilitySurface.SETTINGS,
                    reducedMotion = true
                ),
                4L
            )
        )
        assertEquals(
            "Audio off",
            policy.next(
                snapshot(
                    AccessibilitySurface.SETTINGS,
                    reducedMotion = true,
                    audioEnabled = false
                ),
                5L
            )
        )
        assertEquals(
            "Haptics off",
            policy.next(
                snapshot(
                    AccessibilitySurface.SETTINGS,
                    reducedMotion = true,
                    audioEnabled = false,
                    hapticsEnabled = false
                ),
                6L
            )
        )
        assertNull(
            policy.next(
                snapshot(
                    AccessibilitySurface.SETTINGS,
                    reducedMotion = true,
                    audioEnabled = false,
                    hapticsEnabled = false,
                    score = 999
                ),
                7L
            )
        )
    }

    @Test
    fun resetRestartsOrientationWithoutLeakingPreviousState() {
        val policy = AccessibilityAnnouncementPolicy()
        policy.next(snapshot(AccessibilitySurface.PLAYING), 0L)
        policy.reset()

        assertEquals(
            "Run started. Jump, long jump, or duck",
            policy.next(
                snapshot(
                    AccessibilitySurface.PLAYING,
                    distanceM = 500,
                    bloomActive = true
                ),
                1L
            )
        )
    }

    private fun snapshot(
        surface: AccessibilitySurface,
        reducedMotion: Boolean = false,
        audioEnabled: Boolean = true,
        hapticsEnabled: Boolean = true,
        distanceM: Int = 0,
        score: Int = 0,
        seeds: Int = 0,
        bloomReady: Boolean = false,
        bloomActive: Boolean = false,
        gardenUnlockedPlants: Int = 0,
        wardrobeUnlocked: Boolean = false
    ): AccessibilitySemanticSnapshot = AccessibilitySemanticSnapshot(
        surface = surface,
        reducedMotion = reducedMotion,
        audioEnabled = audioEnabled,
        hapticsEnabled = hapticsEnabled,
        distanceM = distanceM,
        score = score,
        seeds = seeds,
        bloomReady = bloomReady,
        bloomActive = bloomActive,
        gardenUnlockedPlants = gardenUnlockedPlants,
        gardenTotalPlants = 9,
        wardrobeUnlocked = wardrobeUnlocked
    )
}
