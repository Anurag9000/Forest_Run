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

    @Test
    fun `menu copy carries repeat friend warmth forward`() {
        repeat(3) { PersistentMemoryManager.recordEncounter(context, EntityType.DOG) }
        PersistentMemoryManager.recordSpare(context, EntityType.DOG)
        SaveManager.saveLastRunSummary(
            context,
            RunSummary(
                score = 1_010,
                distanceM = 770f,
                isNewHighScore = false,
                highScore = 1_420,
                mercyHearts = 2,
                mercyMisses = 2,
                kindnessChain = 5,
                cleanPasses = 9,
                sparedCount = 1,
                hitsTaken = 0,
                seedsCollected = 7,
                bloomConversions = 0,
                lastKiller = null,
                restQuote = "Gladly.",
                forestMood = ForestMood.GENTLE
            )
        )

        val copy = SessionArcComposer.menuCopy(context)

        assertTrue(copy.atmosphereLine.contains("Dog", ignoreCase = true) || copy.atmosphereLine.contains("familiar", ignoreCase = true))
    }

    @Test
    fun `menu and rest copy acknowledge peaceful bloom afterglow`() {
        val summary = RunSummary(
            score = 1_420,
            distanceM = 980f,
            isNewHighScore = false,
            highScore = 1_880,
            mercyHearts = 5,
            mercyMisses = 5,
            kindnessChain = 8,
            cleanPasses = 11,
            sparedCount = 2,
            hitsTaken = 0,
            seedsCollected = 10,
            bloomConversions = 3,
            lastKiller = null,
            restQuote = "Quietly.",
            forestMood = ForestMood.GENTLE,
            pacifistRouteTier = PacifistRouteTier.PEACEFUL
        )
        SaveManager.saveLastRunSummary(context, summary)

        val menuCopy = SessionArcComposer.menuCopy(context)
        val restCopy = SessionArcComposer.restCopy(context, summary)

        assertTrue(menuCopy.atmosphereLine.contains("Bloom") || menuCopy.atmosphereLine.contains("hush"))
        assertTrue(restCopy.subtitle.contains("Bloom") || restCopy.carryHomeLine.contains("Bloom"))
    }

    @Test
    fun `milestone home presence can carry menu and rest copy`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.OWL) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.OWL) }
        val summary = RunSummary(
            score = 910,
            distanceM = 620f,
            isNewHighScore = false,
            highScore = 1_340,
            mercyHearts = 2,
            mercyMisses = 2,
            kindnessChain = 4,
            cleanPasses = 7,
            sparedCount = 1,
            hitsTaken = 0,
            seedsCollected = 6,
            bloomConversions = 0,
            lastKiller = null,
            restQuote = "Quietly.",
            forestMood = ForestMood.STEADY
        )
        SaveManager.saveLastRunSummary(context, summary)

        val menuCopy = SessionArcComposer.menuCopy(context)
        val restCopy = SessionArcComposer.restCopy(context, summary)

        assertTrue(menuCopy.atmosphereLine.contains("night", ignoreCase = true) || menuCopy.atmosphereLine.contains("dark edge", ignoreCase = true))
        assertTrue(restCopy.carryHomeLine.contains("night", ignoreCase = true) || restCopy.carryHomeLine.contains("dark edge", ignoreCase = true))
    }

    @Test
    fun `menu and rest copy can name a peaceful biome world-state sign`() {
        repeat(2) { PersistentMemoryManager.recordBiomeFriendship(context, Biome.ORCHARD) }
        val summary = RunSummary(
            score = 1_360,
            distanceM = 920f,
            isNewHighScore = false,
            highScore = 1_700,
            mercyHearts = 5,
            mercyMisses = 5,
            kindnessChain = 8,
            cleanPasses = 12,
            sparedCount = 2,
            hitsTaken = 0,
            seedsCollected = 10,
            bloomConversions = 2,
            lastKiller = null,
            restQuote = "Quietly.",
            forestMood = ForestMood.GENTLE,
            pacifistRouteTier = PacifistRouteTier.PEACEFUL
        )
        SaveManager.saveLastRunSummary(context, summary)

        val menuCopy = SessionArcComposer.menuCopy(context)
        val restCopy = SessionArcComposer.restCopy(context, summary)

        assertTrue(menuCopy.atmosphereLine.contains("Orchard", ignoreCase = true))
        assertTrue(restCopy.carryHomeLine.contains("Orchard", ignoreCase = true))
    }
}
