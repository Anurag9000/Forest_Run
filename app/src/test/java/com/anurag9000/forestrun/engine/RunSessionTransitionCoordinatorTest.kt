package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSessionTransitionCoordinatorTest {
    @Test
    fun appliesEffectsInPlannerOrderAndAllowsFinalStateAdoption() {
        val calls = mutableListOf<RunSessionEffect>()
        val coordinator = RunSessionTransitionCoordinator { effect ->
            calls += effect
            true
        }

        val result = coordinator.execute(
            current = RunSessionSnapshot(AppGameState.PLAYING, RunState.RESTARTING),
            event = RunSessionEvent.RESTART_FADE_COMPLETED
        )

        assertEquals(RunSessionExecutionDisposition.APPLIED, result.disposition)
        assertTrue(result.mayAdoptAfterState)
        assertNull(result.failedEffect)
        assertEquals(result.transition.effects, calls)
        assertEquals(
            RunSessionSnapshot(AppGameState.GARDEN, RunState.PLAYING),
            result.transition.after
        )
    }

    @Test
    fun stopsAtFirstFailedEffectAndForbidsStateAdoption() {
        val calls = mutableListOf<RunSessionEffect>()
        val coordinator = RunSessionTransitionCoordinator { effect ->
            calls += effect
            effect != RunSessionEffect.RELOAD_GHOST
        }

        val result = coordinator.execute(
            current = RunSessionSnapshot(AppGameState.PLAYING, RunState.RESTARTING),
            event = RunSessionEvent.RESTART_FADE_COMPLETED
        )

        assertEquals(
            listOf(
                RunSessionEffect.EXECUTE_RUN_RESET,
                RunSessionEffect.RESET_GHOST_RECORDER,
                RunSessionEffect.RELOAD_GHOST
            ),
            calls
        )
        assertEquals(
            RunSessionExecutionDisposition.EFFECT_FAILED,
            result.disposition
        )
        assertEquals(RunSessionEffect.RELOAD_GHOST, result.failedEffect)
        assertFalse(result.mayAdoptAfterState)
    }

    @Test
    fun runtimeExceptionFailsClosedWithoutApplyingLaterEffects() {
        val calls = mutableListOf<RunSessionEffect>()
        val coordinator = RunSessionTransitionCoordinator { effect ->
            calls += effect
            if (effect == RunSessionEffect.REFRESH_MENU_COPY) {
                throw IllegalStateException("copy unavailable")
            }
            true
        }

        val result = coordinator.execute(
            current = RunSessionSnapshot(AppGameState.GARDEN, RunState.PLAYING),
            event = RunSessionEvent.GARDEN_BACK_REQUESTED
        )

        assertEquals(
            listOf(
                RunSessionEffect.RESET_MENU_RITUAL,
                RunSessionEffect.REFRESH_MENU_COPY
            ),
            calls
        )
        assertEquals(
            RunSessionExecutionDisposition.EFFECT_FAILED,
            result.disposition
        )
        assertEquals(RunSessionEffect.REFRESH_MENU_COPY, result.failedEffect)
        assertFalse(result.mayAdoptAfterState)
    }

    @Test
    fun invalidEventDoesNotCallEffectSink() {
        var calls = 0
        val coordinator = RunSessionTransitionCoordinator {
            calls += 1
            true
        }

        val snapshot = RunSessionSnapshot(AppGameState.MENU, RunState.PLAYING)
        val result = coordinator.execute(
            current = snapshot,
            event = RunSessionEvent.REST_TAPPED
        )

        assertEquals(0, calls)
        assertEquals(RunSessionExecutionDisposition.NO_OP, result.disposition)
        assertEquals(snapshot, result.transition.after)
        assertFalse(result.mayAdoptAfterState)
    }
}
