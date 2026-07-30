package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwayComponentTest {

    @Test
    fun `invalid deltas do not poison future sway`() {
        val sway = SwayComponent(speed = 2f, intensity = 10f)

        assertEquals(0f, sway.getOffset(Float.NaN), 0f)
        assertEquals(0f, sway.getOffset(Float.POSITIVE_INFINITY), 0f)
        assertEquals(0f, sway.getOffset(-1f), 0f)
        val valid = sway.getOffset(0.1f)

        assertTrue(valid.isFinite())
        assertTrue(valid in -10f..10f)
    }

    @Test
    fun `invalid wind multiplier uses finite default and negative wind cannot rewind`() {
        val defaultWind = SwayComponent(speed = 2f, intensity = 10f)
        val invalidWind = SwayComponent(speed = 2f, intensity = 10f)
        val negativeWind = SwayComponent(speed = 2f, intensity = 10f)

        assertEquals(
            defaultWind.getOffset(0.1f),
            invalidWind.getOffset(0.1f, Float.NaN),
            0.0001f
        )
        assertEquals(
            defaultWind.getOffset(0.1f),
            negativeWind.getOffset(0.1f, -4f),
            0.0001f
        )
    }

    @Test
    fun `extreme finite time and wind remain bounded`() {
        val sway = SwayComponent(speed = Float.MAX_VALUE, intensity = Float.MAX_VALUE)

        repeat(8) {
            val offset = sway.getOffset(Float.MAX_VALUE, Float.MAX_VALUE)
            assertTrue(offset.isFinite())
            assertTrue(offset in -Float.MAX_VALUE..Float.MAX_VALUE)
        }
    }

    @Test
    fun `reset restores the initial zero phase`() {
        val sway = SwayComponent(speed = 2f, intensity = 10f)
        assertTrue(sway.getOffset(0.2f) != 0f)

        sway.reset()

        assertEquals(0f, sway.getOffset(0f), 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative speed is rejected`() {
        SwayComponent(speed = -1f, intensity = 1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non finite intensity is rejected`() {
        SwayComponent(speed = 1f, intensity = Float.NaN)
    }
}
