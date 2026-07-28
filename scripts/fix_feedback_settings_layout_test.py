#!/usr/bin/env python3
"""Replace Android RectF helper calls in the generated layout test with pure arithmetic."""

from pathlib import Path

path = Path("app/src/test/java/com/anurag9000/forestrun/ui/FeedbackSettingsPanelLayoutTest.kt")
text = path.read_text(encoding="utf-8")
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
