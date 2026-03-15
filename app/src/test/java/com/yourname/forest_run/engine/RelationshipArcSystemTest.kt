package com.yourname.forest_run.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RelationshipArcSystemTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `tracked encounters progress cat into recognition and trust`() {
        PersistentMemoryManager.recordEncounter(context, EntityType.CAT)
        PersistentMemoryManager.recordEncounter(context, EntityType.CAT)

        assertEquals(RelationshipStage.RECOGNITION, RelationshipArcSystem.stageFor(context, EntityType.CAT))

        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }

        assertEquals(RelationshipStage.TRUST, RelationshipArcSystem.stageFor(context, EntityType.CAT))
    }

    @Test
    fun `wolf can reach milestone bond through encounters and spares`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.WOLF) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.WOLF) }

        assertEquals(RelationshipStage.MILESTONE, RelationshipArcSystem.stageFor(context, EntityType.WOLF))
    }

    @Test
    fun `strongest relationship label returns formatted bond`() {
        repeat(3) { PersistentMemoryManager.recordEncounter(context, EntityType.FOX) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.FOX) }

        val label = RelationshipArcSystem.strongestRelationshipLabel(context)

        assertTrue(label!!.startsWith("Fox"))
        assertTrue(label.contains("Trust") || label.contains("Bond"))
    }

    @Test
    fun `preferred garden visitor requires trust or better`() {
        repeat(3) { PersistentMemoryManager.recordEncounter(context, EntityType.CAT) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }

        assertEquals(EntityType.CAT, RelationshipArcSystem.preferredGardenVisitor(context))
        assertNotNull(RelationshipArcSystem.creatureThought(context, EntityType.CAT))
    }
}
