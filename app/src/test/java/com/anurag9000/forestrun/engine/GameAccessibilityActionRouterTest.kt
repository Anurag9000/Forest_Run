package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAccessibilityActionRouterTest {
    @Test
    fun performsOnlyAnActionPublishedByTheCurrentNode() {
        val calls = mutableListOf<Pair<Int, AccessibilitySemanticAction>>()
        val router = router(
            snapshot = AccessibilitySemanticSnapshot(AccessibilitySurface.MENU),
            calls = calls
        )

        val result = router.perform(
            AccessibilityNodeIds.MENU_CONTINUE,
            AccessibilitySemanticAction.ACTIVATE
        )

        assertEquals(AccessibilityActionDisposition.PERFORMED, result.disposition)
        assertTrue(result.performed)
        assertEquals(
            listOf(
                AccessibilityNodeIds.MENU_CONTINUE to
                    AccessibilitySemanticAction.ACTIVATE
            ),
            calls
        )
    }

    @Test
    fun rejectsStaleNodeAndUnsupportedActionWithoutCallingLiveHandler() {
        val calls = mutableListOf<Pair<Int, AccessibilitySemanticAction>>()
        val router = router(
            snapshot = AccessibilitySemanticSnapshot(AccessibilitySurface.MENU),
            calls = calls
        )

        val stale = router.perform(
            AccessibilityNodeIds.REST_CONTINUE,
            AccessibilitySemanticAction.ACTIVATE
        )
        val unsupported = router.perform(
            AccessibilityNodeIds.MENU_CONTINUE,
            AccessibilitySemanticAction.DUCK
        )

        assertEquals(AccessibilityActionDisposition.NODE_NOT_FOUND, stale.disposition)
        assertEquals(
            AccessibilityActionDisposition.ACTION_NOT_AVAILABLE,
            unsupported.disposition
        )
        assertTrue(calls.isEmpty())
    }

    @Test
    fun disabledGardenNodeCannotSpendSeedsOrActivate() {
        val calls = mutableListOf<Pair<Int, AccessibilitySemanticAction>>()
        val router = router(
            snapshot = AccessibilitySemanticSnapshot(
                surface = AccessibilitySurface.GARDEN,
                seeds = 0,
                gardenUnlockedPlants = 1,
                gardenTotalPlants = 9,
                nextPlantCost = 20
            ),
            calls = calls
        )

        val locked = router.perform(
            AccessibilityNodeIds.GARDEN_FIRST_PLANT + 2,
            AccessibilitySemanticAction.ACTIVATE
        )
        val nextButUnaffordable = router.perform(
            AccessibilityNodeIds.GARDEN_FIRST_PLANT + 1,
            AccessibilitySemanticAction.ACTIVATE
        )

        assertEquals(AccessibilityActionDisposition.NODE_DISABLED, locked.disposition)
        assertEquals(
            AccessibilityActionDisposition.ACTION_NOT_AVAILABLE,
            nextButUnaffordable.disposition
        )
        assertTrue(calls.isEmpty())
    }

    @Test
    fun affordableNextGardenPlantIsAdmittedExactlyOnce() {
        val calls = mutableListOf<Pair<Int, AccessibilitySemanticAction>>()
        val router = router(
            snapshot = AccessibilitySemanticSnapshot(
                surface = AccessibilitySurface.GARDEN,
                seeds = 20,
                gardenUnlockedPlants = 1,
                gardenTotalPlants = 9,
                nextPlantCost = 20
            ),
            calls = calls
        )

        val result = router.perform(
            AccessibilityNodeIds.GARDEN_FIRST_PLANT + 1,
            AccessibilitySemanticAction.ACTIVATE
        )

        assertTrue(result.performed)
        assertEquals(1, calls.size)
    }

    @Test
    fun malformedSemanticSnapshotFailsClosed() {
        var calls = 0
        val router = GameAccessibilityActionRouter(
            snapshotProvider = {
                AccessibilitySemanticSnapshot(
                    surface = AccessibilitySurface.PLAYING,
                    distanceM = -1
                )
            },
            handler = AccessibilitySemanticActionHandler { _, _ ->
                calls += 1
                true
            }
        )

        val result = router.perform(
            AccessibilityNodeIds.RUN_JUMP,
            AccessibilitySemanticAction.JUMP
        )

        assertEquals(
            AccessibilityActionDisposition.SEMANTICS_UNAVAILABLE,
            result.disposition
        )
        assertFalse(result.performed)
        assertEquals(0, calls)
    }

    @Test
    fun rejectedOrThrowingLiveHandlerNeverReportsSuccess() {
        val snapshot = AccessibilitySemanticSnapshot(AccessibilitySurface.PLAYING)
        val rejected = GameAccessibilityActionRouter(
            snapshotProvider = { snapshot },
            handler = AccessibilitySemanticActionHandler { _, _ -> false }
        ).perform(
            AccessibilityNodeIds.RUN_DUCK,
            AccessibilitySemanticAction.DUCK
        )
        val throwing = GameAccessibilityActionRouter(
            snapshotProvider = { snapshot },
            handler = AccessibilitySemanticActionHandler { _, _ ->
                throw IllegalStateException("input unavailable")
            }
        ).perform(
            AccessibilityNodeIds.RUN_JUMP,
            AccessibilitySemanticAction.JUMP
        )

        assertEquals(
            AccessibilityActionDisposition.HANDLER_REJECTED,
            rejected.disposition
        )
        assertEquals(
            AccessibilityActionDisposition.HANDLER_REJECTED,
            throwing.disposition
        )
        assertFalse(rejected.performed)
        assertFalse(throwing.performed)
    }

    private fun router(
        snapshot: AccessibilitySemanticSnapshot,
        calls: MutableList<Pair<Int, AccessibilitySemanticAction>>
    ): GameAccessibilityActionRouter = GameAccessibilityActionRouter(
        snapshotProvider = { snapshot },
        handler = AccessibilitySemanticActionHandler { nodeId, action ->
            calls += nodeId to action
            true
        }
    )
}
