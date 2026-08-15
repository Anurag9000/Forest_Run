package com.anurag9000.forestrun.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphemeClustersTest {
    @Test
    fun `surrogate pairs combining marks and variation selectors stay intact`() {
        val text = "A😀e\u0301✈\uFE0F"

        assertEquals(
            listOf("A", "😀", "e\u0301", "✈\uFE0F"),
            GraphemeClusters.split(text)
        )
    }

    @Test
    fun `emoji modifiers and ZWJ families stay intact`() {
        val text = "👍🏽👩‍👩‍👧‍👦"

        assertEquals(
            listOf("👍🏽", "👩‍👩‍👧‍👦"),
            GraphemeClusters.split(text)
        )
    }

    @Test
    fun `regional indicators are paired instead of split individually`() {
        assertEquals(
            listOf("🇮🇳", "🇯🇵", "🇺"),
            GraphemeClusters.split("🇮🇳🇯🇵🇺")
        )
    }

    @Test
    fun `CRLF remains one boundary and empty input stays empty`() {
        assertEquals(listOf("\r\n", "x"), GraphemeClusters.split("\r\nx"))
        assertTrue(GraphemeClusters.split("").isEmpty())
    }

    @Test
    fun `every emitted cluster is valid UTF-16 and recomposes exactly`() {
        val input = "वन🌲 e\u0301lan 👨🏾‍🌾 🇮🇳"
        val clusters = GraphemeClusters.split(input)

        assertEquals(input, clusters.joinToString(separator = ""))
        clusters.forEach { cluster ->
            assertFalse(cluster.isEmpty())
            var index = 0
            while (index < cluster.length) {
                val character = cluster[index]
                if (character.isHighSurrogate()) {
                    assertTrue(index + 1 < cluster.length)
                    assertTrue(cluster[index + 1].isLowSurrogate())
                    index += 2
                } else {
                    assertFalse(character.isLowSurrogate())
                    index += 1
                }
            }
        }
    }
}
