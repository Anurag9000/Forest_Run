package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.systems.GhostFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunOutcomeRecoveryCoordinatorTest {

    @Test
    fun `recoverable commit journals before side effects and clears after summary`() {
        val events = mutableListOf<String>()
        val store = MemoryRecoveryStore(events = events)
        val sink = RecoverableRecordingSink(store, events = events)
        val coordinator = RunOutcomePersistenceCoordinator(sink, clock = { FIXED_NOW_MS })

        val result = coordinator.commit(summary(), ghostFrames(), persistProgress = true)

        assertEquals(RunOutcomeCommitDisposition.COMMITTED, result.disposition)
        assertTrue(result.committed)
        assertTrue(result.ghostPromoted)
        assertEquals(1, sink.moodState.totalRuns)
        assertEquals(1, sink.moodState.steadyRuns)
        assertEquals(FIXED_NOW_MS, sink.returnState.lastActiveAtMs)
        assertEquals(0, sink.returnState.roughRunStreak)
        assertEquals(summary(), sink.lastSummary)
        assertNull(store.record)
        assertEquals(
            listOf(
                "loadMood",
                "loadReturn",
                "journal:PREPARED",
                "loadBestDistance",
                "publishGhost:2",
                "saveBestDistance:480.0",
                "loadMood",
                "saveMood:1",
                "loadMood",
                "journal:MOOD_APPLIED",
                "loadReturn",
                "saveReturn:$FIXED_NOW_MS:0",
                "loadReturn",
                "journal:RETURN_APPLIED",
                "saveSummary:480.0",
                "journal:SUMMARY_APPLIED",
                "journal:clear"
            ),
            events
        )
    }

    @Test
    fun `startup recovery recognizes an applied mood write without incrementing twice`() {
        val previousMood = ForestMoodState()
        val nextMood = RunOutcomeRecoveryTransitions.nextForestMood(previousMood, summary())
        val previousReturn = ReturnMomentState()
        val nextReturn = RunOutcomeRecoveryTransitions.nextReturnMoment(
            previousReturn,
            summary(),
            FIXED_NOW_MS
        )
        val store = MemoryRecoveryStore(
            record = RunOutcomeRecoveryRecord(
                phase = RunOutcomeRecoveryPhase.PREPARED,
                summary = summary(),
                previousMood = previousMood,
                nextMood = nextMood,
                previousReturn = previousReturn,
                nextReturn = nextReturn
            )
        )
        val sink = RecoverableRecordingSink(
            recoveryStore = store,
            moodState = nextMood,
            returnState = previousReturn
        )

        RunOutcomePersistenceCoordinator(sink, clock = { 99L })

        assertEquals(nextMood, sink.moodState)
        assertEquals(1, sink.moodState.totalRuns)
        assertEquals(nextReturn, sink.returnState)
        assertEquals(summary(), sink.lastSummary)
        assertNull(store.record)
        assertFalse(sink.events.any { it.startsWith("saveMood:") })
    }

    @Test
    fun `conflicting progression state blocks recovery and future persistence`() {
        val previousMood = ForestMoodState()
        val nextMood = RunOutcomeRecoveryTransitions.nextForestMood(previousMood, summary())
        val record = recoveryRecord(previousMood = previousMood, nextMood = nextMood)
        val store = MemoryRecoveryStore(record = record)
        val conflictingMood = previousMood.copy(totalRuns = 7, steadyRuns = 7)
        val sink = RecoverableRecordingSink(
            recoveryStore = store,
            moodState = conflictingMood
        )
        val coordinator = RunOutcomePersistenceCoordinator(sink)

        val result = coordinator.commit(summary(), ghostFrames(), persistProgress = true)

        assertEquals(RunOutcomeCommitDisposition.RECOVERY_BLOCKED, result.disposition)
        assertFalse(result.committed)
        assertFalse(result.ghostPromoted)
        assertEquals(record, store.record)
        assertFalse(sink.events.any { it == "loadBestDistance" })
    }

    @Test
    fun `corrupt journal blocks writes without being erased`() {
        val store = MemoryRecoveryStore(loadResultOverride = RunOutcomeRecoveryLoadResult.Corrupt)
        val sink = RecoverableRecordingSink(store)
        val coordinator = RunOutcomePersistenceCoordinator(sink)

        val result = coordinator.commit(summary(), ghostFrames(), persistProgress = true)

        assertEquals(RunOutcomeCommitDisposition.RECOVERY_BLOCKED, result.disposition)
        assertTrue(store.journalCalls.isEmpty())
        assertFalse(sink.events.any { it == "loadBestDistance" })
    }

    @Test
    fun `failed journal clear leaves recoverable pending result and reset retries`() {
        val store = MemoryRecoveryStore(clearSucceeds = false)
        val sink = RecoverableRecordingSink(store)
        val coordinator = RunOutcomePersistenceCoordinator(sink, clock = { FIXED_NOW_MS })

        val first = coordinator.commit(summary(), emptyList(), persistProgress = true)

        assertEquals(RunOutcomeCommitDisposition.RECOVERY_PENDING, first.disposition)
        assertEquals(RunOutcomeRecoveryPhase.SUMMARY_APPLIED, store.record?.phase)
        assertEquals(1, sink.moodState.totalRuns)

        store.clearSucceeds = true
        coordinator.resetForNewRun()

        assertNull(store.record)
        assertEquals(1, sink.moodState.totalRuns)
        assertEquals(summary(), sink.lastSummary)

        val second = coordinator.commit(summary(distanceM = 300f), emptyList(), persistProgress = false)
        assertEquals(RunOutcomeCommitDisposition.NON_PERSISTENT_RUN, second.disposition)
    }

    @Test
    fun `rough run transition is recovered exactly once`() {
        val roughSummary = summary(
            distanceM = 300f,
            forestMood = ForestMood.FEARFUL,
            hitsTaken = 2,
            kindnessChain = 0,
            seedsCollected = 1
        )
        val previousReturn = ReturnMomentState(roughRunStreak = 4)
        val expectedReturn = RunOutcomeRecoveryTransitions.nextReturnMoment(
            previousReturn,
            roughSummary,
            FIXED_NOW_MS
        )
        val record = recoveryRecord(
            summary = roughSummary,
            previousReturn = previousReturn,
            nextReturn = expectedReturn
        )
        val store = MemoryRecoveryStore(record = record)
        val sink = RecoverableRecordingSink(
            recoveryStore = store,
            moodState = record.nextMood,
            returnState = expectedReturn
        )

        RunOutcomePersistenceCoordinator(sink)

        assertEquals(5, sink.returnState.roughRunStreak)
        assertNull(store.record)
        assertFalse(sink.events.any { it.startsWith("saveReturn:") })
    }

    private fun recoveryRecord(
        summary: RunSummary = summary(),
        previousMood: ForestMoodState = ForestMoodState(),
        nextMood: ForestMoodState = RunOutcomeRecoveryTransitions.nextForestMood(
            previousMood,
            summary
        ),
        previousReturn: ReturnMomentState = ReturnMomentState(),
        nextReturn: ReturnMomentState = RunOutcomeRecoveryTransitions.nextReturnMoment(
            previousReturn,
            summary,
            FIXED_NOW_MS
        )
    ): RunOutcomeRecoveryRecord = RunOutcomeRecoveryRecord(
        phase = RunOutcomeRecoveryPhase.PREPARED,
        summary = summary,
        previousMood = previousMood,
        nextMood = nextMood,
        previousReturn = previousReturn,
        nextReturn = nextReturn
    )

    private fun summary(
        distanceM: Float = 480f,
        forestMood: ForestMood = ForestMood.STEADY,
        hitsTaken: Int = 1,
        kindnessChain: Int = 4,
        seedsCollected: Int = 10
    ): RunSummary = RunSummary(
        score = 1_200,
        distanceM = distanceM,
        isNewHighScore = false,
        highScore = 2_000,
        mercyHearts = 3,
        mercyMisses = 1,
        kindnessChain = kindnessChain,
        cleanPasses = 8,
        sparedCount = 2,
        hitsTaken = hitsTaken,
        seedsCollected = seedsCollected,
        bloomConversions = 2,
        lastKiller = null,
        restQuote = "Rest.",
        forestMood = forestMood
    )

    private fun ghostFrames(): List<GhostFrame> = listOf(
        GhostFrame(0f, 100f, 200f, 0, 1f, 1f),
        GhostFrame(0.04f, 104f, 196f, 1, 0.98f, 1.02f)
    )

    private class MemoryRecoveryStore(
        var record: RunOutcomeRecoveryRecord? = null,
        private val loadResultOverride: RunOutcomeRecoveryLoadResult? = null,
        var clearSucceeds: Boolean = true,
        val events: MutableList<String> = mutableListOf()
    ) : RunOutcomeRecoveryStore {
        val journalCalls = mutableListOf<String>()

        override fun load(): RunOutcomeRecoveryLoadResult =
            loadResultOverride ?: record?.let(RunOutcomeRecoveryLoadResult::Pending)
            ?: RunOutcomeRecoveryLoadResult.Empty

        override fun save(record: RunOutcomeRecoveryRecord): Boolean {
            this.record = record
            val call = "journal:${record.phase.name}"
            journalCalls += call
            events += call
            return true
        }

        override fun clear(): Boolean {
            journalCalls += "journal:clear"
            events += "journal:clear"
            if (clearSucceeds) record = null
            return clearSucceeds
        }
    }

    private class RecoverableRecordingSink(
        override val recoveryStore: MemoryRecoveryStore,
        var moodState: ForestMoodState = ForestMoodState(),
        var returnState: ReturnMomentState = ReturnMomentState(),
        private var bestDistanceM: Float = 120f,
        val events: MutableList<String> = recoveryStore.events
    ) : RecoverableRunOutcomePersistenceSink {
        var lastSummary: RunSummary? = null
            private set

        override fun loadBestDistanceM(): Float {
            events += "loadBestDistance"
            return bestDistanceM
        }

        override fun publishBestGhost(frames: List<GhostFrame>): Boolean {
            events += "publishGhost:${frames.size}"
            return true
        }

        override fun saveBestDistanceM(distanceM: Float) {
            bestDistanceM = distanceM
            events += "saveBestDistance:$distanceM"
        }

        override fun recordForestMood(summary: RunSummary) {
            error("recoverable path must not call legacy mood mutation")
        }

        override fun recordReturnMoment(summary: RunSummary) {
            error("recoverable path must not call legacy return mutation")
        }

        override fun saveLastRunSummary(summary: RunSummary) {
            lastSummary = summary
            events += "saveSummary:${summary.distanceM}"
        }

        override fun loadForestMoodState(): ForestMoodState {
            events += "loadMood"
            return moodState
        }

        override fun saveForestMoodState(state: ForestMoodState) {
            moodState = state
            events += "saveMood:${state.totalRuns}"
        }

        override fun loadReturnMomentState(): ReturnMomentState {
            events += "loadReturn"
            return returnState
        }

        override fun saveReturnMomentState(state: ReturnMomentState) {
            returnState = state
            events += "saveReturn:${state.lastActiveAtMs}:${state.roughRunStreak}"
        }
    }

    private companion object {
        const val FIXED_NOW_MS = 1_725_000_000_000L
    }
}
