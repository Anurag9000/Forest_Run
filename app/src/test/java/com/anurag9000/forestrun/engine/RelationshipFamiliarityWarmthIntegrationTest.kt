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
class RelationshipFamiliarityWarmthIntegrationTest {

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
    fun `all independent warmth modifiers reach bonded authored thought`() {
        SaveManager.saveRelationshipStage(context, EntityType.CAT, RelationshipStage.TRUST)
        repeat(5) { SaveManager.incrementEncounterCount(context, EntityType.CAT) }
        repeat(5) { SaveManager.incrementCleanPassCount(context, EntityType.CAT) }
        repeat(2) { SaveManager.incrementSparedCount(context, EntityType.CAT) }
        repeat(3) { SaveManager.incrementKindnessStreak(context, EntityType.CAT) }

        assertEquals(
            "The cat has started keeping your quiet ready before you arrive.",
            RelationshipArcSystem.creatureThought(context, EntityType.CAT)
        )
    }

    @Test
    fun `personal tier remains distinct from bonded tier`() {
        SaveManager.saveRelationshipStage(context, EntityType.CAT, RelationshipStage.TRUST)
        repeat(5) { SaveManager.incrementEncounterCount(context, EntityType.CAT) }
        repeat(3) { SaveManager.incrementCleanPassCount(context, EntityType.CAT) }
        repeat(3) { SaveManager.incrementKindnessStreak(context, EntityType.CAT) }

        assertEquals(
            "The cat has stopped leaving and started expecting your step.",
            RelationshipArcSystem.creatureThought(context, EntityType.CAT)
        )
    }

    @Test
    fun `non warm tone does not receive familiarity warmth`() {
        SaveManager.saveRelationshipStage(context, EntityType.CAT, RelationshipStage.TRUST)
        repeat(5) { SaveManager.incrementEncounterCount(context, EntityType.CAT) }
        repeat(5) { SaveManager.incrementCleanPassCount(context, EntityType.CAT) }

        assertEquals(
            "The cat waits, but not too close.",
            RelationshipArcSystem.creatureThought(context, EntityType.CAT)
        )
    }
}
