package com.anurag9000.forestrun.ui

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeedbackSettingsPanelLayoutTest {
    @Test
    fun `settings chips remain inside and non-overlapping across supported landscapes`() {
        for ((width, height) in listOf(1280f to 720f, 1920f to 1080f, 2560f to 1440f, 2400f to 1080f)) {
            val layout = FeedbackSettingsPanelLayout.build(width, height)
            for (rect in layout.all) {
                assertTrue(rect.left >= 0f)
                assertTrue(rect.top >= 0f)
                assertTrue(rect.right <= width)
                assertTrue(rect.bottom <= height)
                assertTrue(rect.left < rect.right)
                assertTrue(rect.top < rect.bottom)
            }
            for (left in layout.all.indices) {
                for (right in left + 1 until layout.all.size) {
                    assertFalse(overlaps(layout.all[left], layout.all[right]))
                }
            }
        }
    }

    @Test
    fun `each chip maps to exactly one setting`() {
        val layout = FeedbackSettingsPanelLayout.build(1920f, 1080f)
        assertEquals(
            FeedbackToggle.REDUCED_MOTION,
            FeedbackSettingsPanelLayout.hitTest(layout, centerX(layout.reducedMotion), centerY(layout.reducedMotion))
        )
        assertEquals(
            FeedbackToggle.AUDIO,
            FeedbackSettingsPanelLayout.hitTest(layout, centerX(layout.audio), centerY(layout.audio))
        )
        assertEquals(
            FeedbackToggle.HAPTICS,
            FeedbackSettingsPanelLayout.hitTest(layout, centerX(layout.haptics), centerY(layout.haptics))
        )
        assertNull(FeedbackSettingsPanelLayout.hitTest(layout, 10f, 10f))
    }

    @Test
    fun `non finite and non positive surfaces are rejected`() {
        val malformed = listOf(
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            0f,
            -1f
        )
        malformed.forEach { value ->
            assertRejected { FeedbackSettingsPanelLayout.build(value, 1080f) }
            assertRejected { FeedbackSettingsPanelLayout.build(1920f, value) }
        }
    }

    @Test
    fun `surfaces too small for three controls are rejected before drawing`() {
        assertRejected { FeedbackSettingsPanelLayout.build(100f, 720f) }
        assertRejected { FeedbackSettingsPanelLayout.build(1280f, 100f) }

        val minimumLayout = FeedbackSettingsPanelLayout.build(228f, 146f)
        minimumLayout.all.forEach { rect ->
            assertTrue(rect.left >= 0f)
            assertTrue(rect.top >= 0f)
            assertTrue(rect.right <= 228f)
            assertTrue(rect.bottom <= 146f)
        }
    }

    @Test
    fun `non finite taps fail closed`() {
        val layout = FeedbackSettingsPanelLayout.build(1920f, 1080f)
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { malformed ->
            assertNull(FeedbackSettingsPanelLayout.hitTest(layout, malformed, centerY(layout.audio)))
            assertNull(FeedbackSettingsPanelLayout.hitTest(layout, centerX(layout.audio), malformed))
        }
    }

    @Test
    fun `malformed or externally mutated rectangles cannot toggle settings`() {
        val malformedLayouts = listOf(
            FeedbackSettingsLayout(
                reducedMotion = RectF(Float.NaN, 0f, 20f, 20f),
                audio = RectF(),
                haptics = RectF()
            ),
            FeedbackSettingsLayout(
                reducedMotion = RectF(20f, 0f, 10f, 20f),
                audio = RectF(),
                haptics = RectF()
            ),
            FeedbackSettingsLayout(
                reducedMotion = RectF(0f, 10f, 20f, 10f),
                audio = RectF(),
                haptics = RectF()
            )
        )

        malformedLayouts.forEach { layout ->
            assertNull(FeedbackSettingsPanelLayout.hitTest(layout, 5f, 5f))
        }

        val mutableLayout = FeedbackSettingsPanelLayout.build(1920f, 1080f)
        mutableLayout.audio.set(Float.NEGATIVE_INFINITY, 0f, Float.POSITIVE_INFINITY, 100f)
        assertNull(FeedbackSettingsPanelLayout.hitTest(mutableLayout, 50f, 50f))
    }

    private fun centerX(rect: RectF): Float = (rect.left + rect.right) / 2f

    private fun centerY(rect: RectF): Float = (rect.top + rect.bottom) / 2f

    private fun overlaps(left: RectF, right: RectF): Boolean =
        left.left < right.right && left.right > right.left &&
            left.top < right.bottom && left.bottom > right.top

    private fun assertRejected(block: () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Expected malformed feedback layout input to be rejected.", rejected)
    }
}
