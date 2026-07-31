package com.anurag9000.forestrun.ui

import android.graphics.Color
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
    fun `nearby duplicate refreshes and adopts newest style instead of allocating`() {
        FlavorTextManager.spawn(
            text = "Mercy",
            x = 100f,
            y = 100f,
            colour = Color.RED,
            lifetime = 1f,
            size = 20f
        )
        FlavorTextManager.update(0.75f)
        FlavorTextManager.spawn(
            text = "Mercy",
            x = 120f,
            y = 118f,
            colour = Color.BLUE,
            lifetime = 3f,
            size = 44f
        )

        assertEquals(1, FlavorTextManager.activeCountForTest())
        assertEquals(listOf("Mercy"), FlavorTextManager.activeTextsForTest())
        assertEquals(listOf(3f), FlavorTextManager.activeLifetimesForTest())
        assertEquals(listOf(44f), FlavorTextManager.activeSizesForTest())
        assertEquals(listOf(Color.BLUE), FlavorTextManager.activeColoursForTest())

        FlavorTextManager.update(1f)
        assertEquals(1, FlavorTextManager.activeCountForTest())
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
        assertEquals(0.15f, FlavorTextManager.activeLifetimesForTest().single(), 0f)
        assertEquals(72f, FlavorTextManager.activeSizesForTest().single(), 0f)
    }

    @Test
    fun `non finite lifetime and size use finite defaults instead of immortal NaN state`() {
        FlavorTextManager.spawn(
            text = "Finite",
            x = 100f,
            y = 100f,
            lifetime = Float.NaN,
            size = Float.POSITIVE_INFINITY
        )

        assertEquals(1.4f, FlavorTextManager.activeLifetimesForTest().single(), 0f)
        assertEquals(30f, FlavorTextManager.activeSizesForTest().single(), 0f)

        FlavorTextManager.update(1.4f)
        assertEquals(0, FlavorTextManager.activeCountForTest())
    }

    @Test
    fun `invalid update deltas are no ops`() {
        FlavorTextManager.spawn("Stable", 100f, 100f)

        FlavorTextManager.update(Float.NaN)
        FlavorTextManager.update(Float.POSITIVE_INFINITY)
        FlavorTextManager.update(-1f)

        assertEquals(1, FlavorTextManager.activeCountForTest())
    }

    @Test
    fun `huge finite update expires every entry without residue`() {
        repeat(4) { index -> FlavorTextManager.spawn("entry-$index", index * 100f, 100f) }

        FlavorTextManager.update(Float.MAX_VALUE)

        assertEquals(0, FlavorTextManager.activeCountForTest())
    }
}
