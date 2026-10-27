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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
    fun `cue admission and lifecycle cancellation share one ordering monitor`() {
        HapticManager.init(context)

        assertWaitsForLifecycleMonitor { HapticManager.shortPulse() }
        assertWaitsForLifecycleMonitor { HapticManager.cancel() }
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

    private fun assertWaitsForLifecycleMonitor(action: () -> Unit) {
        val attempted = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val worker = Thread {
            attempted.countDown()
            action()
            completed.countDown()
        }

        synchronized(HapticManager) {
            worker.start()
            assertTrue(attempted.await(2, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (worker.state != Thread.State.BLOCKED &&
                completed.count != 0L && System.nanoTime() < deadline
            ) {
                Thread.yield()
            }
            assertTrue("operation must block on the lifecycle monitor", completed.count != 0L)
            assertTrue("worker must reach the lifecycle monitor", worker.state == Thread.State.BLOCKED)
        }

        assertTrue("operation must resume after lifecycle monitor release", completed.await(2, TimeUnit.SECONDS))
        worker.join(TimeUnit.SECONDS.toMillis(2))
        assertFalse("worker must terminate", worker.isAlive)
    }
}
