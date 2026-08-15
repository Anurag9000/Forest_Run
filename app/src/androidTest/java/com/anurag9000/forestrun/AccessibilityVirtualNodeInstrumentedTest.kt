package com.anurag9000.forestrun

import android.os.SystemClock
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anurag9000.forestrun.engine.AccessibilityNodeIds
import com.anurag9000.forestrun.engine.FeedbackSettings
import com.anurag9000.forestrun.engine.GameView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityVirtualNodeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext.applicationContext

    @Before
    fun setUp() {
        InstrumentationStateReset.clear(targetContext)
    }

    @Test
    fun installedGameViewPublishesAndRoutesVirtualAccessibilityNodes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var gameView: GameView
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                gameView = content.getChildAt(0) as GameView
            }

            waitForCondition("GameView accessibility host is laid out") {
                gameView.width > 0 && gameView.height > 0 && gameView.debugFrameCounter > 5
            }

            scenario.onActivity {
                val provider = gameView.accessibilityNodeProvider
                assertNotNull(provider)
                provider ?: return@onActivity

                val menuRoot = provider.createAccessibilityNodeInfo(
                    AccessibilityNodeProvider.HOST_VIEW_ID
                )
                assertNotNull(menuRoot)
                assertEquals(4, menuRoot!!.childCount)

                val continueNode = provider.createAccessibilityNodeInfo(
                    AccessibilityNodeIds.MENU_CONTINUE
                )
                assertNotNull(continueNode)
                assertEquals("Begin forest run", continueNode!!.contentDescription.toString())
                assertTrue(continueNode.isClickable)

                val journalNode = provider.createAccessibilityNodeInfo(
                    AccessibilityNodeIds.MENU_JOURNAL
                )
                assertNotNull(journalNode)
                assertEquals(
                    "Open Forest Journal",
                    journalNode!!.contentDescription.toString()
                )
                assertTrue(journalNode.isClickable)

                assertNull(
                    provider.createAccessibilityNodeInfo(
                        AccessibilityNodeIds.SETTINGS_REDUCED_MOTION
                    )
                )

                assertTrue(
                    provider.performAction(
                        AccessibilityNodeIds.MENU_SETTINGS,
                        AccessibilityNodeInfo.ACTION_CLICK,
                        null
                    )
                )

                val settingsRoot = provider.createAccessibilityNodeInfo(
                    AccessibilityNodeProvider.HOST_VIEW_ID
                )
                assertNotNull(settingsRoot)
                assertEquals(4, settingsRoot!!.childCount)
                assertNull(
                    provider.createAccessibilityNodeInfo(
                        AccessibilityNodeIds.MENU_CONTINUE
                    )
                )
                assertNull(
                    provider.createAccessibilityNodeInfo(
                        AccessibilityNodeIds.MENU_JOURNAL
                    )
                )

                val motionBefore = provider.createAccessibilityNodeInfo(
                    AccessibilityNodeIds.SETTINGS_REDUCED_MOTION
                )
                assertNotNull(motionBefore)
                assertTrue(motionBefore!!.isCheckable)
                assertFalse(motionBefore.isChecked)
                assertFalse(FeedbackSettings.reducedMotion)

                assertTrue(
                    provider.performAction(
                        AccessibilityNodeIds.SETTINGS_REDUCED_MOTION,
                        AccessibilityNodeInfo.ACTION_CLICK,
                        null
                    )
                )

                val motionAfter = provider.createAccessibilityNodeInfo(
                    AccessibilityNodeIds.SETTINGS_REDUCED_MOTION
                )
                assertNotNull(motionAfter)
                assertTrue(motionAfter!!.isChecked)
                assertTrue(FeedbackSettings.reducedMotion)

                assertTrue(
                    provider.performAction(
                        AccessibilityNodeIds.SETTINGS_CLOSE,
                        AccessibilityNodeInfo.ACTION_CLICK,
                        null
                    )
                )
                assertNotNull(
                    provider.createAccessibilityNodeInfo(
                        AccessibilityNodeIds.MENU_CONTINUE
                    )
                )
                assertNotNull(
                    provider.createAccessibilityNodeInfo(
                        AccessibilityNodeIds.MENU_JOURNAL
                    )
                )
                assertNull(
                    provider.createAccessibilityNodeInfo(
                        AccessibilityNodeIds.SETTINGS_REDUCED_MOTION
                    )
                )
            }
        }
    }

    private fun waitForCondition(
        label: String,
        timeoutMs: Long = 8_000L,
        condition: () -> Boolean
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(50L)
        }
        throw AssertionError("Timed out waiting for $label")
    }
}