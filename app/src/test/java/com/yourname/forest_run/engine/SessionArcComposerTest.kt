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
class SessionArcComposerTest {

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
    fun `menu copy softens after a fearful run`() {
        SaveManager.saveLastRunSummary(
            context,
            RunSummary(
                score = 320,
                distanceM = 260f,
                isNewHighScore = false,
                highScore = 780,
                mercyHearts = 0,
                mercyMisses = 0,
                kindnessChain = 0,
                cleanPasses = 1,
                sparedCount = 0,
                hitsTaken = 2,
                seedsCollected = 2,
                bloomConversions = 0,
                lastKiller = EntityType.WOLF,
                restQuote = "Again.",
                forestMood = ForestMood.FEARFUL
            )
        )

        val copy = SessionArcComposer.menuCopy(context)

        assertEquals("tap when you're ready", copy.idlePrompt)
        assertTrue(copy.atmosphereLine.contains("voice low") || copy.idleSupportLine.contains("wait"))
    }

    @Test
    fun `rest copy previews the return home when a bonded moment is waiting`() {
        repeat(3) { PersistentMemoryManager.recordEncounter(context, EntityType.CAT) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }
        val summary = RunSummary(
            score = 980,
            distanceM = 640f,
            isNewHighScore = false,
            highScore = 1_300,
            mercyHearts = 3,
            mercyMisses = 3,
            kindnessChain = 6,
            cleanPasses = 8,
            sparedCount = 2,
            hitsTaken = 0,
            seedsCollected = 8,
            bloomConversions = 1,
            lastKiller = null,
            restQuote = "Softly.",
            forestMood = ForestMood.GENTLE
        )

        val copy = SessionArcComposer.restCopy(context, summary)

        assertEquals("tap to return home", copy.promptLine)
        assertTrue(copy.carryHomeLine.contains("cat", ignoreCase = true) || copy.carryHomeLine.contains("gentler", ignoreCase = true))
    }

    @Test
    fun `garden arrival line falls back to carry-home tone when no return moment is active`() {
        val summary = RunSummary(
            score = 760,
            distanceM = 580f,
            isNewHighScore = false,
            highScore = 1_100,
            mercyHearts = 1,
            mercyMisses = 1,
            kindnessChain = 2,
            cleanPasses = 6,
            sparedCount = 0,
            hitsTaken = 0,
            seedsCollected = 5,
            bloomConversions = 0,
            lastKiller = null,
            restQuote = "Steady.",
            forestMood = ForestMood.STEADY
        )
        val sanctuary = GardenSanctuaryPlanner.build(context, summary)

        val line = SessionArcComposer.gardenArrivalLine(summary, null, sanctuary)

        assertTrue(line.isNotBlank())
    }
}
