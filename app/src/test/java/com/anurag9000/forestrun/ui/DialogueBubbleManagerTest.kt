package com.anurag9000.forestrun.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DialogueBubbleManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        DialogueBubbleManager.clear()
        DialogueBubbleManager.init(context)
    }

    @Test
    fun `spawn variant rotates through authored options`() {
        repeat(4) {
            DialogueBubbleManager.spawnVariant(
                triggerKey = "pass_duck",
                textOptions = listOf("Clean read", "Kept the beat", "Past clean"),
                anchorX = 100f,
                anchorY = 100f
            )
        }

        assertEquals(
            listOf("Clean read", "Kept the beat", "Past clean", "Clean read"),
            DialogueBubbleManager.activeTextsForTest()
        )
    }

    @Test
    fun `variant options are trimmed and blank options are ignored`() {
        DialogueBubbleManager.spawnVariant(
            triggerKey = "  pass_cat  ",
            textOptions = listOf("  Quiet step  ", "   ", "Home again"),
            anchorX = 100f,
            anchorY = 100f
        )
        DialogueBubbleManager.spawnVariant(
            triggerKey = "pass_cat",
            textOptions = listOf("  Quiet step  ", "   ", "Home again"),
            anchorX = 100f,
            anchorY = 100f
        )

        assertEquals(listOf("Quiet step", "Home again"), DialogueBubbleManager.activeTextsForTest())
        assertEquals(1, DialogueBubbleManager.variantKeyCountForTest())
    }

    @Test
    fun `blank variant keys and empty options create no state`() {
        DialogueBubbleManager.spawnVariant("   ", listOf("Text"), 100f, 100f)
        DialogueBubbleManager.spawnVariant("valid", listOf("", "   "), 100f, 100f)

        assertTrue(DialogueBubbleManager.activeTextsForTest().isEmpty())
        assertEquals(0, DialogueBubbleManager.variantKeyCountForTest())
    }

    @Test
    fun `variant key history evicts oldest entries at a fixed cap`() {
        repeat(160) { index ->
            DialogueBubbleManager.spawnVariant(
                triggerKey = "dynamic_$index",
                textOptions = listOf("Line $index"),
                anchorX = 100f,
                anchorY = 100f
            )
        }

        assertEquals(128, DialogueBubbleManager.variantKeyCountForTest())
        assertEquals(5, DialogueBubbleManager.activeTextsForTest().size)
    }

    @Test
    fun `clear resets active bubbles and variant counters`() {
        DialogueBubbleManager.spawnVariant(
            triggerKey = "pass_line",
            textOptions = listOf("Held the line", "Stayed exact"),
            anchorX = 100f,
            anchorY = 100f
        )
        DialogueBubbleManager.clear()
        DialogueBubbleManager.spawnVariant(
            triggerKey = "pass_line",
            textOptions = listOf("Held the line", "Stayed exact"),
            anchorX = 100f,
            anchorY = 100f
        )

        assertEquals(listOf("Held the line"), DialogueBubbleManager.activeTextsForTest())
        assertEquals(1, DialogueBubbleManager.variantKeyCountForTest())
    }

    @Test
    fun `spawn variant respects bubble cap`() {
        repeat(8) { index ->
            DialogueBubbleManager.spawnVariant(
                triggerKey = "cap_test",
                textOptions = listOf("One", "Two", "Three"),
                anchorX = index.toFloat(),
                anchorY = 50f
            )
        }

        assertEquals(5, DialogueBubbleManager.activeTextsForTest().size)
        assertTrue(DialogueBubbleManager.activeTextsForTest().isNotEmpty())
    }

    @Test
    fun `long dialogue wraps to no more than three lines and marks truncation`() {
        val lines = DialogueBubbleManager.wrapTextForTest(
            text = List(80) { "forest" }.joinToString(" "),
            maxWidth = 60f
        )

        assertTrue(lines.isNotEmpty())
        assertTrue(lines.size <= 3)
        assertTrue(lines.all { it.isNotBlank() })
        assertTrue(lines.last().endsWith("…"))
    }

    @Test
    fun `oversized unbroken word is split safely`() {
        val lines = DialogueBubbleManager.wrapTextForTest(
            text = "supercalifragilistic".repeat(20),
            maxWidth = 40f
        )

        assertTrue(lines.isNotEmpty())
        assertTrue(lines.size <= 3)
        assertTrue(lines.all { it.isNotBlank() })
        assertTrue(lines.last().endsWith("…"))
    }

    @Test
    fun `invalid wrap width still returns a bounded nonblank result`() {
        listOf(-1f, 0f, Float.NaN, Float.POSITIVE_INFINITY).forEach { invalid ->
            val lines = DialogueBubbleManager.wrapTextForTest("A very long forest sentence", invalid)
            assertTrue(lines.isNotEmpty())
            assertTrue(lines.size <= 3)
            assertTrue(lines.all { it.isNotBlank() })
        }
    }

    @Test
    fun `huge finite update expires all bubbles without nonfinite residue`() {
        DialogueBubbleManager.spawn("Transient", 100f, 100f)

        DialogueBubbleManager.update(Float.MAX_VALUE)

        assertTrue(DialogueBubbleManager.activeTextsForTest().isEmpty())
    }

    @Test
    fun `draw reuses the line width measured at spawn`() {
        DialogueBubbleManager.spawn(
            text = "A gentle crossing remembered by the whole forest",
            anchorX = 320f,
            anchorY = 220f
        )
        val measurementsAfterSpawn = DialogueBubbleManager.lineMeasurementCountForTest
        assertTrue(measurementsAfterSpawn > 0)

        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        repeat(12) { DialogueBubbleManager.draw(canvas) }

        assertEquals(
            measurementsAfterSpawn,
            DialogueBubbleManager.lineMeasurementCountForTest
        )
    }

    @Test
    fun `tiny canvas draw remains bounded and does not throw`() {
        DialogueBubbleManager.spawn(
            text = "Tiny viewport",
            anchorX = 1_000f,
            anchorY = -1_000f
        )
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)

        DialogueBubbleManager.draw(Canvas(bitmap))

        assertEquals(1, DialogueBubbleManager.activeTextsForTest().size)
    }
}
