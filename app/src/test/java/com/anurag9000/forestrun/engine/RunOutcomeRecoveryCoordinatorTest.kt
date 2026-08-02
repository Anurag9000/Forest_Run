package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.systems.GhostFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunOutcomeRecoveryCoordinatorTest {

    @Test
    fun `recoverable commit journals before side effects and clears after atomic snapshot`() {
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
        assertEquals(1, sink.routeTierCount)
        assertNull(store.record)
        assertEquals(
            listOf(
                "loadMood",
                "loadReturn",
                "loadRoute:MERCIFUL",
                "journal:PREPARED",
                "loadBestDistance",
                "publishGhost:2:480.0",
                "loadMood",
                "saveMood:1",
                "loadMood",
                "journal:MOOD_APPLIED",
                "loadReturn",
                "saveReturn:$FIXED_NOW_MS:0",
                "loadReturn",
                "journal:RETURN_APPLIED",
                "loadSummary",
                "loadRoute:MERCIFUL",
                "saveSnapshot:480.0:1",
                "loadSummary",
                "loadRoute:MERCIFUL",
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
            record = recoveryRecord(
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
        assertEquals(1, sink.routeTierCount)
        assertNull(store.record)
        assertFalse(sink.events.any { it.startsWith("saveMood:") })
    }

    @Test
    fun `startup recovery recognizes an applied atomic summary snapshot`() {
        val record = recoveryRecord(phase = RunOutcomeRecoveryPhase.RETURN_APPLIED)
        val store = MemoryRecoveryStore(record = record)
        val sink = RecoverableRecordingSink(
            recoveryStore = store,
            moodState = record.nextMood,
            returnState = record.nextReturn,
            lastSummary = RunOutcomeRecoveryTransitions.persistedSummary(record.summary),
            routeTierCount = record.nextRouteTierCount
        )

        RunOutcomePersistenceCoordinator(sink)

        assertNull(store.record)
        assertEquals(record.nextRouteTierCount, sink.routeTierCount)
        assertFalse(sink.events.any { it.startsWith("saveSnapshot:") })
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
    fun `conflicting route count blocks summary replay`() {
        val record = recoveryRecord(
            phase = RunOutcomeRecoveryPhase.RETURN_APPLIED,
            previousRouteTierCount = 2,
            nextRouteTierCount = 3
        )
        val store = MemoryRecoveryStore(record = record)
        val sink = RecoverableRecordingSink(
            recoveryStore = store,
            moodState = record.nextMood,
            returnState = record.nextReturn,
            routeTierCount = 9
        )
        val coordinator = RunOutcomePersistenceCoordinator(sink)

        val result = coordinator.commit(summary(), emptyList(), persistProgress = true)

        assertEquals(RunOutcomeCommitDisposition.RECOVERY_BLOCKED, result.disposition)
        assertEquals(9, sink.routeTierCount)
        assertNull(sink.lastSummary)
        assertFalse(sink.events.any { it.startsWith("saveSnapshot:") })
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
    fun `failed journal clear leaves recoverable snapshot and reset does not count twice`() {
        val store = MemoryRecoveryStore(clearSucceeds = false)
        val sink = RecoverableRecordingSink(store)
        val coordinator = RunOutcomePersistenceCoordinator(sink, clock = { FIXED_NOW_MS })

        val first = coordinator.commit(summary(), emptyList(), persistProgress = true)

        assertEquals(RunOutcomeCommitDisposition.RECOVERY_PENDING, first.disposition)
        assertEquals(RunOutcomeRecoveryPhase.SUMMARY_APPLIED, store.record?.phase)
        assertEquals(1, sink.moodState.totalRuns)
        assertEquals(1, sink.routeTierCount)

        store.clearSucceeds = true
        coordinator.resetForNewRun()

        assertNull(store.record)
        assertEquals(1, sink.moodState.totalRuns)
        assertEquals(1, sink.routeTierCount)
        assertEquals(summary(), sink.lastSummary)
        assertEquals(1, sink.events.count { it.startsWith("saveSnapshot:") })

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
        phase: RunOutcomeRecoveryPhase = RunOutcomeRecoveryPhase.PREPARED,
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
        ),
        previousRouteTierCount: Int = 0,
        nextRouteTierCount: Int = RunOutcomeRecoveryTransitions.nextRouteTierCount(
            previousRouteTierCount,
            summary.pacifistRouteTier
        )
    ): RunOutcomeRecoveryRecord = RunOutcomeRecoveryRecord(
        phase = phase,
        summary = summary,
        previousMood = previousMood,
        nextMood = nextMood,
        previousReturn = previousReturn,
        nextReturn = nextReturn,
        previousRouteTierCount = previousRouteTierCount,
        nextRouteTierCount = nextRouteTierCount
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
        forestMood = forestMood,
        pacifistRouteTier = PacifistRouteTier.MERCIFUL
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
        private val bestDistanceM: Float = 120f,
        var lastSummary: RunSummary? = null,
        var routeTierCount: Int = 0,
        val events: MutableList<String> = recoveryStore.events
    ) : RecoverableRunOutcomePersistenceSink {
        override val summarySnapshotStore: RunOutcomeSummarySnapshotStore =
            object : RunOutcomeSummarySnapshotStore {
                override fun save(summary: RunSummary, routeTierCount: Int): Boolean {
                    lastSummary = summary
                    this@RecoverableRecordingSink.routeTierCount = routeTierCount
                    events += "saveSnapshot:${summary.distanceM}:$routeTierCount"
                    return true
                }
            }

        override fun loadBestDistanceM(): Float {
            events += "loadBestDistance"
            return bestDistanceM
        }

        override fun publishBestGhost(
            frames: List<GhostFrame>,
            distanceM: Float
        ): Boolean {
            events += "publishGhost:${frames.size}:$distanceM"
            return true
        }

        override fun recordForestMood(summary: RunSummary) {
            error("recoverable path must not call legacy mood mutation")
        }

        override fun recordReturnMoment(summary: RunSummary) {
            error("recoverable path must not call legacy return mutation")
        }

        override fun saveLastRunSummary(summary: RunSummary) {
            error("recoverable path must use the atomic summary snapshot")
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

        override fun loadLastRunSummary(): RunSummary? {
            events += "loadSummary"
            return lastSummary
        }

        override fun loadRouteTierCount(tier: PacifistRouteTier): Int {
            events += "loadRoute:${tier.name}"
            return routeTierCount
        }
    }

    private companion object {
        const val FIXED_NOW_MS = 1_725_000_000_000L
    }
}
