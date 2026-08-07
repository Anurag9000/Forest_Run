package com.anurag9000.forestrun.engine

import android.graphics.Rect
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameAccessibilityNodeProviderTest {
    @Test
    fun rootPublishesCurrentSemanticChildrenAndVirtualNodeDescription() {
        val host = View(ApplicationProvider.getApplicationContext()).apply {
            layout(0, 0, 1000, 600)
        }
        val router = GameAccessibilityActionRouter(
            snapshotProvider = {
                AccessibilitySemanticSnapshot(
                    surface = AccessibilitySurface.PLAYING,
                    distanceM = 42,
                    score = 900,
                    seeds = 4,
                    bloomReady = true
                )
            },
            handler = AccessibilitySemanticActionHandler { _, _ -> true }
        )
        val provider = provider(host, router)

        val root = provider.createAccessibilityNodeInfo(AccessibilityNodeProvider.HOST_VIEW_ID)
        assertNotNull(root)
        assertEquals(4, root!!.childCount)

        val status = provider.createAccessibilityNodeInfo(AccessibilityNodeIds.RUN_STATUS)
        assertNotNull(status)
        assertEquals(
            "Run status, 42 metres, score 900, 4 Seeds, Bloom ready",
            status!!.contentDescription.toString()
        )
        assertFalse(status.isClickable)

        val jump = provider.createAccessibilityNodeInfo(AccessibilityNodeIds.RUN_JUMP)
        assertNotNull(jump)
        assertTrue(jump!!.isClickable)
        assertEquals("Jump", jump.contentDescription.toString())
    }

    @Test
    fun clickDelegatesOnlyTheSemanticActionPublishedByCurrentTree() {
        val calls = mutableListOf<Pair<Int, AccessibilitySemanticAction>>()
        val host = View(ApplicationProvider.getApplicationContext()).apply {
            layout(0, 0, 1000, 600)
        }
        val router = GameAccessibilityActionRouter(
            snapshotProvider = { AccessibilitySemanticSnapshot(AccessibilitySurface.MENU) },
            handler = AccessibilitySemanticActionHandler { nodeId, action ->
                calls += nodeId to action
                true
            }
        )
        val provider = provider(host, router)

        assertTrue(
            provider.performAction(
                AccessibilityNodeIds.MENU_GARDEN,
                AccessibilityNodeInfo.ACTION_CLICK,
                null
            )
        )
        assertEquals(
            listOf(
                AccessibilityNodeIds.MENU_GARDEN to AccessibilitySemanticAction.ACTIVATE
            ),
            calls
        )
        assertFalse(
            provider.performAction(
                AccessibilityNodeIds.REST_CONTINUE,
                AccessibilityNodeInfo.ACTION_CLICK,
                null
            )
        )
        assertEquals(1, calls.size)
    }

    @Test
    fun virtualAccessibilityFocusIsExclusiveAndClears() {
        val host = View(ApplicationProvider.getApplicationContext()).apply {
            layout(0, 0, 1000, 600)
        }
        val router = GameAccessibilityActionRouter(
            snapshotProvider = { AccessibilitySemanticSnapshot(AccessibilitySurface.MENU) },
            handler = AccessibilitySemanticActionHandler { _, _ -> true }
        )
        val provider = provider(host, router)

        assertTrue(
            provider.performAction(
                AccessibilityNodeIds.MENU_CONTINUE,
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                null
            )
        )
        assertNotNull(provider.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
        assertFalse(
            provider.performAction(
                AccessibilityNodeIds.MENU_CONTINUE,
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                null
            )
        )
        assertTrue(
            provider.performAction(
                AccessibilityNodeIds.MENU_CONTINUE,
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
                null
            )
        )
        assertNull(provider.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
    }

    @Test
    fun settingsToggleExposesCheckableState() {
        val host = View(ApplicationProvider.getApplicationContext()).apply {
            layout(0, 0, 1000, 600)
        }
        val router = GameAccessibilityActionRouter(
            snapshotProvider = {
                AccessibilitySemanticSnapshot(
                    surface = AccessibilitySurface.SETTINGS,
                    reducedMotion = true,
                    audioEnabled = false,
                    hapticsEnabled = true
                )
            },
            handler = AccessibilitySemanticActionHandler { _, _ -> true }
        )
        val provider = provider(host, router)

        val motion = provider.createAccessibilityNodeInfo(
            AccessibilityNodeIds.SETTINGS_REDUCED_MOTION
        )
        val audio = provider.createAccessibilityNodeInfo(
            AccessibilityNodeIds.SETTINGS_AUDIO
        )

        assertTrue(motion!!.isCheckable)
        assertTrue(motion.isChecked)
        assertTrue(audio!!.isCheckable)
        assertFalse(audio.isChecked)
    }

    private fun provider(
        host: View,
        router: GameAccessibilityActionRouter
    ): GameAccessibilityNodeProvider = GameAccessibilityNodeProvider(
        hostView = host,
        router = router,
        boundsResolver = AccessibilityNodeBoundsResolver { Rect(10, 10, 200, 100) }
    )
}
