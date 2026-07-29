#!/usr/bin/env python3
"Keep the shader-cache test independent of Robolectric's negative-X bitmap bug."

from pathlib import Path

path = Path("app/src/test/java/com/anurag9000/forestrun/engine/RenderHotPathReuseTest.kt")
text = path.read_text(encoding="utf-8")
old = '''        background.update(1f / 60f, GameConstants.BASE_SCROLL_SPEED)
        background.draw(canvas)
        val firstCount = background.dynamicShaderRebuildCountForTest
        assertTrue(firstCount > 0)

        repeat(12) {
            background.update(1f / 60f, GameConstants.BASE_SCROLL_SPEED)
            background.draw(canvas)
        }
'''
new = '''        background.draw(canvas)
        val firstCount = background.dynamicShaderRebuildCountForTest
        assertTrue(firstCount > 0)

        repeat(12) {
            background.draw(canvas)
        }
'''
count = text.count(old)
if count != 1:
    raise RuntimeError(f"expected one shader-cache test block, found {count}")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
