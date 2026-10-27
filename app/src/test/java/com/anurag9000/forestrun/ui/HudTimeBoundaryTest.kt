package com.anurag9000.forestrun.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.BloomPresentation
import com.anurag9000.forestrun.engine.GameConstants
import com.anurag9000.forestrun.engine.OpeningInputState
import com.anurag9000.forestrun.engine.OpeningReadabilityGuide
import com.anurag9000.forestrun.engine.PacifistRouteTier
import com.anurag9000.forestrun.engine.SaveManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HudTimeBoundaryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `invalid deltas leave HUD animation state unchanged`() {
        val state = GameStateManager(context)
        state.collectSeed()
        val hud = HUD(context, 1920, 1080)

        listOf(
            0f,
            -1f,
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        ).forEach { hud.update(it, state) }

        assertEquals(0f, field(hud, "displayedFill"), 0f)
        assertEquals(0f, field(hud, "bloomPulse"), 0f)
        assertEquals(0f, field(hud, "newBadge"), 0f)
    }

    @Test
    fun `large lifecycle delta is capped to one render-frame budget`() {
        val state = GameStateManager(context)
        state.collectSeed()
        val hud = HUD(context, 1920, 1080)

        hud.update(10f, state)

        assertEquals(0.4f, field(hud, "displayedFill"), 0.0001f)
    }

    @Test
    fun `valid update repairs poisoned animation fields`() {
        val state = GameStateManager(context)
        state.debugActivateBloom()
        state.addBonus(points = 1)
        val hud = HUD(context, 1920, 1080)
        setField(hud, "displayedFill", Float.NaN)
        setField(hud, "bloomPulse", Float.NaN)
        setField(hud, "newBadge", Float.NaN)

        hud.update(0.016f, state)

        assertTrue(field(hud, "displayedFill").isFinite())
        assertTrue(field(hud, "bloomPulse").isFinite())
        assertTrue(field(hud, "newBadge").isFinite())
    }

    @Test
    fun `inactive presentation pulses reset deterministically`() {
        val state = GameStateManager(context)
        val hud = HUD(context, 1920, 1080)
        setField(hud, "bloomPulse", 2f)
        setField(hud, "newBadge", 3f)

        hud.update(0.016f, state)

        assertEquals(0f, field(hud, "bloomPulse"), 0f)
        assertEquals(0f, field(hud, "newBadge"), 0f)
    }

    @Test
    fun `compact HUD keeps mercy hearts below the seed label`() {
        val state = GameStateManager(context)
        val hud = HUD(context, 720, 540)
        val bitmap = Bitmap.createBitmap(720, 540, Bitmap.Config.ARGB_8888)
        try {
            hud.draw(
                Canvas(bitmap),
                state,
                BloomPresentation.hudPresentation(
                    bloomMeter = state.bloomMeter,
                    seedTarget = GameConstants.BLOOM_SEED_COUNT,
                    isActive = state.isBloomActive,
                    secondsRemaining = state.bloomSecondsRemaining,
                    totalConversions = state.bloomConversionsThisRun,
                    burstConversions = 0,
                    recentAfterglow = 0f
                )
            )

            var redHeartPixelsInSeedLabel = 0
            for (y in 54 until 78) {
                for (x in 60 until 180) {
                    val color = bitmap.getPixel(x, y)
                    if (
                        Color.red(color) > 60 &&
                        Color.red(color) > Color.green(color) * 1.35f &&
                        Color.red(color) > Color.blue(color) * 1.2f
                    ) {
                        redHeartPixelsInSeedLabel++
                    }
                }
            }
            assertEquals("heart glyphs must not paint over the seed label", 0, redHeartPixelsInSeedLabel)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun `every opening guidance line wraps within the compact HUD panel`() {
        val cues = listOf(
            OpeningReadabilityGuide.cueFor(0f, OpeningInputState(), PacifistRouteTier.NONE, 0, 0),
            OpeningReadabilityGuide.cueFor(
                1f, OpeningInputState(duckSeen = true), PacifistRouteTier.NONE, 0, 0
            ),
            OpeningReadabilityGuide.cueFor(
                4f, OpeningInputState(duckSeen = true), PacifistRouteTier.NONE, 0, 0
            ),
            OpeningReadabilityGuide.cueFor(
                5f, OpeningInputState(jumpSeen = true, duckSeen = true), PacifistRouteTier.NONE, 0, 0
            ),
            OpeningReadabilityGuide.cueFor(
                11f, OpeningInputState(jumpSeen = true, holdSeen = true), PacifistRouteTier.NONE, 0, 0
            ),
            OpeningReadabilityGuide.cueFor(
                12f, OpeningInputState(true, true, true), PacifistRouteTier.PEACEFUL, 0, 0
            ),
            OpeningReadabilityGuide.cueFor(
                12f, OpeningInputState(true, true, true), PacifistRouteTier.NONE, 1, 0
            ),
            OpeningReadabilityGuide.cueFor(
                12f, OpeningInputState(true, true, true), PacifistRouteTier.NONE, 0, 0
            )
        ).map { requireNotNull(it) }.distinctBy { it.line }

        val hud = HUD(context, 720, 540)
        val textWidth = hud.openingGuidanceTextWidthForTest()
        cues.forEach { cue ->
            val lines = hud.openingGuidanceLinesForTest(cue.line, textWidth)
            assertTrue("line wraps to at most two rows: ${cue.line}", lines.size in 1..2)
            assertEquals(cue.line.trim().split(Regex("\\s+")).joinToString(" "), lines.joinToString(" "))
            assertTrue("line fits panel: ${cue.line}", hud.openingGuidanceLineFitsForTest(cue.line, textWidth))
        }
        assertEquals("all authored lines are covered", 8, cues.size)
    }

    private fun field(hud: HUD, name: String): Float {
        val field = HUD::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getFloat(hud)
    }

    private fun setField(hud: HUD, name: String, value: Float) {
        val field = HUD::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.setFloat(hud, value)
    }
}
