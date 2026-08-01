package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeProgressionArithmeticTest {

    @Test
    fun `increment normalizes negatives and saturates without overflow`() {
        assertEquals(1, SafeProgressionArithmetic.saturatingIncrement(-7, maximum = 10))
        assertEquals(6, SafeProgressionArithmetic.saturatingIncrement(5, maximum = 10))
        assertEquals(10, SafeProgressionArithmetic.saturatingIncrement(10, maximum = 10))
        assertEquals(10, SafeProgressionArithmetic.saturatingIncrement(Int.MAX_VALUE, maximum = 10))
        assertEquals(
            SafeProgressionArithmetic.DEFAULT_COUNTER_MAX,
            SafeProgressionArithmetic.saturatingIncrement(Int.MAX_VALUE)
        )
    }

    @Test
    fun `zero maximum remains a valid saturated counter`() {
        assertEquals(0, SafeProgressionArithmetic.saturatingIncrement(-1, maximum = 0))
        assertEquals(0, SafeProgressionArithmetic.saturatingIncrement(0, maximum = 0))
        assertEquals(0, SafeProgressionArithmetic.saturatingIncrement(Int.MAX_VALUE, maximum = 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative maximum is rejected`() {
        SafeProgressionArithmetic.saturatingIncrement(0, maximum = -1)
    }

    @Test
    fun `elapsed threshold is rollback safe and subtraction cannot overflow`() {
        assertTrue(SafeProgressionArithmetic.elapsedAtLeast(10_000L, 1_000L, 9_000L))
        assertFalse(SafeProgressionArithmetic.elapsedAtLeast(9_999L, 1_000L, 9_000L))
        assertFalse(SafeProgressionArithmetic.elapsedAtLeast(999L, 1_000L, 0L))
        assertFalse(SafeProgressionArithmetic.elapsedAtLeast(-1L, 0L, 0L))
        assertFalse(SafeProgressionArithmetic.elapsedAtLeast(0L, -1L, 0L))
        assertFalse(SafeProgressionArithmetic.elapsedAtLeast(0L, 0L, -1L))
        assertTrue(
            SafeProgressionArithmetic.elapsedAtLeast(
                Long.MAX_VALUE,
                0L,
                Long.MAX_VALUE
            )
        )
    }

    @Test
    fun `elapsed value returns zero for rollback or invalid timestamps`() {
        assertEquals(9_000L, SafeProgressionArithmetic.elapsedOrZero(10_000L, 1_000L))
        assertEquals(0L, SafeProgressionArithmetic.elapsedOrZero(999L, 1_000L))
        assertEquals(0L, SafeProgressionArithmetic.elapsedOrZero(-1L, 0L))
        assertEquals(Long.MAX_VALUE, SafeProgressionArithmetic.elapsedOrZero(Long.MAX_VALUE, 0L))
    }
}
