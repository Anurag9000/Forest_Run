package com.anurag9000.forestrun.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class MathUtilsTest {
    @Test
    fun `lerp preserves endpoints and allows extrapolation`() {
        assertEquals(2f, MathUtils.lerp(2f, 8f, 0f), 0f)
        assertEquals(8f, MathUtils.lerp(2f, 8f, 1f), 0f)
        assertEquals(5f, MathUtils.lerp(2f, 8f, 0.5f), 0f)
        assertEquals(11f, MathUtils.lerp(2f, 8f, 1.5f), 0f)
    }

    @Test
    fun `clamp and clamp01 include both boundaries`() {
        assertEquals(-2f, MathUtils.clamp(-3f, -2f, 4f), 0f)
        assertEquals(1.5f, MathUtils.clamp(1.5f, -2f, 4f), 0f)
        assertEquals(4f, MathUtils.clamp(8f, -2f, 4f), 0f)
        assertEquals(0f, MathUtils.clamp01(-1f), 0f)
        assertEquals(0.4f, MathUtils.clamp01(0.4f), 0f)
        assertEquals(1f, MathUtils.clamp01(2f), 0f)
    }

    @Test
    fun `normalise handles boundaries reversed ranges and a zero range`() {
        assertEquals(0f, MathUtils.normalise(-2f, -2f, 6f), 0f)
        assertEquals(0.5f, MathUtils.normalise(2f, -2f, 6f), 0f)
        assertEquals(1f, MathUtils.normalise(6f, -2f, 6f), 0f)
        assertEquals(0f, MathUtils.normalise(-4f, 5f, 5f), 0f)
        assertEquals(0.5f, MathUtils.normalise(1f, 6f, -4f), 0f)
        assertEquals(0f, MathUtils.normalise(8f, 6f, -4f), 0f)
    }

    @Test
    fun `normalise avoids overflow for finite ranges spanning float extremes`() {
        assertEquals(
            0.5f,
            MathUtils.normalise(0f, -Float.MAX_VALUE, Float.MAX_VALUE),
            0f
        )
        assertEquals(
            0.75f,
            MathUtils.normalise(Float.MAX_VALUE / 2f, -Float.MAX_VALUE, Float.MAX_VALUE),
            0.000001f
        )
        assertEquals(
            0.5f,
            MathUtils.map(0f, -Float.MAX_VALUE, Float.MAX_VALUE, 0f, 1f),
            0f
        )
    }

    @Test
    fun `map clamps input and supports reversed output ranges`() {
        assertEquals(-10f, MathUtils.map(-1f, 0f, 10f, -10f, 10f), 0f)
        assertEquals(0f, MathUtils.map(5f, 0f, 10f, 10f, -10f), 0f)
        assertEquals(-10f, MathUtils.map(11f, 0f, 10f, 10f, -10f), 0f)
    }

    @Test
    fun `smooth step clamps its factor and reaches midpoint smoothly`() {
        assertEquals(0f, MathUtils.smoothStep(0f, 10f, -1f), 0f)
        assertEquals(5f, MathUtils.smoothStep(0f, 10f, 0.5f), 0f)
        assertEquals(10f, MathUtils.smoothStep(0f, 10f, 2f), 0f)
    }

    @Test
    fun `degree trigonometry dp conversion and sign cover representative values`() {
        assertEquals(0f, MathUtils.sinDeg(0f), 0.000001f)
        assertEquals(1f, MathUtils.sinDeg(90f), 0.000001f)
        assertEquals(-1f, MathUtils.cosDeg(180f), 0.000001f)
        assertEquals(10f, MathUtils.dpToPx(4f, 2.5f), 0f)
        assertEquals(1f, MathUtils.sign(3f), 0f)
        assertEquals(-1f, MathUtils.sign(-3f), 0f)
        assertEquals(0f, MathUtils.sign(0f), 0f)
    }
}
