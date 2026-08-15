package com.anurag9000.forestrun.engine

import android.content.Context
import com.anurag9000.forestrun.entities.EntityType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForestJournalReadOnlyTest {
    private lateinit var context: Context
    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SaveManager.useCompatibilityPreferences(TEST_SCHEMA)
        prefs = context.getSharedPreferences(SaveManager.activePrefsNameForTests, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
        SaveManager.usePrimaryPreferences()
    }

    @Test
    fun `journal snapshot never materializes a missing relationship stage or milestone`() {
        repeat(6) { SaveManager.incrementEncounterCount(context, EntityType.FOX) }
        repeat(4) { SaveManager.incrementSparedCount(context, EntityType.FOX) }
        repeat(2) { SaveManager.incrementCleanPassCount(context, EntityType.FOX) }

        // This history is deliberately strong enough that the mutating gameplay
        // refresh path would promote Fox to MILESTONE. The test leaves stage
        // persistence absent to model an old/repaired save and prove that merely
        // opening the Journal does not perform that repair as a side effect.
        assertNull(SaveManager.loadRelationshipStage(context, EntityType.FOX))
        assertFalse(EntityType.FOX in SaveManager.loadUnlockedRelationshipMilestones(context))

        val before = immutablePreferenceSnapshot(prefs)
        val journal = ForestJournalComposer.snapshot(context)
        val collection = ForestCollectionProgressComposer.snapshot(context, journal)
        val after = immutablePreferenceSnapshot(prefs)

        val fox = journal.entries.single { it.type == EntityType.FOX }
        assertTrue(fox.discovered)
        assertEquals(RelationshipStage.FIRST_IMPRESSION, fox.relationshipStage)
        assertEquals(before, after)
        assertNull(SaveManager.loadRelationshipStage(context, EntityType.FOX))
        assertFalse(EntityType.FOX in SaveManager.loadUnlockedRelationshipMilestones(context))

        val bonds = collection.tracks.single { it.id == "bonds" }
        assertEquals(0, bonds.completed)
        assertEquals(6, bonds.total)
    }

    @Test
    fun `journal reports an already persisted Bond without rewriting it`() {
        repeat(6) { SaveManager.incrementEncounterCount(context, EntityType.OWL) }
        SaveManager.saveRelationshipStage(context, EntityType.OWL, RelationshipStage.MILESTONE)
        SaveManager.saveUnlockedRelationshipMilestones(context, setOf(EntityType.OWL))

        val before = immutablePreferenceSnapshot(prefs)
        val journal = ForestJournalComposer.snapshot(context)
        val collection = ForestCollectionProgressComposer.snapshot(context, journal)
        val after = immutablePreferenceSnapshot(prefs)

        assertEquals(
            RelationshipStage.MILESTONE,
            journal.entries.single { it.type == EntityType.OWL }.relationshipStage
        )
        assertEquals(1, collection.tracks.single { it.id == "bonds" }.completed)
        assertEquals(before, after)
    }

    private fun immutablePreferenceSnapshot(
        preferences: android.content.SharedPreferences
    ): Map<String, Any?> = preferences.all.mapValues { (_, value) ->
        when (value) {
            is Set<*> -> value.toSet()
            else -> value
        }
    }.toMap()

    private companion object {
        const val TEST_SCHEMA = 91_337
    }
}
