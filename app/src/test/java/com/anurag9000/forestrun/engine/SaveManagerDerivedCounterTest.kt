package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SaveManagerDerivedCounterTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `derived counter reads reject wrong types and negative values`() {
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("encounter_cat", "corrupt")
            .putInt("spared_cat", -50)
            .commit()

        assertEquals(0, SaveManager.loadEncounterCount(context, EntityType.CAT))
        assertEquals(0, SaveManager.loadSparedCount(context, EntityType.CAT))
    }

    @Test
    fun `extreme derived counters clamp below overflow and stay saturated`() {
        val prefs = context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("hit_wolf", Int.MAX_VALUE).commit()

        val capped = SaveManager.loadHitCount(context, EntityType.WOLF)
        assertTrue(capped > 0)
        assertTrue(capped < Int.MAX_VALUE)

        SaveManager.incrementHitCount(context, EntityType.WOLF)

        assertEquals(capped, SaveManager.loadHitCount(context, EntityType.WOLF))
        assertEquals(capped, prefs.getInt("hit_wolf", 0))
    }
}
