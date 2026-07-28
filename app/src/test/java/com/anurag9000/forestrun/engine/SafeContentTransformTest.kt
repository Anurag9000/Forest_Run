package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeContentTransformTest {

    @Test
    fun `zero insets preserve the existing logical coordinate system`() {
        val transform = SafeContentTransform.create(1920, 1080)

        assertEquals(1f, transform.scale, 0.0001f)
        assertEquals(0f, transform.contentLeft, 0.0001f)
        assertEquals(0f, transform.contentTop, 0.0001f)
        assertEquals(1920f, transform.contentWidth, 0.0001f)
        assertEquals(1080f, transform.contentHeight, 0.0001f)
    }

    @Test
    fun `asymmetric landscape cutout keeps aspect ratio and centers vertically`() {
        val transform = SafeContentTransform.create(
            surfaceWidth = 2400,
            surfaceHeight = 1080,
            insets = SafeAreaInsets(left = 120)
        )

        assertEquals(0.95f, transform.scale, 0.0001f)
        assertEquals(120f, transform.contentLeft, 0.0001f)
        assertEquals(27f, transform.contentTop, 0.0001f)
        assertEquals(2280f, transform.contentWidth, 0.0001f)
        assertEquals(1026f, transform.contentHeight, 0.0001f)
    }

    @Test
    fun `physical and logical coordinates round trip`() {
        val transform = SafeContentTransform.create(
            2560,
            1440,
            SafeAreaInsets(left = 96, right = 42, top = 18, bottom = 24)
        )
        val physical = transform.toPhysical(1234f, 678f)
        val logical = transform.toLogical(physical.x, physical.y)

        assertEquals(1234f, logical.x, 0.001f)
        assertEquals(678f, logical.y, 0.001f)
    }

    @Test
    fun `touches outside safe content clamp to logical edges`() {
        val transform = SafeContentTransform.create(
            1920,
            1080,
            SafeAreaInsets(left = 140, right = 80)
        )

        val before = transform.toLogical(0f, -100f)
        val after = transform.toLogical(5000f, 5000f)

        assertEquals(0f, before.x, 0f)
        assertEquals(0f, before.y, 0f)
        assertEquals(1920f, after.x, 0f)
        assertEquals(1080f, after.y, 0f)
    }

    @Test
    fun `pathological insets still produce finite positive content`() {
        val transform = SafeContentTransform.create(
            10,
            6,
            SafeAreaInsets(left = 100, top = 100, right = 100, bottom = 100)
        )

        assertTrue(transform.scale.isFinite())
        assertTrue(transform.scale > 0f)
        assertTrue(transform.contentWidth > 0f)
        assertTrue(transform.contentHeight > 0f)
        assertTrue(transform.contentLeft.isFinite())
        assertTrue(transform.contentTop.isFinite())
    }
}
