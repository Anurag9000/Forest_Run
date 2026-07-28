#!/usr/bin/env python3
"""Use pure hit arithmetic and Robolectric-backed RectF construction in tests."""

from pathlib import Path

panel_path = Path("app/src/main/java/com/anurag9000/forestrun/ui/FeedbackSettingsPanel.kt")
panel = panel_path.read_text(encoding="utf-8")
old_hit_test = '''    fun hitTest(layout: FeedbackSettingsLayout, x: Float, y: Float): FeedbackToggle? = when {
        layout.reducedMotion.contains(x, y) -> FeedbackToggle.REDUCED_MOTION
        layout.audio.contains(x, y) -> FeedbackToggle.AUDIO
        layout.haptics.contains(x, y) -> FeedbackToggle.HAPTICS
        else -> null
    }
'''
new_hit_test = '''    fun hitTest(layout: FeedbackSettingsLayout, x: Float, y: Float): FeedbackToggle? = when {
        contains(layout.reducedMotion, x, y) -> FeedbackToggle.REDUCED_MOTION
        contains(layout.audio, x, y) -> FeedbackToggle.AUDIO
        contains(layout.haptics, x, y) -> FeedbackToggle.HAPTICS
        else -> null
    }

    private fun contains(rect: RectF, x: Float, y: Float): Boolean =
        x >= rect.left && x < rect.right && y >= rect.top && y < rect.bottom
'''
if panel.count(old_hit_test) != 1:
    raise RuntimeError("FeedbackSettingsPanel hitTest did not match")
panel_path.write_text(panel.replace(old_hit_test, new_hit_test, 1), encoding="utf-8")

path = Path("app/src/test/java/com/anurag9000/forestrun/ui/FeedbackSettingsPanelLayoutTest.kt")
text = path.read_text(encoding="utf-8")
text = text.replace(
    "import org.junit.Test\n",
    "import org.junit.Test\nimport org.junit.runner.RunWith\nimport org.robolectric.RobolectricTestRunner\n",
    1,
)
text = text.replace(
    "class FeedbackSettingsPanelLayoutTest {",
    "@RunWith(RobolectricTestRunner::class)\nclass FeedbackSettingsPanelLayoutTest {",
    1,
)
text = text.replace(
    "assertFalse(android.graphics.RectF.intersects(layout.all[left], layout.all[right]))",
    "assertFalse(overlaps(layout.all[left], layout.all[right]))",
)
text = text.replace(
    "FeedbackSettingsPanelLayout.hitTest(layout, layout.reducedMotion.centerX(), layout.reducedMotion.centerY())",
    "FeedbackSettingsPanelLayout.hitTest(layout, centerX(layout.reducedMotion), centerY(layout.reducedMotion))",
)
text = text.replace(
    "FeedbackSettingsPanelLayout.hitTest(layout, layout.audio.centerX(), layout.audio.centerY())",
    "FeedbackSettingsPanelLayout.hitTest(layout, centerX(layout.audio), centerY(layout.audio))",
)
text = text.replace(
    "FeedbackSettingsPanelLayout.hitTest(layout, layout.haptics.centerX(), layout.haptics.centerY())",
    "FeedbackSettingsPanelLayout.hitTest(layout, centerX(layout.haptics), centerY(layout.haptics))",
)
needle = """        assertEquals(null, FeedbackSettingsPanelLayout.hitTest(layout, 10f, 10f))
    }
}
"""
replacement = """        assertEquals(null, FeedbackSettingsPanelLayout.hitTest(layout, 10f, 10f))
    }

    private fun centerX(rect: android.graphics.RectF): Float = (rect.left + rect.right) / 2f

    private fun centerY(rect: android.graphics.RectF): Float = (rect.top + rect.bottom) / 2f

    private fun overlaps(left: android.graphics.RectF, right: android.graphics.RectF): Boolean =
        left.left < right.right && left.right > right.left &&
            left.top < right.bottom && left.bottom > right.top
}
"""
if needle not in text:
    raise RuntimeError("FeedbackSettingsPanelLayoutTest tail did not match")
path.write_text(text.replace(needle, replacement, 1), encoding="utf-8")
