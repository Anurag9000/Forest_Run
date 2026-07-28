package com.anurag9000.forestrun.engine

import java.util.Calendar
import java.util.TimeZone

/**
 * Stable day identifier based on the user's local calendar date rather than a
 * fixed UTC-sized bucket. The multiplier is greater than the maximum number of
 * days in a year, so adjacent years cannot collide.
 */
internal fun localCalendarDayId(
    nowMs: Long,
    timeZone: TimeZone = TimeZone.getDefault()
): Long {
    val calendar = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMs
    }
    return calendar.get(Calendar.YEAR).toLong() * 400L +
        calendar.get(Calendar.DAY_OF_YEAR).toLong()
}
