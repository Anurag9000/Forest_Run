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
    fun `long dialogue wraps to no more than three lines`() {
        val lines = DialogueBubbleManager.wrapTextForTest(
            text = "The forest remembers every gentle crossing and every hurried mistake you bring home.",
            maxWidth = 120f
        )

        assertTrue(lines.isNotEmpty())
        assertTrue(lines.size <= 3)
        assertTrue(lines.all { it.isNotBlank() })
    }

    @Test
    fun `oversized unbroken word is split safely`() {
        val lines = DialogueBubbleManager.wrapTextForTest(
            text = "supercalifragilisticexpialidocioussupercalifragilisticexpialidocious",
            maxWidth = 70f
        )

        assertTrue(lines.isNotEmpty())
        assertTrue(lines.size <= 3)
        assertTrue(lines.all { it.isNotBlank() })
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
}
