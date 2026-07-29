#!/usr/bin/env python3
"Test the real Parallax shader cache without Robolectric bitmap rasterization."

from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


parallax = Path(
    "app/src/main/java/com/anurag9000/forestrun/engine/ParallaxBackground.kt"
)
replace_once(
    parallax,
    '''    private fun quantizedRgb(color: Int): Int = Color.rgb(
        Color.red(color) and 0xF8,
        Color.green(color) and 0xF8,
        Color.blue(color) and 0xF8
    )
''',
    '''    /** Exercise the production shader cache without rasterizing Parallax bitmaps. */
    internal fun refreshDynamicShadersForTest(
        skyTop: Int,
        skyBottom: Int,
        nightFactor: Float,
        bloomStrength: Float
    ) {
        val lighting = resolveRunLightingIdentity(
            target = reusableLighting,
            nightFactor = nightFactor,
            bloomStrength = bloomStrength
        )
        ensureDynamicAtmosphereShaders(skyTop, skyBottom, lighting)
    }

    private fun quantizedRgb(color: Int): Int = Color.rgb(
        Color.red(color) and 0xF8,
        Color.green(color) and 0xF8,
        Color.blue(color) and 0xF8
    )
''',
    "test-only shader-cache entry point",
)

test = Path(
    "app/src/test/java/com/anurag9000/forestrun/engine/RenderHotPathReuseTest.kt"
)
replace_once(
    test,
    '''import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
''',
    '''import android.graphics.Color
''',
    "remove Robolectric bitmap imports",
)
replace_once(
    test,
    '''    @Test
    fun `parallax shaders stay stable across unchanged frames`() {
        val background = ParallaxBackground(screenWidth = 320, screenHeight = 180)
        background.applyBiomeColours(
            skyTop = Color.rgb(150, 200, 245),
            skyBottom = Color.rgb(220, 238, 252),
            groundColour = Color.rgb(72, 142, 70),
            foliage = Color.rgb(42, 104, 58)
        )
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        background.update(1f / 60f, GameConstants.BASE_SCROLL_SPEED)
        background.draw(canvas)
        val firstCount = background.dynamicShaderRebuildCountForTest
        assertTrue(firstCount > 0)

        repeat(12) {
            background.update(1f / 60f, GameConstants.BASE_SCROLL_SPEED)
            background.draw(canvas)
        }
        assertEquals(firstCount, background.dynamicShaderRebuildCountForTest)

        background.applyBiomeColours(
            skyTop = Color.rgb(30, 42, 72),
            skyBottom = Color.rgb(68, 76, 106),
            groundColour = Color.rgb(56, 88, 58),
            foliage = Color.rgb(18, 52, 34)
        )
        background.draw(canvas)
        assertTrue(background.dynamicShaderRebuildCountForTest > firstCount)
    }
''',
    '''    @Test
    fun `parallax shaders stay stable across unchanged frames`() {
        val background = ParallaxBackground(screenWidth = 320, screenHeight = 180)
        val brightTop = Color.rgb(150, 200, 245)
        val brightBottom = Color.rgb(220, 238, 252)

        background.refreshDynamicShadersForTest(
            skyTop = brightTop,
            skyBottom = brightBottom,
            nightFactor = 0.15f,
            bloomStrength = 0f
        )
        val firstCount = background.dynamicShaderRebuildCountForTest
        assertTrue(firstCount > 0)

        repeat(12) {
            background.refreshDynamicShadersForTest(
                skyTop = brightTop,
                skyBottom = brightBottom,
                nightFactor = 0.15f,
                bloomStrength = 0f
            )
        }
        assertEquals(firstCount, background.dynamicShaderRebuildCountForTest)

        background.refreshDynamicShadersForTest(
            skyTop = Color.rgb(30, 42, 72),
            skyBottom = Color.rgb(68, 76, 106),
            nightFactor = 0.75f,
            bloomStrength = 0.8f
        )
        assertTrue(background.dynamicShaderRebuildCountForTest > firstCount)
    }
''',
    "shader-cache test without bitmap drawing",
)
