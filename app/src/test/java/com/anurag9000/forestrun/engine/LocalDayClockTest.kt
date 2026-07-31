package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class LocalDayClockTest {
    @Test
    fun `utc bucket boundary does not create a new local day`() {
        val zone = TimeZone.getTimeZone("Asia/Kolkata")
        val beforeUtcMidnight = utcMillis(2026, Calendar.JULY, 27, 23, 59)
        val afterUtcMidnight = utcMillis(2026, Calendar.JULY, 28, 0, 1)

        assertEquals(
            localCalendarDayId(beforeUtcMidnight, zone),
            localCalendarDayId(afterUtcMidnight, zone)
        )
    }

    @Test
    fun `local midnight creates a new day`() {
        val zone = TimeZone.getTimeZone("Asia/Kolkata")
        val beforeLocalMidnight = utcMillis(2026, Calendar.JULY, 28, 18, 29)
        val afterLocalMidnight = utcMillis(2026, Calendar.JULY, 28, 18, 31)

        assertNotEquals(
            localCalendarDayId(beforeLocalMidnight, zone),
            localCalendarDayId(afterLocalMidnight, zone)
        )
    }

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis
}
