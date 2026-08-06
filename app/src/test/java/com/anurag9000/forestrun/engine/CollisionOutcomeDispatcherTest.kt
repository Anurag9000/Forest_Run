package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.systems.GhostFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollisionOutcomeDispatcherTest {

    @Test
    fun `hit invokes only terminal captures and returns completed summary`() {
        val fixture = Fixture()

        val result = fixture.dispatcher.dispatch(
            result = CollisionResult.HIT,
            persistEncounter = true,
            captureTerminalImpact = fixture::captureTerminalImpact,
            buildTerminalSummaryPreview = fixture::buildTerminalSummary,
            buildStumbleInput = fixture::buildStumbleInput,
            deactivateStumbleEntity = fixture::deactivateStumble,
            buildMercyMissInput = fixture::buildMercyInput
        )

        assertTrue(result is CollisionOutcomeDispatchResult.Terminal)
        val terminal = result as CollisionOutcomeDispatchResult.Terminal
        assertEquals("resolved quote", terminal.completion.summary.restQuote)
        assertEquals(1, fixture.terminalCaptures)
        assertEquals(1, fixture.summaryBuilds)
        assertEquals(0, fixture.stumbleBuilds)
        assertEquals(0, fixture.mercyBuilds)
        assertEquals(0, fixture.deactivations)
        assertEquals(
            listOf(
                "impact:record",
                "impact:suppress:1.35",
                "impact:rest",
                "impact:shake",
                "impact:hit",
                "impact:music",
                "impact:haptic",
                "capture:terminal",
                "relationship:WOLF",
                "feedback:WOLF",
                "summary:WOLF",
                "quote:ANCIENT_GROVE:WOLF",
                "commit:true:1"
            ),
            fixture.calls
        )
    }

    @Test
    fun `stumble invokes only stumble capture and deactivation`() {
        val fixture = Fixture()

        val result = fixture.dispatcher.dispatch(
            result = CollisionResult.STUMBLE,
            persistEncounter = true,
            captureTerminalImpact = fixture::captureTerminalImpact,
            buildTerminalSummaryPreview = fixture::buildTerminalSummary,
            buildStumbleInput = fixture::buildStumbleInput,
            deactivateStumbleEntity = fixture::deactivateStumble,
            buildMercyMissInput = fixture::buildMercyInput
        )

        assertTrue(result === CollisionOutcomeDispatchResult.NonTerminal)
        assertEquals(0, fixture.terminalCaptures)
        assertEquals(0, fixture.summaryBuilds)
        assertEquals(1, fixture.stumbleBuilds)
        assertEquals(0, fixture.mercyBuilds)
        assertEquals(1, fixture.deactivations)
        assertEquals(
            listOf(
                "capture:stumble",
                "nonterminal:record",
                "relationship:CAT",
                "nonterminal:suppress:0.9",
                "nonterminal:stumble",
                "nonterminal:flash:123",
                "nonterminal:hit",
                "nonterminal:shake",
                "nonterminal:haptic",
                "feedback:stumble:CAT",
                "deactivate"
            ),
            fixture.calls
        )
    }

    @Test
    fun `mercy miss invokes only mercy capture`() {
        val fixture = Fixture()

        val result = fixture.dispatcher.dispatch(
            result = CollisionResult.MERCY_MISS,
            persistEncounter = false,
            captureTerminalImpact = fixture::captureTerminalImpact,
            buildTerminalSummaryPreview = fixture::buildTerminalSummary,
            buildStumbleInput = fixture::buildStumbleInput,
            deactivateStumbleEntity = fixture::deactivateStumble,
            buildMercyMissInput = fixture::buildMercyInput
        )

        assertTrue(result === CollisionOutcomeDispatchResult.NonTerminal)
        assertEquals(0, fixture.terminalCaptures)
        assertEquals(0, fixture.summaryBuilds)
        assertEquals(0, fixture.stumbleBuilds)
        assertEquals(1, fixture.mercyBuilds)
        assertEquals(0, fixture.deactivations)
        assertEquals(
            listOf(
                "capture:mercy",
                "nonterminal:mercyFlash",
                "nonterminal:mercySound",
                "nonterminal:doubleTap",
                "feedback:mercy:EAGLE",
                "nonterminal:stars:356.0:770.0",
                "nonterminal:mercyShake"
            ),
            fixture.calls
        )
    }

    @Test
    fun `none invokes no lazy capture or side effect`() {
        val fixture = Fixture()

        val result = fixture.dispatcher.dispatch(
            result = CollisionResult.NONE,
            persistEncounter = true,
            captureTerminalImpact = fixture::captureTerminalImpact,
            buildTerminalSummaryPreview = fixture::buildTerminalSummary,
            buildStumbleInput = fixture::buildStumbleInput,
            deactivateStumbleEntity = fixture::deactivateStumble,
            buildMercyMissInput = fixture::buildMercyInput
        )

        assertTrue(result === CollisionOutcomeDispatchResult.Ignored)
        assertEquals(0, fixture.terminalCaptures)
        assertEquals(0, fixture.summaryBuilds)
        assertEquals(0, fixture.stumbleBuilds)
        assertEquals(0, fixture.mercyBuilds)
        assertEquals(0, fixture.deactivations)
        assertTrue(fixture.calls.isEmpty())
    }

    private class Fixture {
        val calls = mutableListOf<String>()
        var terminalCaptures = 0
        var summaryBuilds = 0
        var stumbleBuilds = 0
        var mercyBuilds = 0
        var deactivations = 0

        val dispatcher = CollisionOutcomeDispatcher(
            terminalHitImpact = TerminalHitImpactCoordinator(
                effects = object : TerminalHitImpactEffectSink {
                    override fun recordRunHit() { calls += "impact:record" }
                    override fun suppressGhost(seconds: Float) { calls += "impact:suppress:$seconds" }
                    override fun triggerPlayerRest() { calls += "impact:rest" }
                    override fun shakeHit() { calls += "impact:shake" }
                    override fun playHit() { calls += "impact:hit" }
                    override fun playRest() { calls += "impact:music" }
                    override fun longPulse() { calls += "impact:haptic" }
                }
            ),
            terminalHitOutcome = TerminalHitOutcomeCoordinator(
                relationshipRecorder = object : TerminalHitRelationshipRecorder {
                    override fun recordHit(type: EntityType) { calls += "relationship:${type.name}" }
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
                outcomeCommitter = object : RunOutcomeCommitter {
                    override fun commit(
                        summary: RunSummary,
                        completedGhost: List<GhostFrame>,
                        persistProgress: Boolean
                    ): RunOutcomeCommitResult {
                        calls += "commit:$persistProgress:${completedGhost.size}"
                        return RunOutcomeCommitResult(
                            disposition = RunOutcomeCommitDisposition.COMMITTED,
                            ghostPromoted = true
                        )
                    }
                }
            ),
            nonTerminalOutcome = NonTerminalCollisionOutcomeCoordinator(
                effects = object : NonTerminalCollisionEffectSink {
                    override fun recordRunHit() { calls += "nonterminal:record" }
                    override fun suppressGhost(seconds: Float) { calls += "nonterminal:suppress:$seconds" }
                    override fun triggerStumble() { calls += "nonterminal:stumble" }
                    override fun showStumbleFlash(dominantColor: Int) { calls += "nonterminal:flash:$dominantColor" }
                    override fun playNonLethalHit() { calls += "nonterminal:hit" }
                    override fun shakeHit() { calls += "nonterminal:shake" }
                    override fun mediumPulse() { calls += "nonterminal:haptic" }
                    override fun showMercyFlash() { calls += "nonterminal:mercyFlash" }
                    override fun playMercyMiss() { calls += "nonterminal:mercySound" }
                    override fun doubleTap() { calls += "nonterminal:doubleTap" }
                    override fun emitMercyStars(centerX: Float, centerY: Float) {
                        calls += "nonterminal:stars:$centerX:$centerY"
                    }
                    override fun shakeMercyMiss() { calls += "nonterminal:mercyShake" }
                },
                relationshipRecorder = object : NonTerminalCollisionRelationshipRecorder {
                    override fun recordHit(type: EntityType) { calls += "relationship:${type.name}" }
                },
                feedbackPresenter = object : NonTerminalCollisionFeedbackPresenter {
                    override fun presentStumble(input: StumbleCollisionOutcome) {
                        calls += "feedback:stumble:${input.killerType?.name}"
                    }
                    override fun presentMercyMiss(input: MercyMissCollisionOutcome) {
                        calls += "feedback:mercy:${input.entityType?.name}"
                    }
                }
            )
        )

        fun captureTerminalImpact(): TerminalHitImpactCapture {
            terminalCaptures++
            calls += "capture:terminal"
            return TerminalHitImpactCapture(
                killerType = EntityType.WOLF,
                biome = Biome.ANCIENT_GROVE,
                presentation = TerminalHitPresentation(
                    killerType = EntityType.WOLF,
                    routeTier = PacifistRouteTier.MERCIFUL,
                    playerX = 320f,
                    playerY = 720f
                ),
                completedGhost = listOf(GhostFrame(0f, 100f, 200f, 0, 1f, 1f))
            )
        }

        fun buildTerminalSummary(killerType: EntityType?): RunSummary {
            summaryBuilds++
            calls += "summary:${killerType?.name}"
            return summary(killerType)
        }

        fun buildStumbleInput(): StumbleCollisionOutcome {
            stumbleBuilds++
            calls += "capture:stumble"
            return StumbleCollisionOutcome(
                killerType = EntityType.CAT,
                routeTier = PacifistRouteTier.KIND,
                playerX = 320f,
                playerY = 720f,
                dominantColor = 123,
                persistEncounter = true
            )
        }

        fun deactivateStumble() {
            deactivations++
            calls += "deactivate"
        }

        fun buildMercyInput(): MercyMissCollisionOutcome {
            mercyBuilds++
            calls += "capture:mercy"
            return MercyMissCollisionOutcome(
                entityType = EntityType.EAGLE,
                routeTier = PacifistRouteTier.PEACEFUL,
                mercyHearts = 6,
                kindnessChain = 8,
                playerX = 320f,
                playerY = 720f
            )
        }

        private fun summary(killerType: EntityType?): RunSummary = RunSummary(
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
            lastKiller = killerType,
            restQuote = "preview",
            forestMood = ForestMood.STEADY
        )
    }
}
