package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BloomPresentationTest {

    @Test
    fun `ready bloom presentation highlights one-seed-away state`() {
        val presentation = BloomPresentation.hudPresentation(
            bloomMeter = 7,
            seedTarget = 8,
            isActive = false,
            secondsRemaining = 0f,
            totalConversions = 0,
            burstConversions = 0,
            recentAfterglow = 0f
        )

        assertEquals(BloomPresentationMode.READY, presentation.mode)
        assertEquals("READY", presentation.labelText)
        assertTrue(presentation.statusText.contains("1 more seed", ignoreCase = true))
    }

    @Test
    fun `active bloom presentation prioritizes countdown and conversions`() {
        val presentation = BloomPresentation.hudPresentation(
            bloomMeter = 0,
            seedTarget = 8,
            isActive = true,
            secondsRemaining = 4.3f,
            totalConversions = 3,
            burstConversions = 0,
            recentAfterglow = 0f
        )

        assertEquals(BloomPresentationMode.ACTIVE, presentation.mode)
        assertEquals("BLOOM", presentation.labelText)
        assertTrue(presentation.statusText.contains("4.3s"))
        assertTrue(presentation.statusText.contains("3 converts"))
    }

    @Test
    fun `afterglow presentation keeps burst result visible after bloom ends`() {
        val presentation = BloomPresentation.hudPresentation(
            bloomMeter = 0,
            seedTarget = 8,
            isActive = false,
            secondsRemaining = 0f,
            totalConversions = 3,
            burstConversions = 2,
            recentAfterglow = 0.7f
        )

        assertEquals(BloomPresentationMode.AFTERGLOW, presentation.mode)
        assertEquals("AFTERGLOW", presentation.labelText)
        assertTrue(presentation.statusText.contains("2 converts"))
        assertTrue(presentation.statusText.contains("light", ignoreCase = true))
    }
}
