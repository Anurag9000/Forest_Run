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
import com.anurag9000.forestrun.engine.ForestCollectionProgressComposer
import com.anurag9000.forestrun.engine.ForestCollectionSnapshot
import com.anurag9000.forestrun.engine.ForestCollectionTrack
import com.anurag9000.forestrun.engine.ForestJournalComposer
import com.anurag9000.forestrun.engine.ForestJournalEntry
import com.anurag9000.forestrun.engine.ForestJournalSnapshot
import com.anurag9000.forestrun.engine.ForestLegacyMilestone
import com.anurag9000.forestrun.engine.ForestMemoryPagePresentation
import com.anurag9000.forestrun.engine.ForestPathHistoryComposer
import com.anurag9000.forestrun.engine.ForestPathHistorySnapshot
import com.anurag9000.forestrun.engine.ForestPathMemory
import com.anurag9000.forestrun.engine.ForestRelationshipMemory
import com.anurag9000.forestrun.engine.ForestWardrobeMemory
import com.anurag9000.forestrun.engine.RelationshipStage

/**
 * Native Android presentation of the persistent Forest Journal.
 *
 * The game itself uses a custom Canvas UI, but the memory book intentionally
 * uses platform views so long-form history remains scrollable, selectable by
 * accessibility services, and independent from gameplay frame timing.
 */
class ForestJournalActivity : Activity() {
    private enum class JournalSection(val label: String) {
        ALL("All"),
        PROGRESS("Progress"),
        BONDS("Bonds"),
        MEMORIES("Memories"),
        FAMILIES("Families")
    }

    private var selectedSection = JournalSection.ALL

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
        val collection = ForestCollectionProgressComposer.snapshot(this, snapshot)
        val pathHistory = ForestPathHistoryComposer.snapshot(this)
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
        content.addView(sectionFilterRow())
        content.addView(spacer(12))
        content.addView(summaryView(snapshot, collection))

        if (selectedSection == JournalSection.ALL || selectedSection == JournalSection.PROGRESS) {
            addProgressSections(content, collection, pathHistory)
        }
        if (selectedSection == JournalSection.ALL || selectedSection == JournalSection.BONDS) {
            addRelationshipSection(content, collection)
        }
        if (selectedSection == JournalSection.ALL || selectedSection == JournalSection.MEMORIES) {
            addMemorySections(content, snapshot, collection)
        }
        if (selectedSection == JournalSection.ALL || selectedSection == JournalSection.FAMILIES) {
            addFamilySections(content, snapshot)
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

    private fun addProgressSections(
        content: LinearLayout,
        collection: ForestCollectionSnapshot,
        pathHistory: ForestPathHistorySnapshot
    ) {
        content.addView(sectionTitle("COLLECTION PATH"))
        collection.tracks.forEach { track ->
            content.addView(collectionTrackCard(track))
        }

        content.addView(sectionTitle("PATH HISTORY"))
        pathHistory.paths.forEach { path ->
            content.addView(pathHistoryCard(path))
        }
        content.addView(
            card(
                title = if (pathHistory.allGentleShapesSeen) {
                    "Every Gentle Shape"
                } else {
                    "○ Every Gentle Shape"
                },
                subtitle = "${pathHistory.discoveredTiers}/${pathHistory.totalTiers} route tiers remembered",
                detail = if (pathHistory.allGentleShapesSeen) {
                    "Kind, Merciful, and Peaceful routes have each returned to the willow at least once."
                } else {
                    "The forest has not yet seen every gentle route tier come home."
                },
                emphasized = pathHistory.allGentleShapesSeen
            )
        )

        content.addView(sectionTitle("LEGACY MILESTONES"))
        collection.milestones
            .sortedByDescending(ForestLegacyMilestone::achieved)
            .forEach { milestone ->
                content.addView(milestoneCard(milestone))
            }

        content.addView(sectionTitle("WARDROBE MEMORIES"))
        collection.wardrobe.forEach { style ->
            content.addView(wardrobeCard(style))
        }
    }

    private fun addRelationshipSection(
        content: LinearLayout,
        collection: ForestCollectionSnapshot
    ) {
        content.addView(sectionTitle("LIVING BONDS"))
        if (collection.relationships.isEmpty()) {
            content.addView(
                card(
                    title = "No familiar creature yet",
                    subtitle = "Persistent bonds begin with repeated meetings.",
                    detail = "Cat, Fox, Wolf, Dog, Owl, and Eagle can learn the pattern of how you treat them over many runs."
                )
            )
        } else {
            collection.relationships.forEach { relationship ->
                content.addView(relationshipCard(relationship))
            }
        }
    }

    private fun addMemorySections(
        content: LinearLayout,
        snapshot: ForestJournalSnapshot,
        collection: ForestCollectionSnapshot
    ) {
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

        content.addView(sectionTitle("MEMORY PAGES"))
        if (collection.memoryPages.isEmpty()) {
            content.addView(
                card(
                    title = "The pages are still quiet",
                    subtitle = "Rest, relationships, routes, Bloom, weather, and Garden reflections can leave pages behind.",
                    detail = "Nothing must be collected from a checklist; pages appear when the forest has something persistent to remember."
                )
            )
        } else {
            collection.memoryPages.forEach { page ->
                content.addView(memoryPageCard(page))
            }
        }
    }

    private fun addFamilySections(
        content: LinearLayout,
        snapshot: ForestJournalSnapshot
    ) {
        EncounterFamilyGroup.entries.forEach { group ->
            val entries = snapshot.entries.filter { it.group == group }
            content.addView(sectionTitle(groupLabel(group)))
            entries.forEach { entry -> content.addView(entryCard(entry)) }
        }
    }

    private fun sectionFilterRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        JournalSection.entries.forEach { section ->
            addView(
                Button(this@ForestJournalActivity).apply {
                    text = section.label
                    isAllCaps = false
                    textSize = 12f
                    isSelected = section == selectedSection
                    alpha = if (isSelected) 1f else 0.78f
                    contentDescription = if (isSelected) {
                        "${section.label} Journal section, selected"
                    } else {
                        "Show ${section.label} Journal section"
                    }
                    setOnClickListener {
                        if (selectedSection != section) {
                            selectedSection = section
                            renderJournal()
                        }
                    }
                },
                LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                }
            )
        }
    }

    private fun summaryView(
        snapshot: ForestJournalSnapshot,
        collection: ForestCollectionSnapshot
    ): View {
        val peaceful = snapshot.peacefulBiomes
            .take(3)
            .joinToString { "${it.biome.displayName} ${it.friendshipCount}" }
            .ifBlank { "None yet" }
        val strongest = snapshot.strongestRelationship ?: "Still unfolding"
        val completedTracks = collection.tracks.count(ForestCollectionTrack::isComplete)
        val achievedMilestones = collection.milestones.count(ForestLegacyMilestone::achieved)
        val detail = buildString {
            append("${snapshot.discoveredFamilies}/${snapshot.totalFamilies} families discovered")
            append("  •  ${snapshot.totalEncounters} meetings")
            append("  •  ${snapshot.totalCleanPasses} clean passes")
            append("  •  ${snapshot.totalSpares} mercies")
            append("  •  ${snapshot.totalHits} hits")
            append("\nMemory pages: ${snapshot.memoryPageCount}")
            append("  •  Strongest bond: $strongest")
            append("\nCollection paths complete: $completedTracks/${collection.tracks.size}")
            append("  •  Legacy milestones: $achievedMilestones/${collection.milestones.size}")
            append("\nGentle routes: ${collection.kindRuns} kind")
            append("  •  ${collection.mercifulRuns} merciful")
            append("  •  ${collection.peacefulRuns} peaceful")
            append("\nPeace carried home: $peaceful")
        }
        return card(
            title = "Your remembered forest",
            subtitle = "Nothing here creates new progression; this book reflects the same history that changes runs and the Garden.",
            detail = detail,
            emphasized = true
        )
    }

    private fun collectionTrackCard(track: ForestCollectionTrack): View = card(
        title = track.label,
        subtitle = if (track.isComplete) {
            "${track.progressLabel} — Complete"
        } else {
            "${track.progressLabel} remembered"
        },
        detail = track.detail,
        emphasized = track.isComplete
    )

    private fun pathHistoryCard(path: ForestPathMemory): View = card(
        title = if (path.discovered) path.label else "○ ${path.label}",
        subtitle = if (path.discovered) {
            "${path.runCount} remembered run${if (path.runCount == 1) "" else "s"}"
        } else {
            "Not yet carried home"
        },
        detail = path.line,
        emphasized = path.discovered
    )

    private fun milestoneCard(milestone: ForestLegacyMilestone): View = card(
        title = if (milestone.achieved) milestone.title else "○ ${milestone.title}",
        subtitle = if (milestone.achieved) "Remembered • ${milestone.progress}" else milestone.progress,
        detail = if (milestone.achieved) {
            milestone.line
        } else {
            "Not yet remembered. ${milestone.line}"
        },
        emphasized = milestone.achieved
    )

    private fun relationshipCard(relationship: ForestRelationshipMemory): View {
        val detail = buildString {
            append(relationship.toneLine)
            relationship.milestoneTitle?.let { title ->
                append("\n$title — ${relationship.milestoneLine}")
            }
            relationship.ritualTitle?.let { title ->
                append("\n$title — ${relationship.ritualLine}")
            }
            relationship.costumeMemory?.let { costume ->
                append("\nWearable memory: $costume")
            }
        }
        return card(
            title = relationship.displayName,
            subtitle = "${relationship.stage.displayName} • ${relationship.toneLabel}",
            detail = detail,
            emphasized = relationship.stage == RelationshipStage.MILESTONE
        )
    }

    private fun wardrobeCard(style: ForestWardrobeMemory): View = card(
        title = when {
            style.active -> "${style.displayName} — Equipped"
            style.available -> style.displayName
            else -> "○ ${style.displayName}"
        },
        subtitle = when {
            style.active -> "Carried into the next run"
            style.available -> "Available"
            else -> "Still locked"
        },
        detail = style.unlockHint,
        emphasized = style.active
    )

    private fun memoryPageCard(page: ForestMemoryPagePresentation): View = card(
        title = page.title,
        subtitle = page.category,
        detail = page.line
    )

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
