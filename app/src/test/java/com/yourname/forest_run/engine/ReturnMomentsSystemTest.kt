package com.yourname.forest_run.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReturnMomentsSystemTest {

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
    fun `first garden open of a day gets greeting`() {
        val nowMs = 2 * 24L * 60L * 60L * 1_000L

        val moment = ReturnMomentsSystem.resolveGardenMoment(context, null, nowMs)

        assertEquals("Good To See You", moment?.title)
        assertNull(ReturnMomentsSystem.resolveGardenMoment(context, null, nowMs + 1_000L))
    }

    @Test
    fun `long absence return overrides normal daily greeting`() {
        SaveManager.saveReturnMomentState(
            context,
            ReturnMomentState(lastActiveAtMs = 1L, lastGardenGreetingDay = -1L, roughRunStreak = 0)
        )

        val nowMs = 3 * 24L * 60L * 60L * 1_000L
        val moment = ReturnMomentsSystem.resolveGardenMoment(context, null, nowMs)

        assertNotNull(moment)
        assertEquals("Welcome Back", moment?.title)
    }

    @Test
    fun `rough run streak creates comfort return moment`() {
        val summary = RunSummary(
            score = 220,
            distanceM = 210f,
            isNewHighScore = false,
            highScore = 800,
            mercyHearts = 0,
            mercyMisses = 0,
            kindnessChain = 0,
            cleanPasses = 1,
            sparedCount = 0,
            hitsTaken = 2,
            seedsCollected = 1,
            bloomConversions = 0,
            lastKiller = null,
            restQuote = "Not yet.",
            forestMood = ForestMood.FEARFUL
        )

        repeat(3) { index ->
            ReturnMomentsSystem.recordRunOutcome(context, summary, nowMs = 1_000L + index)
        }

        val moment = ReturnMomentsSystem.resolveGardenMoment(context, summary, nowMs = 4_000L)
        assertEquals("Take A Breath", moment?.title)
    }

    @Test
    fun `gentle high kindness milestone run can return stronger bonded moment`() {
        repeat(3) { PersistentMemoryManager.recordEncounter(context, EntityType.CAT) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }

        val summary = RunSummary(
            score = 1_050,
            distanceM = 720f,
            isNewHighScore = false,
            highScore = 1_300,
            mercyHearts = 4,
            mercyMisses = 5,
            kindnessChain = 7,
            cleanPasses = 8,
            sparedCount = 2,
            hitsTaken = 0,
            seedsCollected = 8,
            bloomConversions = 1,
            lastKiller = null,
            restQuote = "Softly.",
            forestMood = ForestMood.GENTLE
        )

        val moment = ReturnMomentsSystem.resolveGardenMoment(context, summary, nowMs = 7_000L)

        assertEquals("Kept Company", moment?.title)
        assertEquals(EntityType.CAT, moment?.visitor)
    }

    @Test
    fun `repeated harm creates a still tender return moment`() {
        repeat(2) { PersistentMemoryManager.recordHit(context, EntityType.OWL) }
        SaveManager.saveReturnMomentState(
            context,
            ReturnMomentState(lastActiveAtMs = 10_000L, lastGardenGreetingDay = -1L, roughRunStreak = 2)
        )
        val summary = RunSummary(
            score = 360,
            distanceM = 295f,
            isNewHighScore = false,
            highScore = 910,
            mercyHearts = 0,
            mercyMisses = 0,
            kindnessChain = 0,
            cleanPasses = 1,
            sparedCount = 0,
            hitsTaken = 1,
            seedsCollected = 2,
            bloomConversions = 0,
            lastKiller = EntityType.OWL,
            restQuote = "Again.",
            forestMood = ForestMood.FEARFUL
        )

        val moment = ReturnMomentsSystem.resolveGardenMoment(context, summary, nowMs = 12_000L)

        assertEquals("Still Tender", moment?.title)
        assertEquals(EntityType.OWL, moment?.visitor)
        assertNotNull(moment?.line)
    }

    @Test
    fun `repeat killer history escalates into same shadow return moment`() {
        repeat(3) { PersistentMemoryManager.recordHit(context, EntityType.WOLF) }
        val summary = RunSummary(
            score = 280,
            distanceM = 240f,
            isNewHighScore = false,
            highScore = 900,
            mercyHearts = 0,
            mercyMisses = 0,
            kindnessChain = 0,
            cleanPasses = 1,
            sparedCount = 0,
            hitsTaken = 1,
            seedsCollected = 2,
            bloomConversions = 0,
            lastKiller = EntityType.WOLF,
            restQuote = "Again.",
            forestMood = ForestMood.FEARFUL
        )

        val moment = ReturnMomentsSystem.resolveGardenMoment(context, summary, nowMs = 13_000L)

        assertEquals("Same Shadow", moment?.title)
        assertEquals(EntityType.WOLF, moment?.visitor)
        assertTrue(moment?.line?.contains("howl", ignoreCase = true) == true || moment?.line?.contains("same", ignoreCase = true) == true)
    }

    @Test
    fun `milestone gentle run returns kept company moment`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.FOX) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.FOX) }

        val summary = RunSummary(
            score = 1_220,
            distanceM = 860f,
            isNewHighScore = false,
            highScore = 1_500,
            mercyHearts = 4,
            mercyMisses = 4,
            kindnessChain = 6,
            cleanPasses = 9,
            sparedCount = 1,
            hitsTaken = 0,
            seedsCollected = 8,
            bloomConversions = 1,
            lastKiller = null,
            restQuote = "Softly.",
            forestMood = ForestMood.GENTLE
        )

        val moment = ReturnMomentsSystem.resolveGardenMoment(context, summary, nowMs = 8_000L)

        assertEquals("Kept Company", moment?.title)
        assertEquals(EntityType.FOX, moment?.visitor)
        assertTrue(moment?.line?.contains("fox", ignoreCase = true) == true || moment?.line?.contains("bright") == true)
    }

    @Test
    fun `strong bloom run returns bloom still clings moment`() {
        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.OWL) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.OWL) }

        val summary = RunSummary(
            score = 1_420,
            distanceM = 980f,
            isNewHighScore = false,
            highScore = 1_900,
            mercyHearts = 2,
            mercyMisses = 2,
            kindnessChain = 3,
            cleanPasses = 10,
            sparedCount = 0,
            hitsTaken = 0,
            seedsCollected = 12,
            bloomConversions = 4,
            lastKiller = null,
            restQuote = "Bright.",
            forestMood = ForestMood.STEADY
        )

        val moment = ReturnMomentsSystem.resolveGardenMoment(context, summary, nowMs = 9_000L)

        assertEquals("Bloom Still Clings", moment?.title)
        assertEquals(EntityType.OWL, moment?.visitor)
    }

    @Test
    fun `repeated kindness creates stayed gentle return moment`() {
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.DOG) }
        val summary = RunSummary(
            score = 920,
            distanceM = 700f,
            isNewHighScore = false,
            highScore = 1_400,
            mercyHearts = 3,
            mercyMisses = 3,
            kindnessChain = 5,
            cleanPasses = 8,
            sparedCount = 1,
            hitsTaken = 0,
            seedsCollected = 7,
            bloomConversions = 0,
            lastKiller = null,
            restQuote = "Gentle.",
            forestMood = ForestMood.GENTLE
        )

        val moment = ReturnMomentsSystem.resolveGardenMoment(context, summary, nowMs = 10_000L)

        assertEquals("Stayed Gentle", moment?.title)
        assertEquals(EntityType.DOG, moment?.visitor)
    }

    @Test
    fun `repeat friend creates a kept finding you return moment`() {
        repeat(3) { PersistentMemoryManager.recordEncounter(context, EntityType.DOG) }
        PersistentMemoryManager.recordSpare(context, EntityType.DOG)
        val summary = RunSummary(
            score = 1_020,
            distanceM = 760f,
            isNewHighScore = false,
            highScore = 1_450,
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

        val moment = ReturnMomentsSystem.resolveGardenMoment(context, summary, nowMs = 11_000L)

        assertEquals("Kept Finding You", moment?.title)
        assertEquals(EntityType.DOG, moment?.visitor)
        assertTrue(moment?.line?.contains("dog", ignoreCase = true) == true || moment?.line?.contains("habit", ignoreCase = true) == true)
    }

    @Test
    fun `peaceful route creates a dedicated return moment`() {
        val summary = RunSummary(
            score = 1_540,
            distanceM = 940f,
            isNewHighScore = false,
            highScore = 2_000,
            mercyHearts = 5,
            mercyMisses = 5,
            kindnessChain = 8,
            cleanPasses = 11,
            sparedCount = 2,
            hitsTaken = 0,
            seedsCollected = 12,
            bloomConversions = 1,
            lastKiller = null,
            restQuote = "Quietly.",
            forestMood = ForestMood.GENTLE,
            pacifistRouteTier = PacifistRouteTier.PEACEFUL
        )

        val moment = ReturnMomentsSystem.resolveGardenMoment(context, summary, nowMs = 12_000L)

        assertEquals("Peace Kept", moment?.title)
        assertTrue(moment?.line?.contains("peace", ignoreCase = true) == true || moment?.line?.contains("quiet", ignoreCase = true) == true)
    }

    @Test
    fun `kind route creates a dedicated return moment`() {
        val summary = RunSummary(
            score = 1_040,
            distanceM = 760f,
            isNewHighScore = false,
            highScore = 1_700,
            mercyHearts = 3,
            mercyMisses = 3,
            kindnessChain = 5,
            cleanPasses = 8,
            sparedCount = 1,
            hitsTaken = 0,
            seedsCollected = 7,
            bloomConversions = 0,
            lastKiller = null,
            restQuote = "Kindly.",
            forestMood = ForestMood.GENTLE,
            pacifistRouteTier = PacifistRouteTier.KIND
        )

        val moment = ReturnMomentsSystem.resolveGardenMoment(context, summary, nowMs = 14_000L)

        assertEquals("Kindness Stayed", moment?.title)
        assertTrue(moment?.line?.contains("kind", ignoreCase = true) == true || moment?.line?.contains("home", ignoreCase = true) == true)
    }

    @Test
    fun `preview garden moment does not mutate saved return state`() {
        val before = ReturnMomentState(lastActiveAtMs = 123L, lastGardenGreetingDay = 4L, roughRunStreak = 1)
        SaveManager.saveReturnMomentState(context, before)

        val preview = ReturnMomentsSystem.previewGardenMoment(context, null, nowMs = 8L * 24L * 60L * 60L * 1_000L)

        assertEquals("Welcome Back", preview?.title)
        assertEquals(before, SaveManager.loadReturnMomentState(context))
    }
}
