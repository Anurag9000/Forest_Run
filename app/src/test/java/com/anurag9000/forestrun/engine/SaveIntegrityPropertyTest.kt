package com.anurag9000.forestrun.engine

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SaveIntegrityPropertyTest {

    private lateinit var context: Context
    private lateinit var primary: SharedPreferences
    private lateinit var compatibility: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        primary = context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
        compatibility = context.getSharedPreferences(
            "${SaveManager.PREFS_NAME}_compat_v${SaveIntegrityManager.CURRENT_SCHEMA_VERSION}",
            Context.MODE_PRIVATE
        )
        clearAll()
    }

    @After
    fun tearDown() {
        SaveManager.usePrimaryPreferences()
        clearAll()
    }

    @Test
    fun `random malformed current and legacy saves repair to a typed idempotent fixed point`() {
        repeat(128) { seed ->
            clearAll()
            SaveManager.usePrimaryPreferences()
            val random = Random(seed)
            val editor = primary.edit()
                .putInt(
                    SaveIntegrityManager.KEY_SCHEMA_VERSION,
                    if (random.nextBoolean()) 0 else SaveIntegrityManager.CURRENT_SCHEMA_VERSION
                )

            integerKeys.forEach { key ->
                if (random.nextBoolean()) putRandomValue(editor, key, random)
            }
            floatKeys.forEach { key ->
                if (random.nextBoolean()) putRandomValue(editor, key, random)
            }
            longKeys.forEach { key ->
                if (random.nextBoolean()) putRandomValue(editor, key, random)
            }
            requiredEnumKeys.forEach { key ->
                if (random.nextBoolean()) putRandomValue(editor, key, random)
            }
            nullableEnumKeys.forEach { key ->
                if (random.nextBoolean()) putRandomValue(editor, key, random)
            }
            setKeys.forEach { key ->
                if (random.nextBoolean()) putRandomValue(editor, key, random)
            }

            val dynamicEntity = com.anurag9000.forestrun.entities.EntityType.entries.random(random)
            putRandomValue(editor, "encounter_${dynamicEntity.name.lowercase()}", random)
            putRandomValue(editor, "spared_${dynamicEntity.name.lowercase()}", random)
            putRandomValue(editor, "relationship_stage_${dynamicEntity.name.lowercase()}", random)

            val unknownKey = "future_unknown_$seed"
            val unknownValue = "opaque-${random.nextLong()}"
            editor.putString(unknownKey, unknownValue).commit()

            val firstReport = SaveIntegrityManager.repair(context)
            val afterFirst = snapshot(primary)

            assertTrue(
                "seed=$seed unexpected first status ${firstReport.status}",
                firstReport.status == SaveIntegrityStatus.CURRENT ||
                    firstReport.status == SaveIntegrityStatus.MIGRATED
            )
            assertEquals(
                "seed=$seed schema",
                SaveIntegrityManager.CURRENT_SCHEMA_VERSION,
                primary.getInt(SaveIntegrityManager.KEY_SCHEMA_VERSION, -1)
            )
            assertEquals("seed=$seed unknown key", unknownValue, primary.getString(unknownKey, null))
            assertTypedAndBounded(seed)

            // Public readers for the most failure-prone primitive keys must be safe
            // after arbitrary supported SharedPreferences input types.
            assertTrue(SaveManager.loadHighScore(context) >= 0)
            assertTrue(SaveManager.loadLifetimeSeeds(context) >= 0)
            assertTrue(SaveManager.loadBestDistance(context).isFinite())
            assertTrue(SaveManager.loadBestDistance(context) >= 0f)
            assertTrue(SaveManager.loadGardenProgress(context) in 1..GardenEconomy.catalogueSize)

            val secondReport = SaveIntegrityManager.repair(context)
            val afterSecond = snapshot(primary)
            assertEquals("seed=$seed repair must be idempotent", afterFirst, afterSecond)
            assertEquals("seed=$seed second repair", SaveIntegrityStatus.CURRENT, secondReport.status)
            assertEquals("seed=$seed second repair edits", 0, secondReport.repairedEntries)
        }
    }

    @Test
    fun `random future saves preserve the entire primary namespace exactly`() {
        repeat(96) { seed ->
            clearAll()
            SaveManager.usePrimaryPreferences()
            val random = Random(seed xor 0x5A5A5A5A)
            val futureVersion = SaveIntegrityManager.CURRENT_SCHEMA_VERSION + 1 + random.nextInt(100)
            val editor = primary.edit().putInt(SaveIntegrityManager.KEY_SCHEMA_VERSION, futureVersion)

            (integerKeys + floatKeys + longKeys + requiredEnumKeys + nullableEnumKeys + setKeys)
                .shuffled(random)
                .take(random.nextInt(1, 10))
                .forEach { key -> putRandomValue(editor, key, random) }
            editor.putString("future_owned_$seed", "opaque-${random.nextLong()}").commit()
            val before = snapshot(primary)

            val report = SaveIntegrityManager.repair(context)

            assertEquals("seed=$seed status", SaveIntegrityStatus.FUTURE_VERSION, report.status)
            assertEquals("seed=$seed fromVersion", futureVersion, report.fromVersion)
            assertEquals("seed=$seed toVersion", futureVersion, report.toVersion)
            assertEquals("seed=$seed primary must be immutable", before, snapshot(primary))
            assertEquals(
                "seed=$seed active namespace",
                "${SaveManager.PREFS_NAME}_compat_v${SaveIntegrityManager.CURRENT_SCHEMA_VERSION}",
                SaveManager.activePrefsNameForTests
            )
        }
    }

    private fun assertTypedAndBounded(seed: Int) {
        val all = primary.all
        integerKeys.forEach { key ->
            if (key in all) {
                val value = all[key]
                assertTrue("seed=$seed $key type=${value?.javaClass}", value is Int)
                val intValue = value as Int
                if (key == "garden_unlocked") {
                    assertTrue("seed=$seed $key=$intValue", intValue in 1..GardenEconomy.catalogueSize)
                } else {
                    assertTrue("seed=$seed $key=$intValue", intValue >= 0)
                }
            }
        }
        floatKeys.forEach { key ->
            if (key in all) {
                val value = all[key]
                assertTrue("seed=$seed $key type=${value?.javaClass}", value is Float)
                val floatValue = value as Float
                assertTrue("seed=$seed $key=$floatValue", floatValue.isFinite() && floatValue >= 0f)
            }
        }
        longKeys.forEach { key ->
            if (key in all) {
                val value = all[key]
                assertTrue("seed=$seed $key type=${value?.javaClass}", value is Long)
                val longValue = value as Long
                if (key == "last_garden_greeting_day") {
                    assertTrue("seed=$seed $key=$longValue", longValue >= -1L)
                } else {
                    assertTrue("seed=$seed $key=$longValue", longValue >= 0L)
                }
            }
        }
        requiredEnumKeys.forEach { key ->
            if (key in all) assertTrue("seed=$seed $key", all[key] is String)
        }
        nullableEnumKeys.forEach { key ->
            if (key in all) assertTrue("seed=$seed $key", all[key] is String)
        }
        setKeys.forEach { key ->
            if (key in all) {
                val value = all[key]
                assertTrue("seed=$seed $key type=${value?.javaClass}", value is Set<*>)
                assertTrue("seed=$seed $key elements", (value as Set<*>).all { it is String })
            }
        }
    }

    private fun putRandomValue(editor: SharedPreferences.Editor, key: String, random: Random) {
        when (random.nextInt(6)) {
            0 -> editor.putInt(key, random.nextInt())
            1 -> editor.putLong(key, random.nextLong())
            2 -> {
                val candidates = floatArrayOf(
                    random.nextFloat() * 10_000f - 5_000f,
                    Float.NaN,
                    Float.POSITIVE_INFINITY,
                    Float.NEGATIVE_INFINITY
                )
                editor.putFloat(key, candidates[random.nextInt(candidates.size)])
            }
            3 -> editor.putBoolean(key, random.nextBoolean())
            4 -> editor.putString(key, "random-${random.nextLong()}")
            else -> editor.putStringSet(
                key,
                linkedSetOf("valid-looking", "", "x".repeat(140), "random-${random.nextInt()}")
            )
        }
    }

    private fun snapshot(prefs: SharedPreferences): Map<String, Any?> =
        prefs.all.mapValues { (_, value) ->
            if (value is Set<*>) value.toSet() else value
        }

    private fun clearAll() {
        primary.edit().clear().commit()
        compatibility.edit().clear().commit()
    }

    private companion object {
        val integerKeys = listOf(
            "high_score",
            "lifetime_seeds",
            "garden_unlocked",
            "route_kind_runs",
            "route_merciful_runs",
            "route_peaceful_runs",
            "forest_mood_streak",
            "forest_total_runs",
            "forest_gentle_runs",
            "forest_reckless_runs",
            "forest_fearful_runs",
            "forest_steady_runs",
            "rough_run_streak"
        )
        val floatKeys = listOf("best_distance")
        val longKeys = listOf("last_active_at_ms", "last_garden_greeting_day")
        val requiredEnumKeys = listOf("active_costume", "forest_mood")
        val nullableEnumKeys = listOf("last_killer", "featured_costume")
        val setKeys = listOf(
            "unlocked_costumes",
            "unlocked_memory_pages",
            "unlocked_relationship_milestones",
            "unlocked_history_marks"
        )
    }
}
