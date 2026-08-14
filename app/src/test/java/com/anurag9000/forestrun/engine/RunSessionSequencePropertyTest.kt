package com.anurag9000.forestrun.engine

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSessionSequencePropertyTest {
    @Test
    fun `seeded ordinary event traces preserve transactional state publication`() {
        val random = Random(0xF07E57)
        val ordinaryEvents = RunSessionEvent.entries
            .filterNot { it == RunSessionEvent.DEBUG_PLAYING_STATE_REQUESTED }

        repeat(64) { traceIndex ->
            var current = RunSessionSnapshot(AppGameState.MENU, RunState.PLAYING)
            repeat(512) { stepIndex ->
                val event = ordinaryEvents[random.nextInt(ordinaryEvents.size)]
                val calls = mutableListOf<RunSessionEffect>()
                val coordinator = RunSessionTransitionCoordinator { effect ->
                    calls += effect
                    true
                }
                val result = coordinator.execute(current, event)

                assertEquals(
                    "trace=$traceIndex step=$stepIndex event=$event",
                    current,
                    result.transition.before
                )
                assertEquals(result.transition.effects, calls)
                assertNull(result.failedEffect)

                if (result.transition.accepted) {
                    assertTrue(result.mayAdoptAfterState)
                    assertEquals(
                        if (result.transition.changed) {
                            RunSessionExecutionDisposition.APPLIED
                        } else {
                            RunSessionExecutionDisposition.NO_OP
                        },
                        result.disposition
                    )
                    current = result.transition.after
                } else {
                    assertFalse(result.mayAdoptAfterState)
                    assertEquals(RunSessionExecutionDisposition.NO_OP, result.disposition)
                    assertEquals(current, result.transition.after)
                    assertTrue(calls.isEmpty())
                }
            }
        }
    }

    @Test
    fun `canonical run rest garden menu sequence ignores stale callbacks without duplicate effects`() {
        var current = RunSessionSnapshot(AppGameState.MENU, RunState.PLAYING)
        val applied = mutableListOf<RunSessionEffect>()
        val coordinator = RunSessionTransitionCoordinator { effect ->
            applied += effect
            true
        }

        fun execute(event: RunSessionEvent, accepted: Boolean = true) {
            val before = current
            val result = coordinator.execute(current, event)
            assertEquals(accepted, result.transition.accepted)
            if (accepted) {
                assertTrue(result.mayAdoptAfterState)
                current = result.transition.after
            } else {
                assertFalse(result.mayAdoptAfterState)
                assertEquals(before, result.transition.after)
                assertEquals(RunSessionExecutionDisposition.NO_OP, result.disposition)
            }
        }

        execute(RunSessionEvent.MENU_RUN_REQUESTED)
        execute(RunSessionEvent.MENU_RUN_REQUESTED, accepted = false)
        execute(RunSessionEvent.RESTART_FADE_COMPLETED, accepted = false)
        execute(RunSessionEvent.TERMINAL_COLLISION_COMPLETED)
        execute(RunSessionEvent.TERMINAL_COLLISION_COMPLETED, accepted = false)
        execute(RunSessionEvent.REST_TAPPED, accepted = false)
        execute(RunSessionEvent.DYING_DURATION_COMPLETED)
        execute(RunSessionEvent.DYING_DURATION_COMPLETED, accepted = false)
        execute(RunSessionEvent.REST_TAPPED)
        execute(RunSessionEvent.REST_TAPPED, accepted = false)
        execute(RunSessionEvent.RESTART_FADE_COMPLETED)
        execute(RunSessionEvent.RESTART_FADE_COMPLETED, accepted = false)
        execute(RunSessionEvent.GARDEN_BACK_REQUESTED)
        execute(RunSessionEvent.GARDEN_BACK_REQUESTED, accepted = false)

        assertEquals(
            RunSessionSnapshot(AppGameState.MENU, RunState.PLAYING),
            current
        )
        assertEquals(
            listOf(
                RunSessionEffect.PREPARE_FRESH_RUN,
                RunSessionEffect.TRIGGER_DEATH,
                RunSessionEffect.BEGIN_RESTART,
                RunSessionEffect.EXECUTE_RUN_RESET,
                RunSessionEffect.RESET_GHOST_RECORDER,
                RunSessionEffect.RELOAD_GHOST,
                RunSessionEffect.REFRESH_GARDEN,
                RunSessionEffect.PLAY_MENU_MUSIC,
                RunSessionEffect.RESET_MENU_RITUAL,
                RunSessionEffect.REFRESH_MENU_COPY,
                RunSessionEffect.PLAY_MENU_MUSIC
            ),
            applied
        )
    }

    @Test
    fun `reconstructed planner produces identical plans for every snapshot event pair`() {
        val snapshots = AppGameState.entries.flatMap { appState ->
            RunState.entries.map { runState -> RunSessionSnapshot(appState, runState) }
        }

        snapshots.forEach { snapshot ->
            RunSessionEvent.entries.forEach { event ->
                val captured = snapshot.copy()
                val first = RunSessionTransitionPlanner.plan(captured, event)
                val reconstructed = RunSessionTransitionPlanner.plan(captured.copy(), event)
                assertEquals("snapshot=$snapshot event=$event", first, reconstructed)
            }
        }
    }

    @Test
    fun `every multi effect failure position short circuits and healthy retry is deterministic`() {
        val routes = listOf(
            RunSessionSnapshot(AppGameState.GARDEN, RunState.PLAYING) to
                RunSessionEvent.GARDEN_BACK_REQUESTED,
            RunSessionSnapshot(AppGameState.PLAYING, RunState.RESTARTING) to
                RunSessionEvent.RESTART_FADE_COMPLETED
        )

        routes.forEach { (before, event) ->
            val plan = RunSessionTransitionPlanner.plan(before, event)
            assertTrue(plan.effects.size > 1)

            plan.effects.indices.forEach { failingIndex ->
                val failedCalls = mutableListOf<RunSessionEffect>()
                val failing = RunSessionTransitionCoordinator { effect ->
                    failedCalls += effect
                    failedCalls.lastIndex != failingIndex
                }
                val failed = failing.execute(before, event)

                assertEquals(RunSessionExecutionDisposition.EFFECT_FAILED, failed.disposition)
                assertEquals(plan.effects[failingIndex], failed.failedEffect)
                assertEquals(plan.effects.take(failingIndex + 1), failedCalls)
                assertFalse(failed.mayAdoptAfterState)
                assertEquals(before, failed.transition.before)

                val retryCalls = mutableListOf<RunSessionEffect>()
                val retry = RunSessionTransitionCoordinator { effect ->
                    retryCalls += effect
                    true
                }.execute(before, event)

                assertEquals(RunSessionExecutionDisposition.APPLIED, retry.disposition)
                assertTrue(retry.mayAdoptAfterState)
                assertNull(retry.failedEffect)
                assertEquals(plan, retry.transition)
                assertEquals(plan.effects, retryCalls)
            }
        }
    }
}
