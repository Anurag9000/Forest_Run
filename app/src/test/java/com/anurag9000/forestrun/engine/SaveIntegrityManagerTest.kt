package com.anurag9000.forestrun.engine

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.CostumeStyle
import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SaveIntegrityManagerTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        prefs = context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        context.getSharedPreferences(
            "${SaveManager.PREFS_NAME}_compat_v${SaveIntegrityManager.CURRENT_SCHEMA_VERSION}",
            Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @After
    fun tearDown() {
        SaveManager.usePrimaryPreferences()
    }

    @Test
    fun `legacy corrupt values are repaired without deleting unknown keys`() {
        prefs.edit()
            .putString("high_score", "broken")
            .putInt("lifetime_seeds", -7)
            .putFloat("best_distance", Float.NaN)
            .putInt("garden_unlocked", 99)
            .putString("last_killer", "NOT_AN_ENTITY")
            .putStringSet("unlocked_costumes", setOf(CostumeStyle.FLOWER_CROWN.name, "BROKEN"))
            .putString("active_costume", CostumeStyle.MOON_CAPE.name)
            .putString("featured_costume", "BROKEN")
            .putString("forest_mood", "BROKEN")
            .putInt("encounter_fox", -4)
            .putString("hit_wolf", "broken")
            .putString("relationship_stage_cat", "BROKEN")
            .putLong("last_active_at_ms", -10L)
            .putString("unknown_future_key", "preserve-me")
            .commit()

        val report = SaveIntegrityManager.repair(context)

        assertEquals(SaveIntegrityStatus.MIGRATED, report.status)
        assertEquals(SaveIntegrityManager.CURRENT_SCHEMA_VERSION, prefs.getInt(SaveIntegrityManager.KEY_SCHEMA_VERSION, -1))
        assertEquals(0, SaveManager.loadHighScore(context))
        assertEquals(0, SaveManager.loadLifetimeSeeds(context))
        assertEquals(0f, SaveManager.loadBestDistance(context), 0f)
        assertEquals(9, SaveManager.loadGardenProgress(context))
        assertNull(SaveManager.loadLastKiller(context))
        assertEquals(setOf(CostumeStyle.FLOWER_CROWN), SaveManager.loadUnlockedCostumes(context))
        assertEquals(CostumeStyle.NONE, SaveManager.loadActiveCostume(context))
        assertNull(SaveManager.loadFeaturedCostume(context))
        assertEquals(ForestMood.STEADY, SaveManager.loadForestMoodState(context).currentMood)
        assertEquals(0, SaveManager.loadEncounterCount(context, EntityType.FOX))
        assertEquals(0, SaveManager.loadHitCount(context, EntityType.WOLF))
        assertNull(SaveManager.loadRelationshipStage(context, EntityType.CAT))
        assertEquals(0L, SaveManager.loadReturnMomentState(context).lastActiveAtMs)
        assertEquals("preserve-me", prefs.getString("unknown_future_key", null))
    }

    @Test
    fun `repair is idempotent once current schema is clean`() {
        val first = SaveIntegrityManager.repair(context)
        val second = SaveIntegrityManager.repair(context)

        assertEquals(SaveIntegrityStatus.MIGRATED, first.status)
        assertEquals(SaveIntegrityStatus.CURRENT, second.status)
        assertEquals(0, second.repairedEntries)
    }

    @Test
    fun `future schema is preserved without destructive downgrade`() {
        prefs.edit()
            .putInt(SaveIntegrityManager.KEY_SCHEMA_VERSION, SaveIntegrityManager.CURRENT_SCHEMA_VERSION + 5)
            .putString("high_score", "future-owned-value")
            .putString("future_only_key", "keep")
            .commit()
        val before = prefs.all.toMap()

        val report = SaveIntegrityManager.repair(context)

        assertEquals(SaveIntegrityStatus.FUTURE_VERSION, report.status)
        assertEquals(before, prefs.all)
        assertEquals(
            "${SaveManager.PREFS_NAME}_compat_v${SaveIntegrityManager.CURRENT_SCHEMA_VERSION}",
            SaveManager.activePrefsNameForTests
        )
        assertEquals(0, SaveManager.loadHighScore(context))
    }

    @Test
    fun `partial last run summary is discarded instead of fabricated`() {
        prefs.edit()
            .putInt("last_run_score", 500)
            .putFloat("last_run_distance", 250f)
            .putString("last_run_killer", EntityType.WOLF.name)
            .commit()

        SaveIntegrityManager.repair(context)

        assertNull(SaveManager.loadLastRunSummary(context))
        assertFalse(prefs.contains("last_run_score"))
        assertFalse(prefs.contains("last_run_killer"))
    }

    @Test
    fun `valid current data and unknown dynamic suffixes survive repair`() {
        prefs.edit()
            .putInt(SaveIntegrityManager.KEY_SCHEMA_VERSION, SaveIntegrityManager.CURRENT_SCHEMA_VERSION)
            .putInt("encounter_cat", 4)
            .putString("relationship_stage_cat", RelationshipStage.TRUST.name)
            .putInt("encounter_future_creature", 12)
            .putString("custom_story_state", "alive")
            .commit()

        val report = SaveIntegrityManager.repair(context)

        assertEquals(SaveIntegrityStatus.CURRENT, report.status)
        assertEquals(4, SaveManager.loadEncounterCount(context, EntityType.CAT))
        assertEquals(RelationshipStage.TRUST, SaveManager.loadRelationshipStage(context, EntityType.CAT))
        assertEquals(12, prefs.getInt("encounter_future_creature", -1))
        assertEquals("alive", prefs.getString("custom_story_state", null))
    }

    @Test
    fun `persistent write APIs clamp invalid values and counters saturate`() {
        SaveManager.saveHighScore(context, -5)
        SaveManager.saveBestDistance(context, Float.POSITIVE_INFINITY)
        SaveManager.saveLifetimeSeeds(context, -9)
        SaveManager.saveGardenProgress(context, 999)
        prefs.edit().putInt("encounter_fox", Int.MAX_VALUE).commit()
        SaveManager.incrementEncounterCount(context, EntityType.FOX)

        assertEquals(0, SaveManager.loadHighScore(context))
        assertEquals(0f, SaveManager.loadBestDistance(context), 0f)
        assertEquals(0, SaveManager.loadLifetimeSeeds(context))
        assertEquals(9, SaveManager.loadGardenProgress(context))
        assertEquals(Int.MAX_VALUE, SaveManager.loadEncounterCount(context, EntityType.FOX))
    }
}
