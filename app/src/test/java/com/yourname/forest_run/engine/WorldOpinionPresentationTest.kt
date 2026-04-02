package com.yourname.forest_run.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorldOpinionPresentationTest {

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
    fun `repeated kindness resolves trusting world opinion`() {
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }
        SaveManager.saveForestMoodState(
            context,
            ForestMoodState(currentMood = ForestMood.GENTLE, moodStreak = 3, totalRuns = 3, gentleRuns = 3)
        )

        val opinion = WorldOpinionPresentation.current(context)

        assertEquals("Trusting", opinion?.label)
        assertTrue(opinion?.line?.contains("Cat", ignoreCase = true) == true)
        assertEquals("Trust held", opinion?.runBubbleText)
    }

    @Test
    fun `repeat killer resolves shadowed world opinion`() {
        repeat(3) { PersistentMemoryManager.recordHit(context, EntityType.OWL) }
        SaveManager.saveForestMoodState(
            context,
            ForestMoodState(currentMood = ForestMood.FEARFUL, moodStreak = 3, totalRuns = 3, fearfulRuns = 3)
        )

        val opinion = WorldOpinionPresentation.current(context)

        assertEquals("Shadowed", opinion?.label)
        assertTrue(opinion?.line?.contains("shadow", ignoreCase = true) == true)
        assertEquals("World wary", opinion?.runBubbleText)
    }
}
