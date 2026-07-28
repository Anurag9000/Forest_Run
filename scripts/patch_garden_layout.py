#!/usr/bin/env python3
"""Wire GardenScreen to the tested non-overlapping layout plan."""

from pathlib import Path

TARGET = Path("app/src/main/java/com/yourname/forest_run/ui/GardenScreen.kt")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


def method_span(text: str, marker: str) -> tuple[int, int]:
    index = text.find(marker)
    if index < 0:
        raise RuntimeError(f"method marker not found: {marker}")
    line_start = text.rfind("\n", 0, index) + 1
    brace = text.find("{", index)
    depth = 0
    in_string = False
    escaped = False
    for pos in range(brace, len(text)):
        ch = text[pos]
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
            continue
        if ch == '"':
            in_string = True
        elif ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return line_start, pos + 1
    raise RuntimeError(f"unbalanced method: {marker}")


def replace_method(text: str, marker: str, replacement: str) -> str:
    start, end = method_span(text, marker)
    return text[:start] + replacement.rstrip() + text[end:]


def main() -> None:
    text = TARGET.read_text(encoding="utf-8")

    text = replace_once(
        text,
        """    private val CARD_W     = screenW / 10.5f\n    private val CARD_H     = screenH * 0.55f\n    private val CARD_GAP   = CARD_W * 0.12f\n    private val ROW_START_X = (screenW - (catalogue.size * (CARD_W + CARD_GAP) - CARD_GAP)) / 2f\n    private val ROW_Y       = screenH * 0.20f\n""",
        """    private val layoutPlan = GardenLayoutPlanner.build(\n        width = screenW.toFloat(),\n        height = screenH.toFloat(),\n        plantCount = catalogue.size,\n        costumeCount = CostumeStyle.entries.size\n    )\n    private val CARD_W = layoutPlan.plantCards.first().width\n    private val CARD_H = layoutPlan.plantCards.first().height\n    private val CARD_GAP = if (layoutPlan.plantCards.size > 1) {\n        layoutPlan.plantCards[1].left - layoutPlan.plantCards[0].right\n    } else {\n        0f\n    }\n    private val ROW_START_X = layoutPlan.catalogueBand.left\n    private val ROW_Y = layoutPlan.catalogueBand.top\n""",
        "replace overlapping Garden constants",
    )

    text = replace_once(
        text,
        """        drawHomeCharacter(canvas, cw, ch)\n        drawArrivalBadge(canvas, cw, ch)\n        drawSanctuaryTraces(canvas, cw, ch)\n""",
        """        when {\n            sanctuaryState.arrivalBadge.isNotBlank() -> {\n                drawArrivalBadge(canvas, cw, ch)\n                drawSanctuaryTraces(canvas, cw, ch)\n            }\n            sanctuaryState.homeCharacterLabel.isNotBlank() ->\n                drawHomeCharacter(canvas, cw, ch)\n            else -> drawSanctuaryTraces(canvas, cw, ch)\n        }\n""",
        "avoid stacked Garden story chips",
    )

    text = replace_method(
        text,
        "private fun drawStatsPanel(",
        """    private fun drawStatsPanel(canvas: Canvas, @Suppress("UNUSED_PARAMETER") cw: Float, @Suppress("UNUSED_PARAMETER") ch: Float) {\n        canvas.drawRoundRect(statsRect, 18f, 18f, statsPanelPaint)\n        canvas.drawRoundRect(statsRect, 18f, 18f, statsBorderPaint)\n\n        val rows = listOf(\n            "Best Run" to formatDistance(bestDistance),\n            "Last Killer" to lastKillerLabel,\n            "Spared" to sparedTotal.toString(),\n            "Friend Biomes" to friendshipTotal.toString(),\n            "Strongest Bond" to strongestBondLabel,\n            "Memory Pages" to memoryPageCount.toString()\n        )\n        val innerLeft = statsRect.left + 18f\n        val rowHeight = (statsRect.height() - 46f) / rows.size\n        var top = statsRect.top + 26f\n        canvas.save()\n        canvas.clipRect(statsRect)\n        rows.forEach { (label, value) ->\n            canvas.drawText(label, innerLeft, top, statsLabelPaint)\n            canvas.drawText(\n                ellipsizeText(value, statsRect.width() - 36f, statsValuePaint),\n                innerLeft,\n                top + 18f,\n                statsValuePaint\n            )\n            top += rowHeight\n        }\n        canvas.restore()\n    }\n""",
    )

    text = replace_method(
        text,
        "private fun drawLastRunPanel(",
        """    private fun drawLastRunPanel(canvas: Canvas) {\n        val summary = lastRunSummary ?: return\n        canvas.drawRoundRect(lastRunRect, 18f, 18f, statsPanelPaint)\n        canvas.drawRoundRect(lastRunRect, 18f, 18f, statsBorderPaint)\n\n        val left = lastRunRect.left + 18f\n        val maxWidth = lastRunRect.width() - 36f\n        val bottomLimit = lastRunRect.bottom - 14f\n        var y = lastRunRect.top + 28f\n\n        canvas.save()\n        canvas.clipRect(lastRunRect)\n        canvas.drawText("Last Run", left, y, wardrobeHintPaint)\n        y += 24f\n        canvas.drawText(\n            ellipsizeText(\n                "${formatDistance(summary.distanceM)}  •  ${summary.seedsCollected} seeds  •  ${summary.score} pts",\n                maxWidth,\n                statsValuePaint\n            ),\n            left,\n            y,\n            statsValuePaint\n        )\n        y += 22f\n\n        val summaryBits = buildList {\n            add("Tone: ${summary.forestMood.displayName}")\n            if (summary.pacifistRouteTier != com.yourname.forest_run.engine.PacifistRouteTier.NONE) {\n                add("Route: ${summary.pacifistRouteTier.displayName}")\n            }\n            if (summary.sparedCount > 0) add("${summary.sparedCount} spared")\n            if (summary.bloomConversions > 0) add("${summary.bloomConversions} Bloom")\n            if (summary.kindnessChain > 0) add("chain ${summary.kindnessChain}")\n        }\n        canvas.drawText(\n            ellipsizeText(summaryBits.joinToString("  •  "), maxWidth, statsLabelPaint),\n            left,\n            y,\n            statsLabelPaint\n        )\n        y += 21f\n\n        fun narrative(label: String, line: String, maxLines: Int = 2) {\n            if (line.isBlank() || y >= bottomLimit) return\n            canvas.drawText(ellipsizeText(label, maxWidth, statsLabelPaint), left, y, statsLabelPaint)\n            y += 16f\n            y = drawWrappedLeftText(\n                canvas = canvas,\n                text = line,\n                left = left,\n                baselineY = y,\n                maxWidth = maxWidth,\n                paint = reflectionPaint,\n                maxLines = maxLines\n            ) + 6f\n        }\n\n        if (summary.pacifistRouteTier != com.yourname.forest_run.engine.PacifistRouteTier.NONE) {\n            narrative(\n                "Route afterglow",\n                PacifistPresentation.routeAfterglowLine(summary.pacifistRouteTier),\n                maxLines = 2\n            )\n        }\n        if (sanctuaryState.worldOpinionLine.isNotBlank()) {\n            narrative(\n                "Opinion: ${sanctuaryState.worldOpinionLabel}",\n                sanctuaryState.worldOpinionLine,\n                maxLines = 2\n            )\n        }\n        sanctuaryState.homecomingConsequences.firstOrNull()?.let { consequence ->\n            narrative(consequence.label, consequence.line, maxLines = 2)\n        }\n        reflectionEntries.take(2).forEach { entry ->\n            narrative(entry.label, entry.text, maxLines = 2)\n        }\n        canvas.restore()\n    }\n""",
    )

    text = replace_once(
        text,
        """            canvas.drawText(wardrobeMessage, wardrobeRect.left + 20f, wardrobeRect.bottom - 12f, wardrobeHintPaint)\n            wardrobeHintPaint.alpha = 210\n""",
        """            drawWrappedLeftText(\n                canvas = canvas,\n                text = wardrobeMessage,\n                left = wardrobeRect.left + 20f,\n                baselineY = wardrobeRect.bottom - 28f,\n                maxWidth = wardrobeRect.width() - 40f,\n                paint = wardrobeHintPaint,\n                maxLines = 2\n            )\n            wardrobeHintPaint.alpha = 210\n""",
        "wrap wardrobe message",
    )

    text = replace_method(
        text,
        "private fun syncInteractiveLayout(",
        """    private fun syncInteractiveLayout(@Suppress("UNUSED_PARAMETER") cw: Float, @Suppress("UNUSED_PARAMETER") ch: Float) {\n        fun RectF.apply(box: LayoutBox) {\n            set(box.left, box.top, box.right, box.bottom)\n        }\n\n        runButtonRect.apply(layoutPlan.runButton)\n        statsRect.apply(layoutPlan.statsPanel)\n        lastRunRect.apply(layoutPlan.lastRunPanel)\n        wardrobeRect.apply(layoutPlan.wardrobePanel)\n        layoutPlan.wardrobeCards.forEachIndexed { index, box ->\n            wardrobeCardRects[index].apply(box)\n        }\n    }\n""",
    )

    text = replace_once(
        text,
        "        val top = if (sanctuaryState.arrivalBadge.isNotBlank()) ch * 0.268f else ch * 0.225f\n",
        "        val top = if (sanctuaryState.arrivalBadge.isNotBlank()) ch * 0.292f else ch * 0.268f\n",
        "separate trace chips from arrival badge",
    )

    text = replace_method(
        text,
        "private fun drawWrappedCenteredText(",
        """    private fun drawWrappedCenteredText(\n        canvas: Canvas,\n        text: String,\n        centerX: Float,\n        baselineY: Float,\n        maxWidth: Float,\n        paint: Paint\n    ) {\n        var y = baselineY\n        wrapTextLines(text, maxWidth, paint, maxLines = 2).forEach { line ->\n            canvas.drawText(line, centerX, y, paint)\n            y += paint.textSize + 6f\n        }\n    }\n\n    private fun drawWrappedLeftText(\n        canvas: Canvas,\n        text: String,\n        left: Float,\n        baselineY: Float,\n        maxWidth: Float,\n        paint: Paint,\n        maxLines: Int\n    ): Float {\n        var y = baselineY\n        wrapTextLines(text, maxWidth, paint, maxLines).forEach { line ->\n            canvas.drawText(line, left, y, paint)\n            y += paint.textSize + 4f\n        }\n        return y\n    }\n\n    private fun wrapTextLines(\n        text: String,\n        maxWidth: Float,\n        paint: Paint,\n        maxLines: Int\n    ): List<String> {\n        if (text.isBlank() || maxLines <= 0 || maxWidth <= 0f) return emptyList()\n        val lines = mutableListOf<String>()\n        val builder = StringBuilder()\n        for (word in text.trim().split(Regex("\\\\s+"))) {\n            val candidate = if (builder.isEmpty()) word else "$builder $word"\n            if (paint.measureText(candidate) <= maxWidth || builder.isEmpty()) {\n                builder.clear()\n                builder.append(candidate)\n            } else {\n                lines += builder.toString()\n                builder.clear()\n                builder.append(word)\n                if (lines.size >= maxLines) break\n            }\n        }\n        if (builder.isNotEmpty() && lines.size < maxLines) lines += builder.toString()\n        return lines.take(maxLines).mapIndexed { index, line ->\n            if (index == maxLines - 1 && paint.measureText(line) > maxWidth) {\n                ellipsizeText(line, maxWidth, paint)\n            } else {\n                line\n            }\n        }\n    }\n\n    private fun ellipsizeText(text: String, maxWidth: Float, paint: Paint): String {\n        if (paint.measureText(text) <= maxWidth) return text\n        val suffix = "…"\n        var end = text.length\n        while (end > 0 && paint.measureText(text.substring(0, end).trimEnd() + suffix) > maxWidth) {\n            end--\n        }\n        return text.substring(0, end).trimEnd() + suffix\n    }\n""",
    )

    TARGET.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
