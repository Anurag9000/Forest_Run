package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.PlayerState
import com.anurag9000.forestrun.systems.AtomicFileGhostArtifactManifestStore
import com.anurag9000.forestrun.systems.GhostArtifactManifest
import com.anurag9000.forestrun.systems.GhostArtifactManifestLoadResult
import com.anurag9000.forestrun.systems.GhostFrame
import com.anurag9000.forestrun.systems.GhostPersistenceManager
import com.anurag9000.forestrun.systems.GhostPromotionRecoveryDisposition
import com.anurag9000.forestrun.systems.GhostRunIdentity
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        deletePromotionFiles()
        deleteManifestFiles()
        GhostPersistenceManager.clearMemoryForTests()
    }

    @After
    fun tearDown() {
        GhostPersistenceManager.awaitPendingWrites()
        GhostPersistenceManager.clearMemoryForTests()
        deleteGhostFiles()
        deletePromotionFiles()
        deleteManifestFiles()
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
        assertEquals(480f, GhostPersistenceManager.bestDistanceFloor(context), 0f)
        assertTrue(GhostPersistenceManager.awaitPendingWrites())

        assertEquals(480f, SaveManager.loadBestDistance(context), 0f)
        assertEquals(ghost, SaveManager.loadGhostRun(context))
        assertEquals(
            GhostArtifactManifestLoadResult.Present(manifest(ghost, 480f)),
            manifestStore().load()
        )
        assertEquals(56L, manifestFile().length())
        assertEquals(
            GhostPromotionRecoveryDisposition.ALREADY_APPLIED,
            GhostPersistenceManager.recoverPendingPromotion(context)
        )
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
        assertEquals(GhostArtifactManifestLoadResult.Empty, manifestStore().load())
        assertEquals(summary, SaveManager.loadLastRunSummary(context))
        assertEquals(1, SaveManager.loadForestMoodState(context).totalRuns)
    }

    @Test
    fun `pending accepted distance prevents a shorter next run from replacing ghost`() {
        val firstCoordinator = RunOutcomePersistenceCoordinator(
            AndroidRunOutcomePersistenceSink(context)
        )
        val firstGhost = ghostFrames()
        assertTrue(
            firstCoordinator.commit(
                summary(distanceM = 900f),
                firstGhost,
                persistProgress = true
            ).ghostPromoted
        )

        val secondCoordinator = RunOutcomePersistenceCoordinator(
            AndroidRunOutcomePersistenceSink(context)
        )
        val shorterGhost = firstGhost.map { it.copy(x = it.x + 30f) }
        val shorter = secondCoordinator.commit(
            summary(distanceM = 700f),
            shorterGhost,
            persistProgress = true
        )

        assertFalse(shorter.ghostPromoted)
        assertEquals(900f, GhostPersistenceManager.bestDistanceFloor(context), 0f)
        assertTrue(GhostPersistenceManager.awaitPendingWrites())
        assertEquals(900f, SaveManager.loadBestDistance(context), 0f)
        assertEquals(firstGhost, SaveManager.loadGhostRun(context))
        assertEquals(
            GhostArtifactManifestLoadResult.Present(manifest(firstGhost, 900f)),
            manifestStore().load()
        )
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
        assertEquals(GhostArtifactManifestLoadResult.Empty, manifestStore().load())
        assertNull(SaveManager.loadLastRunSummary(context))
        assertEquals(0, SaveManager.loadForestMoodState(context).totalRuns)
        assertEquals(0L, SaveManager.loadReturnMomentState(context).lastActiveAtMs)
    }

    private fun manifest(
        frames: List<GhostFrame>,
        distanceM: Float
    ): GhostArtifactManifest {
        val identity = GhostRunIdentity.calculate(frames)
        return GhostArtifactManifest(
            distanceM = distanceM,
            frameCount = frames.size,
            fingerprint = identity.fingerprint,
            sha256Hex = identity.sha256Hex
        )
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

    private fun manifestStore(): AtomicFileGhostArtifactManifestStore =
        AtomicFileGhostArtifactManifestStore(
            context = context,
            ghostFilename = SaveManager.activeGhostFilenameForTests
        )

    private fun ghostFile(): File =
        File(context.filesDir, SaveManager.activeGhostFilenameForTests)

    private fun promotionFile(): File =
        File(context.filesDir, "${SaveManager.activeGhostFilenameForTests}.promotion")

    private fun manifestFile(): File =
        File(context.filesDir, "${SaveManager.activeGhostFilenameForTests}.manifest")

    private fun deleteGhostFiles() {
        val base = ghostFile()
        base.delete()
        File(base.path + ".bak").delete()
        File(base.path + ".new").delete()
    }

    private fun deletePromotionFiles() {
        val base = promotionFile()
        base.delete()
        File(base.path + ".bak").delete()
        File(base.path + ".new").delete()
    }

    private fun deleteManifestFiles() {
        val base = manifestFile()
        base.delete()
        File(base.path + ".bak").delete()
        File(base.path + ".new").delete()
    }
}
