package com.anurag9000.forestrun.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(null, FeedbackSettingsPanelLayout.hitTest(layout, 10f, 10f))
    }

    private fun centerX(rect: android.graphics.RectF): Float = (rect.left + rect.right) / 2f

    private fun centerY(rect: android.graphics.RectF): Float = (rect.top + rect.bottom) / 2f

    private fun overlaps(left: android.graphics.RectF, right: android.graphics.RectF): Boolean =
        left.left < right.right && left.right > right.left &&
            left.top < right.bottom && left.bottom > right.top
}
