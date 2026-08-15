package com.anurag9000.forestrun.ui

/**
 * Small allocation-conscious grapheme boundary helper for Canvas-authored copy.
 *
 * Android Canvas measures UTF-16 strings, but wrapping must never cut a user-
 * perceived character between lines. This keeps surrogate pairs, combining
 * marks, variation selectors, emoji modifiers/tags, ZWJ sequences, CRLF, and
 * regional-indicator flag pairs together. It deliberately owns only boundary
 * safety; shaping and glyph availability remain the font/platform's job.
 */
internal object GraphemeClusters {
    private const val ZERO_WIDTH_JOINER = 0x200D
    private const val CARRIAGE_RETURN = 0x000D
    private const val LINE_FEED = 0x000A

    fun split(text: String): List<String> {
        if (text.isEmpty()) return emptyList()

        val result = ArrayList<String>()
        var cluster = StringBuilder()
        var index = 0
        var previousCodePoint = -1
        var regionalIndicatorCount = 0

        fun flush() {
            if (cluster.isNotEmpty()) {
                result.add(cluster.toString())
                cluster = StringBuilder()
            }
            regionalIndicatorCount = 0
        }

        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val codePointChars = Character.charCount(codePoint)
            val continuesCluster = cluster.isNotEmpty() && (
                isExtend(codePoint) ||
                    codePoint == ZERO_WIDTH_JOINER ||
                    previousCodePoint == ZERO_WIDTH_JOINER ||
                    (previousCodePoint == CARRIAGE_RETURN && codePoint == LINE_FEED) ||
                    (
                        isRegionalIndicator(previousCodePoint) &&
                            isRegionalIndicator(codePoint) &&
                            regionalIndicatorCount % 2 == 1
                        )
                )

            if (!continuesCluster) flush()
            cluster.appendCodePoint(codePoint)

            if (isRegionalIndicator(codePoint)) {
                regionalIndicatorCount += 1
            } else if (!isExtend(codePoint) && codePoint != ZERO_WIDTH_JOINER) {
                regionalIndicatorCount = 0
            }

            previousCodePoint = codePoint
            index += codePointChars
        }
        flush()
        return result
    }

    private fun isExtend(codePoint: Int): Boolean =
        when (Character.getType(codePoint)) {
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt() -> true
            else -> isVariationSelector(codePoint) ||
                isEmojiModifier(codePoint) ||
                isEmojiTag(codePoint)
        }

    private fun isVariationSelector(codePoint: Int): Boolean =
        codePoint in 0xFE00..0xFE0F || codePoint in 0xE0100..0xE01EF

    private fun isEmojiModifier(codePoint: Int): Boolean =
        codePoint in 0x1F3FB..0x1F3FF

    private fun isEmojiTag(codePoint: Int): Boolean =
        codePoint in 0xE0020..0xE007F

    private fun isRegionalIndicator(codePoint: Int): Boolean =
        codePoint in 0x1F1E6..0x1F1FF
}
