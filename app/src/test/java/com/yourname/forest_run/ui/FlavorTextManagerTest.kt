package com.yourname.forest_run.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FlavorTextManagerTest {
    @After
    fun tearDown() {
        FlavorTextManager.clear()
    }

    @Test
    fun `active flavor text is bounded`() {
        repeat(40) { index ->
            FlavorTextManager.spawn(
                text = "message-$index",
                x = index * 100f,
                y = 100f
            )
        }

        assertEquals(16, FlavorTextManager.activeCountForTest())
        assertEquals("message-24", FlavorTextManager.activeTextsForTest().first())
        assertEquals("message-39", FlavorTextManager.activeTextsForTest().last())
    }

    @Test
    fun `nearby duplicate refreshes instead of allocating`() {
        FlavorTextManager.spawn("Mercy", 100f, 100f)
        FlavorTextManager.spawn("Mercy", 120f, 118f)

        assertEquals(1, FlavorTextManager.activeCountForTest())
        assertEquals(listOf("Mercy"), FlavorTextManager.activeTextsForTest())
    }

    @Test
    fun `invalid entries are rejected and values are sanitized`() {
        FlavorTextManager.spawn("   ", 100f, 100f)
        FlavorTextManager.spawn("valid", Float.NaN, 100f)
        FlavorTextManager.spawn("valid", 100f, Float.POSITIVE_INFINITY)

        assertEquals(0, FlavorTextManager.activeCountForTest())

        FlavorTextManager.spawn("x".repeat(100), 100f, 100f, lifetime = -4f, size = 900f)
        assertEquals(1, FlavorTextManager.activeCountForTest())
        assertTrue(FlavorTextManager.activeTextsForTest().single().length <= 72)
    }
}
