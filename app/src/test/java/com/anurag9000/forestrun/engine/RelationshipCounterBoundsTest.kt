package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RelationshipCounterBoundsTest {
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

    @After
    fun tearDown() {
        SaveManager.usePrimaryPreferences()
    }

    @Test
    fun `raw maximum counters remain bounded through relationship scoring`() {
        val prefs = context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("encounter_cat", Int.MAX_VALUE)
            .putInt("clean_pass_cat", Int.MAX_VALUE)
            .putInt("spared_cat", Int.MAX_VALUE)
            .putInt("kindness_streak_cat", Int.MAX_VALUE)
            .putInt("hit_cat", 0)
            .commit()

        val encounters = SaveManager.loadEncounterCount(context, EntityType.CAT)
        val cleanPasses = SaveManager.loadCleanPassCount(context, EntityType.CAT)
        val spared = SaveManager.loadSparedCount(context, EntityType.CAT)

        assertTrue(encounters in 1 until Int.MAX_VALUE)
        assertEquals(encounters, cleanPasses)
        assertEquals(encounters, spared)
        assertEquals(
            RelationshipStage.MILESTONE,
            RelationshipArcSystem.refreshStage(context, EntityType.CAT)
        )
        assertEquals(EntityType.CAT, RelationshipArcSystem.strongestRelationship(context)?.first)

        val tuning = RelationshipArcSystem.encounterTuning(context, EntityType.CAT)
        assertTrue(tuning.passBonusPoints >= 0)
        assertTrue(tuning.passBonusSeeds >= 0)
        assertTrue(tuning.mercyPaddingBonusPx.isFinite())
        assertTrue(
            RelationshipArcSystem.lineFor(
                context,
                EntityType.CAT,
                RelationshipArcSystem.Event.PASS
            ).isNotBlank()
        )
    }

    @Test
    fun `maximum hostile counters cannot overflow strained severity`() {
        val prefs = context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("encounter_wolf", Int.MAX_VALUE)
            .putInt("clean_pass_wolf", 1)
            .putInt("spared_wolf", 0)
            .putInt("hit_wolf", Int.MAX_VALUE)
            .putInt("tender_streak_wolf", Int.MAX_VALUE)
            .putInt("kindness_streak_wolf", 0)
            .commit()

        assertEquals(
            RelationshipStage.RECOGNITION,
            RelationshipArcSystem.refreshStage(context, EntityType.WOLF)
        )
        assertTrue(RelationshipArcSystem.isStrainedBond(context, EntityType.WOLF))
        assertTrue(RelationshipArcSystem.strainedBondLine(context, EntityType.WOLF).isNotBlank())
        assertTrue(
            RelationshipArcSystem.encounterCueLine(
                context,
                EntityType.WOLF,
                RelationshipArcSystem.EncounterCue.WOLF_CHARGE
            ).isNotBlank()
        )
    }
}
