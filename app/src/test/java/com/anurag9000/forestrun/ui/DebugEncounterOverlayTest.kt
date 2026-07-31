package com.anurag9000.forestrun.ui

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DebugEncounterOverlayTest {
    @Test
    fun `non finite taps fail closed`() {
        val overlay = DebugEncounterOverlay(1920)

        listOf(
            Float.NaN to 120f,
            120f to Float.NaN,
            Float.POSITIVE_INFINITY to 120f,
            120f to Float.NEGATIVE_INFINITY
        ).forEach { (x, y) ->
            assertNull(overlay.handleTap(x, y))
        }
    }

    @Test
    fun `button centres map to their authored actions`() {
        val overlay = DebugEncounterOverlay(1920)
        overlay.handleTap(-1f, -1f)

        assertEquals(
            DebugOverlayAction.PREVIOUS,
            overlay.handleTap(centerX(overlay, "prevRect"), centerY(overlay, "prevRect"))
        )
        assertEquals(
            DebugOverlayAction.TOGGLE_RUN,
            overlay.handleTap(centerX(overlay, "toggleRect"), centerY(overlay, "toggleRect"))
        )
        assertEquals(
            DebugOverlayAction.NEXT,
            overlay.handleTap(centerX(overlay, "nextRect"), centerY(overlay, "nextRect"))
        )
    }

    @Test
    fun `non positive width still produces ordered contained controls`() {
        val overlay = DebugEncounterOverlay(Int.MIN_VALUE)
        overlay.handleTap(-1f, -1f)

        val panel = rect(overlay, "panelRect")
        val previous = rect(overlay, "prevRect")
        val toggle = rect(overlay, "toggleRect")
        val next = rect(overlay, "nextRect")

        listOf(panel, previous, toggle, next).forEach { value ->
            assertTrue(value.left.isFinite())
            assertTrue(value.top.isFinite())
            assertTrue(value.right.isFinite())
            assertTrue(value.bottom.isFinite())
            assertTrue(value.width() > 0f)
            assertTrue(value.height() > 0f)
        }
        assertTrue(previous.left >= panel.left)
        assertTrue(previous.right < toggle.left)
        assertTrue(toggle.right < next.left)
        assertTrue(next.right <= panel.right)
    }

    @Test
    fun `extreme width is capped before layout arithmetic`() {
        val overlay = DebugEncounterOverlay(Int.MAX_VALUE)

        assertEquals(16_384f, floatField(overlay, "layoutWidth"), 0f)
    }

    private fun centerX(overlay: DebugEncounterOverlay, fieldName: String): Float =
        rect(overlay, fieldName).centerX()

    private fun centerY(overlay: DebugEncounterOverlay, fieldName: String): Float =
        rect(overlay, fieldName).centerY()

    private fun rect(overlay: DebugEncounterOverlay, name: String): RectF {
        val field = DebugEncounterOverlay::class.java.getDeclaredField(name)
        field.isAccessible = true
        return RectF(field.get(overlay) as RectF)
    }

    private fun floatField(overlay: DebugEncounterOverlay, name: String): Float {
        val field = DebugEncounterOverlay::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getFloat(overlay)
    }
}
