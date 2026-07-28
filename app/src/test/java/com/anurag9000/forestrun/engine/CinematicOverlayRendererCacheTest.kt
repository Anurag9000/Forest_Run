package com.anurag9000.forestrun.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CinematicOverlayRendererCacheTest {

    @Test
    fun `profile and shimmer changes reuse shaders when geometry and color are stable`() {
        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val renderer = CinematicOverlayRenderer()

        renderer.draw(
            canvas = canvas,
            width = 640f,
            height = 360f,
            profile = buildCinematicPolishProfile(CinematicScene.RUN, emphasis = 0.2f),
            elapsedSeconds = 0f,
            glowColor = Color.rgb(240, 210, 150),
            centerYFraction = 0.47f
        )
        assertEquals(1, renderer.shaderRebuildCountForTest)

        repeat(8) { frame ->
            renderer.draw(
                canvas = canvas,
                width = 640f,
                height = 360f,
                profile = buildCinematicPolishProfile(
                    CinematicScene.RUN,
                    emphasis = frame / 8f,
                    bloomStrength = if (frame % 2 == 0) 0f else 1f
                ),
                elapsedSeconds = frame * 0.17f,
                glowColor = Color.rgb(240, 210, 150),
                centerYFraction = 0.47f
            )
        }

        assertEquals(1, renderer.shaderRebuildCountForTest)
    }

    @Test
    fun `shader cache rebuilds only when a shader input changes`() {
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val renderer = CinematicOverlayRenderer()
        val profile = buildCinematicPolishProfile(CinematicScene.MENU)

        renderer.draw(canvas, 320f, 180f, profile, 0f, Color.YELLOW, 0.44f)
        renderer.draw(canvas, 320f, 180f, profile, 1f, Color.CYAN, 0.44f)
        renderer.draw(canvas, 320f, 180f, profile, 2f, Color.CYAN, 0.50f)
        renderer.draw(canvas, 640f, 180f, profile, 3f, Color.CYAN, 0.50f)

        assertEquals(4, renderer.shaderRebuildCountForTest)
    }

    @Test
    fun `fixed sanctuary lighting identities are reused`() {
        assertSame(
            buildSanctuaryLightingIdentity(SanctuaryLightingScene.MENU),
            buildSanctuaryLightingIdentity(SanctuaryLightingScene.MENU)
        )
        assertSame(
            buildSanctuaryLightingIdentity(SanctuaryLightingScene.GARDEN),
            buildSanctuaryLightingIdentity(SanctuaryLightingScene.GARDEN)
        )
        assertSame(
            buildSanctuaryLightingIdentity(SanctuaryLightingScene.REST),
            buildSanctuaryLightingIdentity(SanctuaryLightingScene.REST)
        )
    }
}
