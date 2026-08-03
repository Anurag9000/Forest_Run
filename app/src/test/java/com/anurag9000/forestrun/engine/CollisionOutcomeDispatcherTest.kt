package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.systems.GhostFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollisionOutcomeDispatcherTest {

    @Test
    fun `hit evaluates only hit provider and preserves full terminal order`() {
        val calls = mutableListOf<String>()
        val providers = ProviderCounts()
        val dispatcher = dispatcher(calls)

        val result = dispatcher.dispatch(
            result = CollisionResult.HIT,
            hit = {
                providers.hit++
                HitCollisionDispatch(
                    persistEncounter = true,
                    captureAfterImpact = {
                        calls += "capture"
                        TerminalHitImpactCapture(
                            killerType = EntityType.WOLF,
                            biome = Biome.ANCIENT_GROVE,
                            presentation = presentation(EntityType.WOLF),
                            completedGhost = ghostFrames()
                        )
                    },
                    buildSummaryPreview = { killer ->
                        calls += "summary:${killer?.name}"
                        summary(lastKiller = killer)
                    }
                )
            },
            stumble = {
                providers.stumble++
                error("stumble provider must not run for HIT")
            },
            mercyMiss = {
                providers.mercy++
                error("mercy provider must not run for HIT")
            }
        )

        assertEquals(ProviderCounts(hit = 1), providers)
        assertEquals(
            listOf(
                "impact-record-hit",
                "impact-suppress:1.35",
                "impact-rest",
                "impact-shake",
                "impact-sfx",
                "impact-music",
                "impact-haptic",
                "capture",
                "relationship:WOLF",
                "feedback:WOLF",
                "summary:WOLF",
                "quote:ANCIENT_GROVE:WOLF",
                "commit:true:2:resolved"
            ),
            calls
        )
        assertEquals("resolved", result?.summary?.restQuote)
        assertEquals(EntityType.WOLF, result?.summary?.lastKiller)
    }

    @Test
    fun `stumble evaluates only stumble provider and returns no terminal completion`() {
        val calls = mutableListOf<String>()
        val providers = ProviderCounts()
        val dispatcher = dispatcher(calls)

        val result = dispatcher.dispatch(
            result = CollisionResult.STUMBLE,
            hit = {
                providers.hit++
                error("hit provider must not run for STUMBLE")
            },
            stumble = {
                providers.stumble++
                StumbleCollisionDispatch(
                    input = StumbleCollisionOutcome(
                        killerType = EntityType.CAT,
                        routeTier = PacifistRouteTier.MERCIFUL,
                        playerX = 300f,
                        playerY = 700f,
                        dominantColor = 0x11223344,
                        persistEncounter = true
                    ),
                    deactivateEntity = { calls += "deactivate" }
                )
            },
            mercyMiss = {
                providers.mercy++
                error("mercy provider must not run for STUMBLE")
            }
        )

        assertNull(result)
        assertEquals(ProviderCounts(stumble = 1), providers)
        assertEquals(
            listOf(
                "nonterminal-record-hit",
                "relationship-nonterminal:CAT",
                "nonterminal-suppress:0.9",
                "nonterminal-stumble",
                "stumble-flash:287454020",
                "nonterminal-hit-sfx",
                "nonterminal-hit-shake",
                "nonterminal-medium-haptic",
                "present-stumble:CAT",
                "deactivate"
            ),
            calls
        )
    }

    @Test
    fun `mercy evaluates only mercy provider and returns no terminal completion`() {
        val calls = mutableListOf<String>()
        val providers = ProviderCounts()
        val dispatcher = dispatcher(calls)

        val result = dispatcher.dispatch(
            result = CollisionResult.MERCY_MISS,
            hit = {
                providers.hit++
                error("hit provider must not run for MERCY_MISS")
            },
            stumble = {
                providers.stumble++
                error("stumble provider must not run for MERCY_MISS")
            },
            mercyMiss = {
                providers.mercy++
                MercyMissCollisionOutcome(
                    entityType = EntityType.EAGLE,
                    routeTier = PacifistRouteTier.PEACEFUL,
                    mercyHearts = 6,
                    kindnessChain = 8,
                    playerX = 300f,
                    playerY = 700f
                )
            }
        )

        assertNull(result)
        assertEquals(ProviderCounts(mercy = 1), providers)
        assertEquals(
            listOf(
                "mercy-flash",
                "mercy-sfx",
                "mercy-haptic",
                "present-mercy:EAGLE",
                "mercy-stars",
                "mercy-shake"
            ),
            calls
        )
    }

    @Test
    fun `none evaluates no providers and performs no effects`() {
        val calls = mutableListOf<String>()
        val providers = ProviderCounts()
        val dispatcher = dispatcher(calls)

        val result = dispatcher.dispatch(
            result = CollisionResult.NONE,
            hit = {
                providers.hit++
                error("hit provider must not run for NONE")
            },
            stumble = {
                providers.stumble++
                error("stumble provider must not run for NONE")
            },
            mercyMiss = {
                providers.mercy++
                error("mercy provider must not run for NONE")
            }
        )

        assertNull(result)
        assertEquals(ProviderCounts(), providers)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `impact failure propagates without capture or completion`() {
        val calls = mutableListOf<String>()
        val dispatcher = dispatcher(calls, failImpactAtRest = true)
        var captureRuns = 0
        var failed = false

        try {
            dispatcher.dispatch(
                result = CollisionResult.HIT,
                hit = {
                    HitCollisionDispatch(
                        persistEncounter = true,
                        captureAfterImpact = {
                            captureRuns++
                            error("capture must not run")
                        },
                        buildSummaryPreview = { summary(lastKiller = it) }
                    )
                },
                stumble = { error("stumble provider must not run") },
                mercyMiss = { error("mercy provider must not run") }
            )
        } catch (_: IllegalStateException) {
            failed = true
        }

        assertTrue(failed)
        assertEquals(0, captureRuns)
        assertEquals(
            listOf(
                "impact-record-hit",
                "impact-suppress:1.35",
                "impact-rest"
            ),
            calls
        )
    }

    private fun dispatcher(
        calls: MutableList<String>,
        failImpactAtRest: Boolean = false
    ): CollisionOutcomeDispatcher {
        val impact = TerminalHitImpactCoordinator(
            effects = object : TerminalHitImpactEffectSink {
                override fun recordRunHit() { calls += "impact-record-hit" }
                override fun suppressGhost(seconds: Float) { calls += "impact-suppress:$seconds" }
                override fun triggerPlayerRest() {
                    calls += "impact-rest"
                    if (failImpactAtRest) error("rest failed")
                }
                override fun shakeHit() { calls += "impact-shake" }
                override fun playHit() { calls += "impact-sfx" }
                override fun playRest() { calls += "impact-music" }
                override fun longPulse() { calls += "impact-haptic" }
            }
        )
        val terminal = TerminalHitOutcomeCoordinator(
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
                    return "resolved"
                }
            },
            outcomeCommitter = object : RunOutcomeCommitter {
                override fun commit(
                    summary: RunSummary,
                    completedGhost: List<GhostFrame>,
                    persistProgress: Boolean
                ): RunOutcomeCommitResult {
                    calls += "commit:$persistProgress:${completedGhost.size}:${summary.restQuote}"
                    return RunOutcomeCommitResult(
                        disposition = RunOutcomeCommitDisposition.COMMITTED,
                        ghostPromoted = true
                    )
                }
            }
        )
        val nonTerminal = NonTerminalCollisionOutcomeCoordinator(
            effects = object : NonTerminalCollisionEffectSink {
                override fun recordRunHit() { calls += "nonterminal-record-hit" }
                override fun suppressGhost(seconds: Float) { calls += "nonterminal-suppress:$seconds" }
                override fun triggerStumble() { calls += "nonterminal-stumble" }
                override fun showStumbleFlash(dominantColor: Int) { calls += "stumble-flash:$dominantColor" }
                override fun playNonLethalHit() { calls += "nonterminal-hit-sfx" }
                override fun shakeHit() { calls += "nonterminal-hit-shake" }
                override fun mediumPulse() { calls += "nonterminal-medium-haptic" }
                override fun showMercyFlash() { calls += "mercy-flash" }
                override fun playMercyMiss() { calls += "mercy-sfx" }
                override fun doubleTap() { calls += "mercy-haptic" }
                override fun emitMercyStars(centerX: Float, centerY: Float) { calls += "mercy-stars" }
                override fun shakeMercyMiss() { calls += "mercy-shake" }
            },
            relationshipRecorder = object : NonTerminalCollisionRelationshipRecorder {
                override fun recordHit(type: EntityType) {
                    calls += "relationship-nonterminal:${type.name}"
                }
            },
            feedbackPresenter = object : NonTerminalCollisionFeedbackPresenter {
                override fun presentStumble(input: StumbleCollisionOutcome) {
                    calls += "present-stumble:${input.killerType?.name}"
                }
                override fun presentMercyMiss(input: MercyMissCollisionOutcome) {
                    calls += "present-mercy:${input.entityType?.name}"
                }
            }
        )
        return CollisionOutcomeDispatcher(impact, terminal, nonTerminal)
    }

    private fun presentation(killer: EntityType?): TerminalHitPresentation =
        TerminalHitPresentation(
            killerType = killer,
            routeTier = PacifistRouteTier.MERCIFUL,
            playerX = 320f,
            playerY = 720f
        )

    private fun summary(lastKiller: EntityType?): RunSummary = RunSummary(
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
        lastKiller = lastKiller,
        restQuote = "preview",
        forestMood = ForestMood.STEADY
    )

    private fun ghostFrames(): List<GhostFrame> = listOf(
        GhostFrame(0f, 100f, 200f, 0, 1f, 1f),
        GhostFrame(0.04f, 104f, 196f, 1, 0.98f, 1.02f)
    )

    private data class ProviderCounts(
        var hit: Int = 0,
        var stumble: Int = 0,
        var mercy: Int = 0
    )
}
