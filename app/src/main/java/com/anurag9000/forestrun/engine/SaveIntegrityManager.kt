package com.anurag9000.forestrun.engine

import android.content.Context
import android.content.SharedPreferences
import com.anurag9000.forestrun.entities.CostumeStyle
import com.anurag9000.forestrun.entities.EntityType

internal enum class SaveIntegrityStatus {
    CURRENT,
    MIGRATED,
    FUTURE_VERSION,
    WRITE_FAILED
}

internal data class SaveIntegrityReport(
    val status: SaveIntegrityStatus,
    val fromVersion: Int,
    val toVersion: Int,
    val repairedEntries: Int
)

/**
 * Repairs only known Forest Run keys before any runtime consumer reads them.
 * Unknown keys are preserved, and a save stamped by a newer schema is never
 * rewritten by this older build.
 */
object SaveIntegrityManager {
    internal const val CURRENT_SCHEMA_VERSION = 1
    internal const val KEY_SCHEMA_VERSION = "save_schema_version"

    private const val MAX_MEMORY_PAGE_IDS = 512
    private const val MAX_HISTORY_MARK_IDS = 256
    private const val MAX_PERSISTED_ID_LENGTH = 128

    private val lastRunKeys = setOf(
        "last_run_score",
        "last_run_distance",
        "last_run_new_high",
        "last_run_high_score",
        "last_run_mercy_hearts",
        "last_run_mercy_misses",
        "last_run_kindness_chain",
        "last_run_clean_passes",
        "last_run_spared",
        "last_run_hits",
        "last_run_seeds",
        "last_run_bloom_conversions",
        "last_run_quote",
        "last_run_killer",
        "last_run_forest_mood",
        "last_run_pacifist_route"
    )
    private val requiredLastRunKeys = lastRunKeys - "last_run_killer"

    internal fun repair(context: Context): SaveIntegrityReport {
        SaveManager.usePrimaryPreferences()
        val appContext = context.applicationContext
        val primary = appContext.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
        val primaryVersion = (primary.all[KEY_SCHEMA_VERSION] as? Int)?.coerceAtLeast(0) ?: 0
        if (primaryVersion > CURRENT_SCHEMA_VERSION) {
            SaveManager.useCompatibilityPreferences(CURRENT_SCHEMA_VERSION)
            val compatibility = repairNamespace(
                appContext.getSharedPreferences(SaveManager.activePrefsNameForTests, Context.MODE_PRIVATE)
            )
            return SaveIntegrityReport(
                status = if (compatibility.status == SaveIntegrityStatus.WRITE_FAILED) {
                    SaveIntegrityStatus.WRITE_FAILED
                } else {
                    SaveIntegrityStatus.FUTURE_VERSION
                },
                fromVersion = primaryVersion,
                toVersion = primaryVersion,
                repairedEntries = compatibility.repairedEntries
            )
        }
        return repairNamespace(primary)
    }

    private fun repairNamespace(prefs: SharedPreferences): SaveIntegrityReport {
        val all = prefs.all
        val rawVersion = all[KEY_SCHEMA_VERSION]
        val fromVersion = (rawVersion as? Int)?.coerceAtLeast(0) ?: 0
        val repair = RepairSession(all, prefs.edit())
        repair.nonNegativeInt("high_score")
        repair.nonNegativeInt("lifetime_seeds")
        repair.finiteNonNegativeFloat("best_distance")
        repair.nullableEnum("last_killer", EntityType.entries.mapTo(mutableSetOf()) { it.name })
        repair.boundedInt(
            "garden_unlocked",
            minimum = 1,
            maximum = GardenEconomy.catalogueSize,
            fallback = 1
        )

        repair.nonNegativeInt("route_kind_runs")
        repair.nonNegativeInt("route_merciful_runs")
        repair.nonNegativeInt("route_peaceful_runs")

        repair.enumSet(
            "unlocked_costumes",
            CostumeStyle.entries
                .filterNot { it == CostumeStyle.NONE }
                .mapTo(mutableSetOf()) { it.name }
        )
        repair.requiredEnum(
            "active_costume",
            CostumeStyle.entries.mapTo(mutableSetOf()) { it.name },
            CostumeStyle.NONE.name
        )
        repair.nullableEnum(
            "featured_costume",
            CostumeStyle.entries.mapTo(mutableSetOf()) { it.name }
        )

        repair.requiredEnum(
            "forest_mood",
            ForestMood.entries.mapTo(mutableSetOf()) { it.name },
            ForestMood.STEADY.name
        )
        repair.nonNegativeInt("forest_mood_streak")
        repair.nonNegativeInt("forest_total_runs")
        repair.nonNegativeInt("forest_gentle_runs")
        repair.nonNegativeInt("forest_reckless_runs")
        repair.nonNegativeInt("forest_fearful_runs")
        repair.nonNegativeInt("forest_steady_runs")
        repair.boundedLong("last_active_at_ms", minimum = 0L, maximum = Long.MAX_VALUE, fallback = 0L)
        repair.boundedLong("last_garden_greeting_day", minimum = -1L, maximum = Long.MAX_VALUE, fallback = -1L)
        repair.nonNegativeInt("rough_run_streak")
        repair.genericStringSet(
            key = "unlocked_memory_pages",
            maximumEntries = MAX_MEMORY_PAGE_IDS,
            maximumIdLength = MAX_PERSISTED_ID_LENGTH
        )
        repair.enumSet(
            "unlocked_relationship_milestones",
            EntityType.entries
                .filter(RelationshipArcSystem::isTracked)
                .mapTo(mutableSetOf()) { it.name }
        )
        repair.genericStringSet(
            key = "unlocked_history_marks",
            maximumEntries = MAX_HISTORY_MARK_IDS,
            maximumIdLength = MAX_PERSISTED_ID_LENGTH
        )

        repairLastRun(repair, all)
        repairDynamicKeys(repair, all)
        repair.enforceCostumeConsistency()

        if (rawVersion != CURRENT_SCHEMA_VERSION) {
            repair.putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
        }

        val committed = repair.commit()
        val status = when {
            !committed -> SaveIntegrityStatus.WRITE_FAILED
            repair.changedEntries == 0 && fromVersion == CURRENT_SCHEMA_VERSION -> SaveIntegrityStatus.CURRENT
            else -> SaveIntegrityStatus.MIGRATED
        }
        return SaveIntegrityReport(
            status = status,
            fromVersion = fromVersion,
            toVersion = if (committed) CURRENT_SCHEMA_VERSION else fromVersion,
            repairedEntries = repair.changedEntries
        )
    }

    private fun repairLastRun(repair: RepairSession, all: Map<String, *>) {
        if (lastRunKeys.none(all::containsKey)) return
        if (requiredLastRunKeys.any { it !in all } ||
            all["last_run_score"] !is Int ||
            all["last_run_quote"] !is String
        ) {
            lastRunKeys.forEach(repair::removeIfPresent)
            return
        }

        repair.nonNegativeInt("last_run_score")
        repair.finiteNonNegativeFloat("last_run_distance")
        repair.boolean("last_run_new_high", fallback = false)
        repair.nonNegativeInt("last_run_high_score")
        repair.nonNegativeInt("last_run_mercy_hearts")
        repair.nonNegativeInt("last_run_mercy_misses")
        repair.nonNegativeInt("last_run_kindness_chain")
        repair.nonNegativeInt("last_run_clean_passes")
        repair.nonNegativeInt("last_run_spared")
        repair.nonNegativeInt("last_run_hits")
        repair.nonNegativeInt("last_run_seeds")
        repair.nonNegativeInt("last_run_bloom_conversions")
        repair.string("last_run_quote", fallback = "")
        repair.nullableEnum("last_run_killer", EntityType.entries.mapTo(mutableSetOf()) { it.name })
        repair.requiredEnum(
            "last_run_forest_mood",
            ForestMood.entries.mapTo(mutableSetOf()) { it.name },
            ForestMood.STEADY.name
        )
        repair.requiredEnum(
            "last_run_pacifist_route",
            PacifistRouteTier.entries.mapTo(mutableSetOf()) { it.name },
            PacifistRouteTier.NONE.name
        )
    }

    private fun repairDynamicKeys(repair: RepairSession, all: Map<String, *>) {
        val entityNames = EntityType.entries.mapTo(mutableSetOf()) { it.name.lowercase() }
        val biomeNames = Biome.entries.mapTo(mutableSetOf()) { it.name.lowercase() }
        val relationshipStages = RelationshipStage.entries.mapTo(mutableSetOf()) { it.name }
        val entityCounterPrefixes = listOf(
            "encounter_",
            "spared_",
            "hit_",
            "clean_pass_",
            "kindness_streak_",
            "tender_streak_"
        )

        for (key in all.keys) {
            val counterPrefix = entityCounterPrefixes.firstOrNull { key.startsWith(it) }
            when {
                counterPrefix != null && key.removePrefix(counterPrefix) in entityNames ->
                    repair.nonNegativeInt(key)
                key.startsWith("friendship_") && key.removePrefix("friendship_") in biomeNames ->
                    repair.nonNegativeInt(key)
                key.startsWith("relationship_stage_") &&
                    key.removePrefix("relationship_stage_") in entityNames ->
                    repair.nullableEnum(key, relationshipStages)
            }
        }
    }

    private class RepairSession(
        private val original: Map<String, *>,
        private val editor: SharedPreferences.Editor
    ) {
        var changedEntries: Int = 0
            private set

        fun nonNegativeInt(key: String) = boundedInt(key, 0, Int.MAX_VALUE, 0)

        fun boundedInt(key: String, minimum: Int, maximum: Int, fallback: Int) {
            val raw = original[key] ?: return
            val current = raw as? Int
            val repaired = current?.coerceIn(minimum, maximum) ?: fallback.coerceIn(minimum, maximum)
            if (current != repaired) putInt(key, repaired)
        }

        fun boundedLong(key: String, minimum: Long, maximum: Long, fallback: Long) {
            val raw = original[key] ?: return
            val current = raw as? Long
            val repaired = current?.coerceIn(minimum, maximum) ?: fallback.coerceIn(minimum, maximum)
            if (current != repaired) {
                editor.putLong(key, repaired)
                changedEntries++
            }
        }

        fun finiteNonNegativeFloat(key: String) {
            val raw = original[key] ?: return
            val current = raw as? Float
            val repaired = current?.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
            if (current == null || current.toRawBits() != repaired.toRawBits()) {
                editor.putFloat(key, repaired)
                changedEntries++
            }
        }

        fun boolean(key: String, fallback: Boolean) {
            val raw = original[key] ?: return
            if (raw !is Boolean) {
                editor.putBoolean(key, fallback)
                changedEntries++
            }
        }

        fun string(key: String, fallback: String) {
            val raw = original[key] ?: return
            if (raw !is String) {
                editor.putString(key, fallback)
                changedEntries++
            }
        }

        fun requiredEnum(key: String, allowed: Set<String>, fallback: String) {
            val raw = original[key] ?: return
            val current = raw as? String
            if (current == null || current !in allowed) {
                editor.putString(key, fallback)
                changedEntries++
            }
        }

        fun nullableEnum(key: String, allowed: Set<String>) {
            val raw = original[key] ?: return
            val current = raw as? String
            if (current == null || current !in allowed) removeIfPresent(key)
        }

        fun enumSet(key: String, allowed: Set<String>) {
            val raw = original[key] ?: return
            val current = raw as? Set<*>
            val repaired = current.orEmpty().filterIsInstance<String>().filterTo(linkedSetOf()) { it in allowed }
            if (current == null || current.size != repaired.size ||
                current.any { it !is String || it !in repaired }
            ) {
                editor.putStringSet(key, repaired)
                changedEntries++
            }
        }

        fun genericStringSet(
            key: String,
            maximumEntries: Int,
            maximumIdLength: Int
        ) {
            require(maximumEntries > 0) { "maximumEntries must be positive" }
            require(maximumIdLength > 0) { "maximumIdLength must be positive" }
            val raw = original[key] ?: return
            val current = raw as? Set<*>
            val repaired = current.orEmpty()
                .asSequence()
                .filterIsInstance<String>()
                .filter { value -> value.isNotBlank() && value.length <= maximumIdLength }
                .distinct()
                .sorted()
                .take(maximumEntries)
                .toCollection(linkedSetOf())
            if (current == null || current.size != repaired.size ||
                current.any { it !is String || it !in repaired }
            ) {
                editor.putStringSet(key, repaired)
                changedEntries++
            }
        }

        fun enforceCostumeConsistency() {
            val allowed = CostumeStyle.entries
                .filterNot { it == CostumeStyle.NONE }
                .mapTo(mutableSetOf()) { it.name }
            val unlocked = when (val raw = original["unlocked_costumes"]) {
                is Set<*> -> raw.filterIsInstance<String>().filterTo(mutableSetOf()) { it in allowed }
                else -> emptySet()
            }
            val active = original["active_costume"] as? String
            if (active != null && active != CostumeStyle.NONE.name && active !in unlocked) {
                editor.putString("active_costume", CostumeStyle.NONE.name)
                changedEntries++
            }
            val featured = original["featured_costume"] as? String
            if (featured != null && featured !in unlocked) removeIfPresent("featured_costume")
        }

        fun putInt(key: String, value: Int) {
            editor.putInt(key, value)
            changedEntries++
        }

        fun removeIfPresent(key: String) {
            if (!original.containsKey(key)) return
            editor.remove(key)
            changedEntries++
        }

        fun commit(): Boolean = if (changedEntries == 0) true else editor.commit()
    }
}
