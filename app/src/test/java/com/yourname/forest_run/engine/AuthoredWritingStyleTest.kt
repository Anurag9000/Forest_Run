package com.yourname.forest_run.engine

import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthoredWritingStyleTest {

    private val sourceFiles = listOf(
        "app/src/main/java/com/yourname/forest_run/engine/ReturnMomentsSystem.kt",
        "app/src/main/java/com/yourname/forest_run/engine/StoryFragmentSystem.kt",
        "app/src/main/java/com/yourname/forest_run/ui/RestQuoteManager.kt",
        "app/src/main/java/com/yourname/forest_run/engine/RunFlavorPresentation.kt"
    )

    private val ignoredFragments = listOf(
        "page_",
        "music_",
        "debug_",
        "raw/",
        ".name.lowercase()",
        "type.name",
        "appContext",
        "progressKind"
    )

    private val bannedExpositoryPhrases = listOf(
        "this means",
        "that means",
        "for example",
        "in order to",
        "the reason",
        "represents",
        "indicates",
        "explains",
        "therefore"
    )

    @Test
    fun `authored writing stays brief by default while allowing occasional lyrical lines`() {
        val lines = collectAuthoredLines()
        val lengths = lines.map { it.length }.sorted()

        val median = percentile(lengths, 0.50)
        val p90 = percentile(lengths, 0.90)
        val p95 = percentile(lengths, 0.95)
        val max = lengths.last()

        assertTrue("Expected authored corpus to exist", lengths.isNotEmpty())
        assertTrue("Median authored line length drifted too high: $median", median <= 60)
        assertTrue("90th percentile authored line length drifted too high: $p90", p90 <= 96)
        assertTrue("95th percentile authored line length drifted too high: $p95", p95 <= 104)
        assertTrue("Max authored line length drifted too high: $max", max <= 160)
    }

    @Test
    fun `authored writing avoids explicitly expository phrasing`() {
        val lowerLines = collectAuthoredLines().map { it.lowercase() }
        val offenders = lowerLines.filter { line ->
            bannedExpositoryPhrases.any { phrase -> phrase in line }
        }
        assertTrue(
            "Found explicitly expository phrases in authored text: ${offenders.take(5)}",
            offenders.isEmpty()
        )
    }

    private fun collectAuthoredLines(): List<String> {
        val stringRegex = Regex("\"((?:\\\\.|[^\"\\\\])*)\"")
        return sourceFiles.flatMap { relative ->
            val source = readRepoFile(relative)
            stringRegex.findAll(source)
                .map { decodeEscapes(it.groupValues[1]) }
                .filter(::looksLikeAuthoredLine)
                .toList()
        }
    }

    private fun looksLikeAuthoredLine(text: String): Boolean {
        if (text.length < 6) return false
        if (!text.any(Char::isLowerCase)) return false
        if (ignoredFragments.any { it in text }) return false
        return true
    }

    private fun percentile(sortedLengths: List<Int>, fraction: Double): Int {
        val index = (sortedLengths.size * fraction).toInt().coerceAtMost(sortedLengths.lastIndex)
        return sortedLengths[index]
    }

    private fun readRepoFile(relativePath: String): String {
        var root = Path.of("").toAbsolutePath()
        repeat(4) {
            val candidate = root.resolve(relativePath).toFile()
            if (candidate.exists()) {
                return candidate.readText()
            }
            root = root.parent ?: root
        }
        error("Unable to resolve source file for authored writing validation: $relativePath")
    }

    private fun decodeEscapes(value: String): String =
        value
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
}
