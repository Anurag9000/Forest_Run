package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.PlayerState
import com.anurag9000.forestrun.systems.GhostFrame
import com.anurag9000.forestrun.systems.GhostPersistenceManager
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RunOutcomePersistenceIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        deleteGhostFiles()
        GhostPersistenceManager.clearMemoryForTests()
    }

    @After
    fun tearDown() {
        GhostPersistenceManager.awaitPendingWrites()
        GhostPersistenceManager.clearMemoryForTests()
        deleteGhostFiles()
        SaveManager.usePrimaryPreferences()
    }

    @Test
    fun `real sink publishes one complete terminal outcome exactly once`() {
        val coordinator = RunOutcomePersistenceCoordinator(
            AndroidRunOutcomePersistenceSink(context)
        )
        val summary = summary(distanceM = 480f)
        val ghost = ghostFrames()

        val first = coordinator.commit(summary, ghost, persistProgress = true)
        val duplicate = coordinator.commit(
            summary.copy(distanceM = 900f, score = 9_000),
            ghost,
            persistProgress = true
        )

        assertTrue(first.committed)
        assertTrue(first.ghostPromoted)
        assertEquals(RunOutcomeCommitDisposition.ALREADY_COMMITTED, duplicate.disposition)
        assertTrue(GhostPersistenceManager.awaitPendingWrites())

        assertEquals(480f, SaveManager.loadBestDistance(context), 0f)
        assertEquals(ghost, SaveManager.loadGhostRun(context))
        assertEquals(summary, SaveManager.loadLastRunSummary(context))
        assertEquals(1, SaveManager.loadForestMoodState(context).totalRuns)
        val returnState = SaveManager.loadReturnMomentState(context)
        assertTrue(returnState.lastActiveAtMs > 0L)
        assertEquals(0, returnState.roughRunStreak)
    }

    @Test
    fun `empty ghost cannot advance best distance but summary still commits`() {
        val coordinator = RunOutcomePersistenceCoordinator(
            AndroidRunOutcomePersistenceSink(context)
        )
        val summary = summary(distanceM = 750f)

        val result = coordinator.commit(summary, emptyList(), persistProgress = true)

        assertTrue(result.committed)
        assertFalse(result.ghostPromoted)
        assertEquals(0f, SaveManager.loadBestDistance(context), 0f)
        assertFalse(SaveManager.hasGhostRun(context))
        assertEquals(summary, SaveManager.loadLastRunSummary(context))
        assertEquals(1, SaveManager.loadForestMoodState(context).totalRuns)
    }

    @Test
    fun `nonpersistent outcome cannot be retroactively committed`() {
        val coordinator = RunOutcomePersistenceCoordinator(
            AndroidRunOutcomePersistenceSink(context)
        )
        val summary = summary(distanceM = 600f)

        val skipped = coordinator.commit(summary, ghostFrames(), persistProgress = false)
        val retried = coordinator.commit(summary, ghostFrames(), persistProgress = true)

        assertEquals(RunOutcomeCommitDisposition.NON_PERSISTENT_RUN, skipped.disposition)
        assertEquals(RunOutcomeCommitDisposition.ALREADY_COMMITTED, retried.disposition)
        assertEquals(0f, SaveManager.loadBestDistance(context), 0f)
        assertFalse(SaveManager.hasGhostRun(context))
        assertEquals(null, SaveManager.loadLastRunSummary(context))
        assertEquals(0, SaveManager.loadForestMoodState(context).totalRuns)
        assertEquals(0L, SaveManager.loadReturnMomentState(context).lastActiveAtMs)
    }

    private fun summary(distanceM: Float): RunSummary = RunSummary(
        score = 1_800,
        distanceM = distanceM,
        isNewHighScore = true,
        highScore = 1_800,
        mercyHearts = 4,
        mercyMisses = 1,
        kindnessChain = 5,
        cleanPasses = 9,
        sparedCount = 2,
        hitsTaken = 1,
        seedsCollected = 12,
        bloomConversions = 3,
        lastKiller = null,
        restQuote = "The path is quiet now.",
        forestMood = ForestMood.STEADY,
        pacifistRouteTier = PacifistRouteTier.MERCIFUL
    )

    private fun ghostFrames(): List<GhostFrame> = listOf(
        GhostFrame(
            t = 0f,
            x = 120f,
            y = 600f,
            stateOrdinal = PlayerState.RUNNING.ordinal,
            scaleX = 1f,
            scaleY = 1f
        ),
        GhostFrame(
            t = 0.04f,
            x = 120f,
            y = 590f,
            stateOrdinal = PlayerState.JUMPING.ordinal,
            scaleX = 0.98f,
            scaleY = 1.02f
        )
    )

    private fun deleteGhostFiles() {
        val base = File(context.filesDir, SaveManager.activeGhostFilenameForTests)
        base.delete()
        File(base.path + ".bak").delete()
        File(base.path + ".new").delete()
    }
}
