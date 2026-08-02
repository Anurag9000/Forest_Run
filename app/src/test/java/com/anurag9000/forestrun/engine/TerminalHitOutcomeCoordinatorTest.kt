package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.systems.GhostFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalHitOutcomeCoordinatorTest {

    @Test
    fun `persistent known killer completes in authored order`() {
        val calls = mutableListOf<String>()
        val committer = RecordingCommitter(calls)
        val coordinator = coordinator(calls, committer)
        var summaryBuilds = 0
        val ghost = ghostFrames()

        val result = coordinator.complete(
            killerType = EntityType.WOLF,
            biome = Biome.ANCIENT_GROVE,
            presentation = presentation(EntityType.WOLF),
            completedGhost = ghost,
            persistEncounter = true
        ) {
            summaryBuilds++
            calls += "summary"
            summary(restQuote = "preview")
        }

        assertEquals(1, summaryBuilds)
        assertEquals(
            listOf(
                "relationship:WOLF",
                "feedback:WOLF",
                "summary",
                "quote:ANCIENT_GROVE:WOLF",
                "commit:true:2:resolved quote"
            ),
            calls
        )
        assertEquals("resolved quote", result.summary.restQuote)
        assertTrue(result.persistence.committed)
        assertTrue(result.persistence.ghostPromoted)
        assertEquals(result.summary, committer.summary)
        assertEquals(ghost, committer.ghost)
    }

    @Test
    fun `nonpersistent hit skips relationship but still completes presentation and token commit`() {
        val calls = mutableListOf<String>()
        val committer = RecordingCommitter(
            calls = calls,
            result = RunOutcomeCommitResult(
                RunOutcomeCommitDisposition.NON_PERSISTENT_RUN,
                ghostPromoted = false
            )
        )
        val coordinator = coordinator(calls, committer)

        val result = coordinator.complete(
            killerType = EntityType.CAT,
            biome = Biome.MEADOW,
            presentation = presentation(EntityType.CAT),
            completedGhost = ghostFrames(),
            persistEncounter = false
        ) {
            calls += "summary"
            summary(restQuote = "preview")
        }

        assertEquals(
            listOf(
                "feedback:CAT",
                "summary",
                "quote:MEADOW:CAT",
                "commit:false:2:resolved quote"
            ),
            calls
        )
        assertEquals(RunOutcomeCommitDisposition.NON_PERSISTENT_RUN, result.persistence.disposition)
        assertFalse(result.persistence.ghostPromoted)
    }

    @Test
    fun `persistent hit with unknown killer does not invent relationship memory`() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(calls, RecordingCommitter(calls))

        coordinator.complete(
            killerType = null,
            biome = Biome.NIGHT_FOREST,
            presentation = presentation(null),
            completedGhost = emptyList(),
            persistEncounter = true
        ) {
            calls += "summary"
            summary(restQuote = "preview")
        }

        assertEquals(
            listOf(
                "feedback:null",
                "summary",
                "quote:NIGHT_FOREST:null",
                "commit:true:0:resolved quote"
            ),
            calls
        )
    }

    @Test
    fun `presentation coordinates and route tier pass through unchanged`() {
        var observed: TerminalHitPresentation? = null
        val coordinator = TerminalHitOutcomeCoordinator(
            relationshipRecorder = object : TerminalHitRelationshipRecorder {
                override fun recordHit(type: EntityType) = Unit
            },
            feedbackPresenter = object : TerminalHitFeedbackPresenter {
                override fun present(input: TerminalHitPresentation) {
                    observed = input
                }
            },
            restQuoteResolver = object : TerminalHitRestQuoteResolver {
                override fun resolve(
                    summaryPreview: RunSummary,
                    biome: Biome,
                    killerType: EntityType?
                ): String = "quote"
            },
            outcomeCommitter = RecordingCommitter(mutableListOf())
        )
        val input = TerminalHitPresentation(
            killerType = EntityType.EAGLE,
            routeTier = PacifistRouteTier.PEACEFUL,
            playerX = -42.5f,
            playerY = 918.25f
        )

        coordinator.complete(
            killerType = EntityType.EAGLE,
            biome = Biome.DUSK_CANYON,
            presentation = input,
            completedGhost = emptyList(),
            persistEncounter = true
        ) { summary() }

        assertEquals(input, observed)
    }

    @Test
    fun `contradictory killer identity is rejected before side effects`() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(calls, RecordingCommitter(calls))
        var rejected = false

        try {
            coordinator.complete(
                killerType = EntityType.WOLF,
                biome = Biome.ANCIENT_GROVE,
                presentation = presentation(EntityType.CAT),
                completedGhost = ghostFrames(),
                persistEncounter = true
            ) {
                calls += "summary"
                summary()
            }
        } catch (_: IllegalArgumentException) {
            rejected = true
        }

        assertTrue(rejected)
        assertTrue(calls.isEmpty())
    }

    private fun coordinator(
        calls: MutableList<String>,
        committer: RunOutcomeCommitter
    ): TerminalHitOutcomeCoordinator = TerminalHitOutcomeCoordinator(
        relationshipRecorder = object : TerminalHitRelationshipRecorder {
            override fun recordHit(type: EntityType) {
                calls += "relationship:${type.name}"
            }
        },
        feedbackPresenter = object : TerminalHitFeedbackPresenter {
            override fun present(input: TerminalHitPresentation) {
                calls += "feedback:${input.killerType?.name}"
            }
        },
        restQuoteResolver = object : TerminalHitRestQuoteResolver {
            override fun resolve(
                summaryPreview: RunSummary,
                biome: Biome,
                killerType: EntityType?
            ): String {
                calls += "quote:${biome.name}:${killerType?.name}"
                return "resolved quote"
            }
        },
        outcomeCommitter = committer
    )

    private fun presentation(killer: EntityType?): TerminalHitPresentation =
        TerminalHitPresentation(
            killerType = killer,
            routeTier = PacifistRouteTier.MERCIFUL,
            playerX = 320f,
            playerY = 720f
        )

    private fun summary(restQuote: String = "preview"): RunSummary = RunSummary(
        score = 1_200,
        distanceM = 500f,
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
        lastKiller = EntityType.WOLF,
        restQuote = restQuote,
        forestMood = ForestMood.STEADY
    )

    private fun ghostFrames(): List<GhostFrame> = listOf(
        GhostFrame(0f, 100f, 200f, 0, 1f, 1f),
        GhostFrame(0.04f, 104f, 196f, 1, 0.98f, 1.02f)
    )

    private class RecordingCommitter(
        private val calls: MutableList<String>,
        private val result: RunOutcomeCommitResult = RunOutcomeCommitResult(
            RunOutcomeCommitDisposition.COMMITTED,
            ghostPromoted = true
        )
    ) : RunOutcomeCommitter {
        var summary: RunSummary? = null
            private set
        var ghost: List<GhostFrame>? = null
            private set

        override fun commit(
            summary: RunSummary,
            completedGhost: List<GhostFrame>,
            persistProgress: Boolean
        ): RunOutcomeCommitResult {
            this.summary = summary
            ghost = completedGhost
            calls += "commit:$persistProgress:${completedGhost.size}:${summary.restQuote}"
            return result
        }
    }
}
