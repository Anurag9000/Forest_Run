package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.CostumeStyle
import com.anurag9000.forestrun.systems.GhostFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationPersistenceFacadeTest {
    @Test
    fun delegatesEachDurabilityDomainWithoutCrossCallingOthers() {
        val runOutcomes = RecordingRunOutcomePort()
        val calls = mutableListOf<String>()
        val purchaseResult = GardenPurchaseResult(
            status = GardenPurchaseStatus.PURCHASED,
            unlockedCount = 2,
            remainingSeeds = 7
        )
        val feedbackResult = FeedbackPreferences(
            reducedMotion = true,
            audioEnabled = false,
            hapticsEnabled = true
        )
        val facade = ApplicationPersistenceFacade(
            runOutcomes = runOutcomes,
            purchaseGardenPlant = { index ->
                calls += "garden:$index"
                purchaseResult
            },
            feedbackWriter = { preferences ->
                calls += "feedback:$preferences"
                feedbackResult
            },
            wardrobeWriter = { style ->
                calls += "wardrobe:${style.name}"
                true
            },
            recoveryMaintenance = ThrowingRecoveryMaintenance(calls)
        )

        facade.resetForNewRun()
        assertEquals(1, runOutcomes.resetCount)
        assertTrue(calls.isEmpty())

        val summary = sampleSummary()
        val ghost = listOf(GhostFrame(0f, 320f, 720f, 0, 1f, 1f))
        val commit = facade.commitTerminalOutcome(summary, ghost, true)
        assertEquals(RunOutcomeCommitDisposition.COMMITTED, commit.disposition)
        assertSame(summary, runOutcomes.summary)
        assertEquals(ghost, runOutcomes.ghost)
        assertTrue(runOutcomes.persistProgress)
        assertTrue(calls.isEmpty())

        assertSame(purchaseResult, facade.purchaseNextGardenPlant(1))
        assertEquals(listOf("garden:1"), calls)

        assertSame(
            feedbackResult,
            facade.saveFeedbackPreferences(
                FeedbackPreferences(false, true, false)
            )
        )
        assertEquals(2, calls.size)

        assertTrue(facade.equipCostume(CostumeStyle.FLOWER_CROWN))
        assertEquals("wardrobe:FLOWER_CROWN", calls.last())
    }

    @Test
    fun recoveryMethodsRouteToOnlyTheirRequestedOperationAndDomain() {
        val calls = mutableListOf<String>()
        val facade = ApplicationPersistenceFacade(
            runOutcomes = RecordingRunOutcomePort(),
            purchaseGardenPlant = {
                error("garden must not run")
            },
            feedbackWriter = {
                error("feedback must not run")
            },
            wardrobeWriter = {
                error("wardrobe must not run")
            },
            recoveryMaintenance = ThrowingRecoveryMaintenance(calls)
        )

        assertMarker("inspect") { facade.inspectRecoveryEvidence() }
        assertMarker("recover") { facade.recoverSafely() }
        assertMarker("discardCorrupt:GHOST_PROMOTION") {
            facade.discardCorruptRecoveryEvidence(
                RecoveryEvidenceDomain.GHOST_PROMOTION
            )
        }
        assertMarker("discardPending:RUN_OUTCOME") {
            facade.discardUnresolvedPendingRecoveryEvidence(
                RecoveryEvidenceDomain.RUN_OUTCOME
            )
        }

        assertEquals(
            listOf(
                "inspect",
                "recover",
                "discardCorrupt:GHOST_PROMOTION",
                "discardPending:RUN_OUTCOME"
            ),
            calls
        )
    }

    @Test
    fun oneDomainFailureDoesNotInvokeAnotherDomain() {
        val calls = mutableListOf<String>()
        val facade = ApplicationPersistenceFacade(
            runOutcomes = RecordingRunOutcomePort(),
            purchaseGardenPlant = {
                calls += "garden"
                throw DomainFailure("garden")
            },
            feedbackWriter = {
                calls += "feedback"
                it
            },
            wardrobeWriter = {
                calls += "wardrobe"
                false
            },
            recoveryMaintenance = ThrowingRecoveryMaintenance(calls)
        )

        try {
            facade.purchaseNextGardenPlant(1)
            error("expected domain failure")
        } catch (failure: DomainFailure) {
            assertEquals("garden", failure.message)
        }

        assertEquals(listOf("garden"), calls)
        assertFalse(facade.equipCostume(CostumeStyle.NONE))
        assertEquals(listOf("garden", "wardrobe"), calls)
    }

    private fun sampleSummary(): RunSummary = RunSummary(
        score = 300,
        distanceM = 120f,
        isNewHighScore = false,
        highScore = 500,
        mercyHearts = 3,
        mercyMisses = 1,
        kindnessChain = 2,
        cleanPasses = 4,
        sparedCount = 5,
        hitsTaken = 1,
        seedsCollected = 8,
        bloomConversions = 2,
        lastKiller = null,
        restQuote = "Rest beneath the willow.",
        forestMood = ForestMood.STEADY
    )

    private fun assertMarker(expected: String, block: () -> Unit) {
        try {
            block()
            error("expected marker")
        } catch (marker: RecoveryMarker) {
            assertEquals(expected, marker.message)
        }
    }

    private class RecordingRunOutcomePort : ApplicationRunOutcomePort {
        var resetCount = 0
        var summary: RunSummary? = null
        var ghost: List<GhostFrame>? = null
        var persistProgress = false

        override fun resetForNewRun() {
            resetCount += 1
        }

        override fun commit(
            summary: RunSummary,
            completedGhost: List<GhostFrame>,
            persistProgress: Boolean
        ): RunOutcomeCommitResult {
            this.summary = summary
            ghost = completedGhost
            this.persistProgress = persistProgress
            return RunOutcomeCommitResult(
                disposition = RunOutcomeCommitDisposition.COMMITTED,
                ghostPromoted = false
            )
        }
    }

    private class ThrowingRecoveryMaintenance(
        private val calls: MutableList<String>
    ) : ApplicationRecoveryMaintenance {
        override fun inspect(): RecoveryEvidenceReport = mark("inspect")
        override fun recoverSafely(): RecoveryEvidenceReport = mark("recover")
        override fun discardCorrupt(
            domain: RecoveryEvidenceDomain
        ): RecoveryDiscardResult = mark("discardCorrupt:${domain.name}")

        override fun discardUnresolvedPending(
            domain: RecoveryEvidenceDomain
        ): RecoveryDiscardResult = mark("discardPending:${domain.name}")

        private fun <T> mark(value: String): T {
            calls += value
            throw RecoveryMarker(value)
        }
    }

    private class RecoveryMarker(message: String) : RuntimeException(message)
    private class DomainFailure(message: String) : RuntimeException(message)
}
