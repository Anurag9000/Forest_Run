package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HapticManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(FeedbackSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        FeedbackSettings.resetMemoryForTests()
        HapticManager.release()
    }

    @After
    fun tearDown() {
        HapticManager.release()
        FeedbackSettings.resetMemoryForTests()
    }

    @Test
    fun `init acquires service and release clears ownership`() {
        HapticManager.init(context)
        assertTrue(HapticManager.hasServiceForTest())

        HapticManager.release()

        assertFalse(HapticManager.hasServiceForTest())
    }

    @Test
    fun `cancel retains service for later reenable`() {
        HapticManager.init(context)
        HapticManager.cancel()

        assertTrue(HapticManager.hasServiceForTest())
    }

    @Test
    fun `all patterns are no ops when haptics are disabled or released`() {
        FeedbackSettings.setHapticsEnabled(context, false)
        HapticManager.shortPulse()
        HapticManager.mediumPulse()
        HapticManager.longPulse()
        HapticManager.doubleTap()
        HapticManager.bloomSurge()

        HapticManager.release()
        FeedbackSettings.setHapticsEnabled(context, true)
        HapticManager.shortPulse()
        HapticManager.mediumPulse()
        HapticManager.longPulse()
        HapticManager.doubleTap()
        HapticManager.bloomSurge()

        assertFalse(HapticManager.hasServiceForTest())
    }

    @Test
    fun `release and cancel are idempotent before initialization`() {
        repeat(3) {
            HapticManager.cancel()
            HapticManager.release()
        }

        assertFalse(HapticManager.hasServiceForTest())
    }
}
