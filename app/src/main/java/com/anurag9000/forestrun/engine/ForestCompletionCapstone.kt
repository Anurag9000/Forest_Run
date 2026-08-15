package com.anurag9000.forestrun.engine

internal data class ForestCompletionCapstone(
    val completedPillars: Int,
    val totalPillars: Int,
    val complete: Boolean,
    val title: String,
    val line: String
) {
    init {
        require(totalPillars > 0) { "Completion capstone must have at least one pillar" }
        require(completedPillars in 0..totalPillars) { "Completion capstone progress is invalid" }
        require(complete == (completedPillars == totalPillars)) {
            "Completion capstone completion flag must match its progress"
        }
        require(title.isNotBlank() && line.isNotBlank()) {
            "Completion capstone copy must not be blank"
        }
    }
}

/**
 * Derived final recognition for the currently implemented long-horizon systems.
 * Nothing is persisted: expanding an authoritative catalogue automatically
 * changes the underlying track completion and therefore this capstone.
 */
internal object ForestCompletionCapstoneComposer {
    fun compose(
        collection: ForestCollectionSnapshot,
        pathHistory: ForestPathHistorySnapshot
    ): ForestCompletionCapstone {
        val completedTracks = collection.tracks.count(ForestCollectionTrack::isComplete)
        val routePillar = if (pathHistory.allGentleShapesSeen) 1 else 0
        val completed = completedTracks + routePillar
        val total = collection.tracks.size + 1
        val complete = completed == total
        return ForestCompletionCapstone(
            completedPillars = completed,
            totalPillars = total,
            complete = complete,
            title = if (complete) "The Forest Knows Your Name" else "A Forest Still Becoming",
            line = if (complete) {
                "Every family has been met, every living bond has matured, the Garden and wardrobe are complete, every biome carries peace, and every gentle route shape has returned home."
            } else {
                "The forest does not ask for a checklist during a run, but your long history is still growing across families, bonds, sanctuary, wardrobe, biome peace, and route memory."
            }
        )
    }
}
