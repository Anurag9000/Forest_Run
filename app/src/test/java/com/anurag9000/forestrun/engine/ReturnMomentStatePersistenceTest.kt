package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReturnMomentStatePersistenceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `overflowed rough run increment preserves saturated streak`() {
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("rough_run_streak", Int.MAX_VALUE)
            .commit()

        SaveManager.saveReturnMomentState(
            context,
            ReturnMomentState(
                lastActiveAtMs = 1_000L,
                lastGardenGreetingDay = 4L,
                roughRunStreak = Int.MIN_VALUE
            )
        )

        assertEquals(Int.MAX_VALUE, SaveManager.loadReturnMomentState(context).roughRunStreak)
    }

    @Test
    fun `invalid persisted Return Moment values are sanitized on load`() {
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong("last_active_at_ms", -5L)
            .putLong("last_garden_greeting_day", -20L)
            .putInt("rough_run_streak", -9)
            .commit()

        assertEquals(
            ReturnMomentState(
                lastActiveAtMs = 0L,
                lastGardenGreetingDay = -1L,
                roughRunStreak = 0
            ),
            SaveManager.loadReturnMomentState(context)
        )
    }
}
