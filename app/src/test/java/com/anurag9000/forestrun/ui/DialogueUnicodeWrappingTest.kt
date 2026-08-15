package com.anurag9000.forestrun.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DialogueUnicodeWrappingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        DialogueBubbleManager.clear()
        DialogueBubbleManager.init(context)
    }

    @Test
    fun `oversized single grapheme is never split even when wider than the line`() {
        listOf(
            "😀",
            "e\u0301",
            "👍🏽",
            "👩‍👩‍👧‍👦",
            "🇮🇳",
            "✈\uFE0F"
        ).forEach { grapheme ->
            val lines = DialogueBubbleManager.wrapTextForTest(grapheme, 1f)
            assertEquals("grapheme=$grapheme", listOf(grapheme), lines)
        }
    }

    @Test
    fun `truncation removes whole graphemes rather than UTF-16 code units`() {
        val text = "👨🏾‍🌾".repeat(80)
        val lines = DialogueBubbleManager.wrapTextForTest(text, 44f)

        assertTrue(lines.isNotEmpty())
        assertTrue(lines.size <= 3)
        assertTrue(lines.last().endsWith("…"))
        lines.forEach(::assertValidUtf16)

        val retained = lines.joinToString(separator = "").removeSuffix("…")
        assertTrue(text.startsWith(retained))
        assertEquals(
            retained,
            GraphemeClusters.split(retained).joinToString(separator = "")
        )
    }

    @Test
    fun `expanded multilingual copy remains bounded without malformed text`() {
        val text = List(24) {
            "वन की यादें e\u0301lan में रहती हैं 👩‍🌾🌱"
        }.joinToString(" ")

        val lines = DialogueBubbleManager.wrapTextForTest(text, 120f)

        assertTrue(lines.isNotEmpty())
        assertTrue(lines.size <= 3)
        assertTrue(lines.all { it.isNotBlank() })
        lines.forEach(::assertValidUtf16)
    }

    private fun assertValidUtf16(text: String) {
        var index = 0
        while (index < text.length) {
            val character = text[index]
            if (character.isHighSurrogate()) {
                assertTrue(index + 1 < text.length)
                assertTrue(text[index + 1].isLowSurrogate())
                index += 2
            } else {
                assertFalse(character.isLowSurrogate())
                index += 1
            }
        }
    }
}
