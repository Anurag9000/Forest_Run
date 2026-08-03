package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FamiliarityWarmthIntegrationTest {

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
    fun `combined positive history reaches bonded authored line`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.CAT) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }
        repeat(5) { PersistentMemoryManager.recordPass(context, EntityType.CAT) }

        assertEquals(
            RelationshipStage.MILESTONE,
            RelationshipArcSystem.stageFor(context, EntityType.CAT)
        )
        assertEquals(
            "You came back to our quiet.",
            RelationshipArcSystem.lineFor(
                context,
                EntityType.CAT,
                RelationshipArcSystem.Event.PASS
            )
        )
    }

    @Test
    fun `each independent modifier can complete the bonded threshold`() {
        val withoutEncounterBonus = FamiliarityWarmthScoring.score(
            stage = RelationshipStage.MILESTONE,
            passCount = 5,
            sparedCount = 2,
            kindnessStreak = 3,
            encounters = 4
        )
        val withoutKindnessBonus = FamiliarityWarmthScoring.score(
            stage = RelationshipStage.MILESTONE,
            passCount = 5,
            sparedCount = 2,
            kindnessStreak = 2,
            encounters = 5
        )

        assertEquals(FamiliarityWarmthScoring.BONDED_THRESHOLD, withoutEncounterBonus)
        assertEquals(FamiliarityWarmthScoring.BONDED_THRESHOLD, withoutKindnessBonus)
        assertEquals(3, FamiliarityWarmthScoring.tierOrdinal(withoutEncounterBonus))
        assertEquals(3, FamiliarityWarmthScoring.tierOrdinal(withoutKindnessBonus))
    }
}
