package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.systems.GhostFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunOutcomePersistenceCoordinatorTest {

    @Test
    fun `normal terminal outcome commits once in canonical order`() {
        val sink = RecordingSink(bestDistanceM = 120f)
        val coordinator = RunOutcomePersistenceCoordinator(sink)
        val summary = summary(distanceM = 480f)
        val ghost = ghostFrames()

        val first = coordinator.commit(summary, ghost, persistProgress = true)
        val second = coordinator.commit(summary.copy(distanceM = 900f), ghost, persistProgress = true)

        assertEquals(RunOutcomeCommitDisposition.COMMITTED, first.disposition)
        assertTrue(first.committed)
        assertTrue(first.ghostPromoted)
        assertEquals(RunOutcomeCommitDisposition.ALREADY_COMMITTED, second.disposition)
        assertFalse(second.committed)
        assertEquals(
            listOf(
                "loadBestDistance",
                "publishGhost:2",
                "saveBestDistance:480.0",
                "recordForestMood:480.0",
                "recordReturnMoment:480.0",
                "saveLastRunSummary:480.0"
            ),
            sink.calls
        )
    }

    @Test
    fun `nonpersistent terminal outcome consumes token without writes`() {
        val sink = RecordingSink(bestDistanceM = 0f)
        val coordinator = RunOutcomePersistenceCoordinator(sink)

        val skipped = coordinator.commit(summary(400f), ghostFrames(), persistProgress = false)
        val laterModeChange = coordinator.commit(summary(400f), ghostFrames(), persistProgress = true)

        assertEquals(RunOutcomeCommitDisposition.NON_PERSISTENT_RUN, skipped.disposition)
        assertEquals(RunOutcomeCommitDisposition.ALREADY_COMMITTED, laterModeChange.disposition)
        assertTrue(sink.calls.isEmpty())
    }

    @Test
    fun `new run reset reopens exactly once ownership`() {
        val sink = RecordingSink(bestDistanceM = 1_000f)
        val coordinator = RunOutcomePersistenceCoordinator(sink)

        coordinator.commit(summary(300f), ghostFrames(), persistProgress = false)
        coordinator.resetForNewRun()
        val committed = coordinator.commit(summary(300f), ghostFrames(), persistProgress = true)

        assertEquals(RunOutcomeCommitDisposition.COMMITTED, committed.disposition)
        assertFalse(committed.ghostPromoted)
        assertEquals(
            listOf(
                "loadBestDistance",
                "recordForestMood:300.0",
                "recordReturnMoment:300.0",
                "saveLastRunSummary:300.0"
            ),
            sink.calls
        )
    }

    @Test
    fun `non best run persists summary without replacing ghost`() {
        val sink = RecordingSink(bestDistanceM = 600f)
        val coordinator = RunOutcomePersistenceCoordinator(sink)

        val result = coordinator.commit(summary(600f), ghostFrames(), persistProgress = true)

        assertFalse(result.ghostPromoted)
        assertEquals(
            listOf(
                "loadBestDistance",
                "recordForestMood:600.0",
                "recordReturnMoment:600.0",
                "saveLastRunSummary:600.0"
            ),
            sink.calls
        )
    }

    @Test
    fun `rejected ghost does not advance best distance threshold`() {
        val sink = RecordingSink(bestDistanceM = 100f, publishGhostResult = false)
        val coordinator = RunOutcomePersistenceCoordinator(sink)

        val result = coordinator.commit(summary(700f), ghostFrames(), persistProgress = true)

        assertFalse(result.ghostPromoted)
        assertEquals(
            listOf(
                "loadBestDistance",
                "publishGhost:2",
                "recordForestMood:700.0",
                "recordReturnMoment:700.0",
                "saveLastRunSummary:700.0"
            ),
            sink.calls
        )
    }

    @Test
    fun `malformed completed distance cannot promote ghost`() {
        val sink = RecordingSink(bestDistanceM = Float.NaN)
        val coordinator = RunOutcomePersistenceCoordinator(sink)

        val result = coordinator.commit(summary(Float.NaN), ghostFrames(), persistProgress = true)

        assertFalse(result.ghostPromoted)
        assertEquals(
            listOf(
                "loadBestDistance",
                "recordForestMood:NaN",
                "recordReturnMoment:NaN",
                "saveLastRunSummary:NaN"
            ),
            sink.calls
        )
    }

    private fun summary(distanceM: Float): RunSummary = RunSummary(
        score = 1_200,
        distanceM = distanceM,
        isNewHighScore = false,
        highScore = 2_000,
        mercyHearts = 3,
        mercyMisses = 1,
        kindnessChain = 4,
        cleanPasses = 8,
        sparedCount = 2,
        hitsTaken = 1,
        seedsCollected = 10,
        bloomConversions = 2,
        lastKiller = null,
        restQuote = "Rest.",
        forestMood = ForestMood.STEADY
    )

    private fun ghostFrames(): List<GhostFrame> = listOf(
        GhostFrame(0f, 100f, 200f, 0, 1f, 1f),
        GhostFrame(0.04f, 104f, 196f, 1, 0.98f, 1.02f)
    )

    private class RecordingSink(
        private var bestDistanceM: Float,
        private val publishGhostResult: Boolean = true
    ) : RunOutcomePersistenceSink {
        val calls = mutableListOf<String>()

        override fun loadBestDistanceM(): Float {
            calls += "loadBestDistance"
            return bestDistanceM
        }

        override fun publishBestGhost(frames: List<GhostFrame>): Boolean {
            calls += "publishGhost:${frames.size}"
            return publishGhostResult
        }

        override fun saveBestDistanceM(distanceM: Float) {
            bestDistanceM = distanceM
            calls += "saveBestDistance:$distanceM"
        }

        override fun recordForestMood(summary: RunSummary) {
            calls += "recordForestMood:${summary.distanceM}"
        }

        override fun recordReturnMoment(summary: RunSummary) {
            calls += "recordReturnMoment:${summary.distanceM}"
        }

        override fun saveLastRunSummary(summary: RunSummary) {
            calls += "saveLastRunSummary:${summary.distanceM}"
        }
    }
}
