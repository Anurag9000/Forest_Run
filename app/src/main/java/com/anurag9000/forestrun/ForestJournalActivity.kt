package com.anurag9000.forestrun

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.anurag9000.forestrun.engine.EncounterFamilyGroup
import com.anurag9000.forestrun.engine.ForestJournalComposer
import com.anurag9000.forestrun.engine.ForestJournalEntry
import com.anurag9000.forestrun.engine.ForestJournalSnapshot

/**
 * Native Android presentation of the persistent Forest Journal.
 *
 * The game itself uses a custom Canvas UI, but the memory book intentionally
 * uses platform views so long-form history remains scrollable, selectable by
 * accessibility services, and independent from gameplay frame timing.
 */
class ForestJournalActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(28, 45, 35)
        window.navigationBarColor = Color.rgb(20, 34, 27)
    }

    override fun onResume() {
        super.onResume()
        renderJournal()
    }

    private fun renderJournal() {
        val snapshot = ForestJournalComposer.snapshot(this)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(24, 39, 31))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(24), dp(28), dp(40))
        }
        scroll.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        content.addView(text("FOREST JOURNAL", 30f, Color.rgb(246, 231, 151), true))
        content.addView(text("What the forest remembers between runs", 16f, Color.rgb(206, 225, 198)))
        content.addView(spacer(12))
        content.addView(summaryView(snapshot))

        if (snapshot.historyMarks.isNotEmpty()) {
            content.addView(sectionTitle("MEMORY MARKS"))
            snapshot.historyMarks.forEach { mark ->
                content.addView(
                    card(
                        title = mark.label,
                        subtitle = mark.line,
                        detail = null,
                        emphasized = true
                    )
                )
            }
        }

        EncounterFamilyGroup.entries.forEach { group ->
            val entries = snapshot.entries.filter { it.group == group }
            content.addView(sectionTitle(groupLabel(group)))
            entries.forEach { entry -> content.addView(entryCard(entry)) }
        }

        val close = Button(this).apply {
            text = "Return to Forest"
            isAllCaps = false
            textSize = 16f
            setOnClickListener { finish() }
            contentDescription = "Return to Forest Run"
        }
        content.addView(spacer(18))
        content.addView(
            close,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
            )
        )
        setContentView(scroll)
    }

    private fun summaryView(snapshot: ForestJournalSnapshot): View {
        val peaceful = snapshot.peacefulBiomes
            .take(3)
            .joinToString { "${it.biome.displayName} ${it.friendshipCount}" }
            .ifBlank { "None yet" }
        val strongest = snapshot.strongestRelationship ?: "Still unfolding"
        val detail = buildString {
            append("${snapshot.discoveredFamilies}/${snapshot.totalFamilies} families discovered")
            append("  •  ${snapshot.totalEncounters} meetings")
            append("  •  ${snapshot.totalCleanPasses} clean passes")
            append("  •  ${snapshot.totalSpares} mercies")
            append("  •  ${snapshot.totalHits} hits")
            append("\nMemory pages: ${snapshot.memoryPageCount}")
            append("  •  Strongest bond: $strongest")
            append("\nPeace carried home: $peaceful")
        }
        return card(
            title = "Your remembered forest",
            subtitle = "Nothing here creates new progression; this book reflects the same history that changes runs and the Garden.",
            detail = detail,
            emphasized = true
        )
    }

    private fun entryCard(entry: ForestJournalEntry): View {
        val relationship = entry.relationshipStage?.let { "  •  Bond: ${it.displayName}" }.orEmpty()
        val variants = if (entry.authoredVariantCount > 1) {
            "  •  ${entry.authoredVariantCount} known forms"
        } else {
            ""
        }
        val biomes = entry.preferredBiomes.joinToString()
        return if (entry.discovered) {
            card(
                title = entry.displayName,
                subtitle = entry.temperament,
                detail = buildString {
                    append(entry.fieldNote)
                    append("\nMet ${entry.encounterCount}  •  Passed ${entry.cleanPassCount}")
                    append("  •  Spared ${entry.sparedCount}  •  Hit ${entry.hitCount}")
                    append(relationship)
                    append(variants)
                    append("\nUsually found: $biomes")
                }
            )
        } else {
            card(
                title = "?  ${entry.displayName}",
                subtitle = "Undiscovered",
                detail = "The forest has not introduced this family to your remembered path yet."
            )
        }
    }

    private fun card(
        title: String,
        subtitle: String,
        detail: String?,
        emphasized: Boolean = false
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(14))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(if (emphasized) Color.rgb(48, 70, 51) else Color.rgb(36, 57, 43))
            setStroke(dp(1), if (emphasized) Color.rgb(217, 218, 151) else Color.rgb(104, 138, 101))
        }
        addView(text(title, 19f, Color.rgb(247, 235, 174), true))
        addView(text(subtitle, 14f, Color.rgb(193, 219, 183)))
        if (!detail.isNullOrBlank()) {
            addView(text(detail, 13f, Color.rgb(224, 233, 214)))
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(10)
        }
    }

    private fun sectionTitle(label: String): TextView = text(
        label,
        15f,
        Color.rgb(235, 216, 132),
        true
    ).apply {
        setPadding(0, dp(22), 0, dp(8))
        gravity = Gravity.START
    }

    private fun text(
        value: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false
    ): TextView = TextView(this).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setLineSpacing(0f, 1.12f)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun spacer(heightDp: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
    }

    private fun groupLabel(group: EncounterFamilyGroup): String = when (group) {
        EncounterFamilyGroup.FLORA -> "FLORA"
        EncounterFamilyGroup.TREE -> "TREES"
        EncounterFamilyGroup.BIRD -> "BIRDS"
        EncounterFamilyGroup.ANIMAL -> "ANIMALS"
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
