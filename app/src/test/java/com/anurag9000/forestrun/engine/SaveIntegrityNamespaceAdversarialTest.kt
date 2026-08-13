package com.anurag9000.forestrun.engine

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SaveIntegrityNamespaceAdversarialTest {

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
        primary.edit().clear().commit()
        compatibility.edit().clear().commit()
    }

    @After
    fun tearDown() {
        SaveManager.usePrimaryPreferences()
        primary.edit().clear().commit()
        compatibility.edit().clear().commit()
    }

    @Test
    fun `future primary remains untouched while corrupt compatibility namespace is repaired`() {
        val futureVersion = SaveIntegrityManager.CURRENT_SCHEMA_VERSION + 7
        primary.edit()
            .putInt(SaveIntegrityManager.KEY_SCHEMA_VERSION, futureVersion)
            .putString("high_score", "future-owned-score")
            .putString("future_only_key", "preserve-primary")
            .commit()
        compatibility.edit()
            .putInt(SaveIntegrityManager.KEY_SCHEMA_VERSION, SaveIntegrityManager.CURRENT_SCHEMA_VERSION)
            .putString("high_score", "corrupt")
            .putInt("lifetime_seeds", -50)
            .putString("compat_future_key", "preserve-compat")
            .commit()
        val primaryBefore = primary.all.toMap()

        val report = SaveIntegrityManager.repair(context)

        assertEquals(SaveIntegrityStatus.FUTURE_VERSION, report.status)
        assertEquals(futureVersion, report.fromVersion)
        assertEquals(futureVersion, report.toVersion)
        assertEquals(primaryBefore, primary.all)
        assertEquals(
            "${SaveManager.PREFS_NAME}_compat_v${SaveIntegrityManager.CURRENT_SCHEMA_VERSION}",
            SaveManager.activePrefsNameForTests
        )
        assertEquals(0, SaveManager.loadHighScore(context))
        assertEquals(0, SaveManager.loadLifetimeSeeds(context))
        assertEquals(
            SaveIntegrityManager.CURRENT_SCHEMA_VERSION,
            compatibility.getInt(SaveIntegrityManager.KEY_SCHEMA_VERSION, -1)
        )
        assertEquals("preserve-compat", compatibility.getString("compat_future_key", null))
        assertTrue(report.repairedEntries >= 2)
    }

    @Test
    fun `malformed schema version is migrated as legacy without deleting unknown data`() {
        primary.edit()
            .putString(SaveIntegrityManager.KEY_SCHEMA_VERSION, "not-an-integer")
            .putString("high_score", "corrupt")
            .putString("unknown_future_key", "preserve-me")
            .commit()

        val report = SaveIntegrityManager.repair(context)

        assertEquals(SaveIntegrityStatus.MIGRATED, report.status)
        assertEquals(0, report.fromVersion)
        assertEquals(SaveIntegrityManager.CURRENT_SCHEMA_VERSION, report.toVersion)
        assertEquals(
            SaveIntegrityManager.CURRENT_SCHEMA_VERSION,
            primary.getInt(SaveIntegrityManager.KEY_SCHEMA_VERSION, -1)
        )
        assertEquals(0, SaveManager.loadHighScore(context))
        assertEquals("preserve-me", primary.getString("unknown_future_key", null))
    }
}
