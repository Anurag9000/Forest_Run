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
class GameStateEncounterSelectionIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `guided opening emits every authorized family before repeating`() {
        val state = GameStateManager(context)
        val expected = setOf(
            EntityType.DUCK,
            EntityType.LILY_OF_VALLEY,
            EntityType.CAT,
            EntityType.TIT,
            EntityType.CACTUS
        )

        val firstCycle = List(expected.size) {
            state.openingSpawnPool(EntityType.entries.toList()).single()
        }

        assertEquals(expected, firstCycle.toSet())
        assertEquals(expected.size, firstCycle.distinct().size)
    }

    @Test
    fun `post guide selection exhausts the live pool and never leaks another family`() {
        val state = GameStateManager(context)
        val pool = listOf(EntityType.FOX, EntityType.OWL, EntityType.DOG)
        state.update(28f)

        repeat(20) {
            val cycle = List(pool.size) { state.openingSpawnPool(pool).single() }
            assertEquals(pool.toSet(), cycle.toSet())
            assertEquals(pool.size, cycle.distinct().size)
            assertTrue(cycle.all { it in pool })
        }
    }

    @Test
    fun `run reset returns selection to the guided opening contract`() {
        val state = GameStateManager(context)
        val latePool = listOf(EntityType.FOX, EntityType.OWL)
        state.update(28f)
        repeat(6) { state.openingSpawnPool(latePool) }

        state.resetRun()

        val selected = List(5) {
            state.openingSpawnPool(latePool).single()
        }.toSet()
        assertEquals(
            setOf(
                EntityType.DUCK,
                EntityType.LILY_OF_VALLEY,
                EntityType.CAT,
                EntityType.TIT,
                EntityType.CACTUS
            ),
            selected
        )
    }
}
