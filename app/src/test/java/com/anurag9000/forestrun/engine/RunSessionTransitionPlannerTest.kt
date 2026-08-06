package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSessionTransitionPlannerTest {
    @Test
    fun menuAndGardenRunRequestsPrepareFreshPlayingSessionsWithoutDuplicateMusic() {
        val menu = RunSessionTransitionPlanner.plan(
            RunSessionSnapshot(AppGameState.MENU, RunState.PLAYING),
            RunSessionEvent.MENU_RUN_REQUESTED
        )
        val garden = RunSessionTransitionPlanner.plan(
            RunSessionSnapshot(AppGameState.GARDEN, RunState.PLAYING),
            RunSessionEvent.GARDEN_RUN_REQUESTED
        )

        listOf(menu, garden).forEach { transition ->
            assertEquals(
                RunSessionSnapshot(AppGameState.PLAYING, RunState.PLAYING),
                transition.after
            )
            assertEquals(
                listOf(RunSessionEffect.PREPARE_FRESH_RUN),
                transition.effects
            )
            assertTrue(transition.changed)
        }
        assertFalse(RunSessionEffect.entries.any { it.name == "PLAY_RUN_MUSIC" })
    }

    @Test
    fun terminalRestAndRestartFlowCannotSkipInitializationOrReset() {
        val live = RunSessionSnapshot(AppGameState.PLAYING, RunState.PLAYING)
        val dying = RunSessionTransitionPlanner.plan(
            live,
            RunSessionEvent.TERMINAL_COLLISION_COMPLETED
        )
        assertEquals(RunState.DYING, dying.after.runState)
        assertEquals(listOf(RunSessionEffect.TRIGGER_DEATH), dying.effects)

        val gameOver = RunSessionTransitionPlanner.plan(
            dying.after,
            RunSessionEvent.DYING_DURATION_COMPLETED
        )
        assertEquals(RunState.GAME_OVER, gameOver.after.runState)
        assertTrue(gameOver.effects.isEmpty())

        val restarting = RunSessionTransitionPlanner.plan(
            gameOver.after,
            RunSessionEvent.REST_TAPPED
        )
        assertEquals(RunState.RESTARTING, restarting.after.runState)
        assertEquals(listOf(RunSessionEffect.BEGIN_RESTART), restarting.effects)

        val garden = RunSessionTransitionPlanner.plan(
            restarting.after,
            RunSessionEvent.RESTART_FADE_COMPLETED
        )
        assertEquals(
            RunSessionSnapshot(AppGameState.GARDEN, RunState.PLAYING),
            garden.after
        )
        assertEquals(
            listOf(
                RunSessionEffect.EXECUTE_RUN_RESET,
                RunSessionEffect.RESET_GHOST_RECORDER,
                RunSessionEffect.RELOAD_GHOST,
                RunSessionEffect.REFRESH_GARDEN,
                RunSessionEffect.PLAY_MENU_MUSIC
            ),
            garden.effects
        )
    }

    @Test
    fun gardenBackRestoresMenuRitualAndCopyBeforeMenuMusic() {
        val transition = RunSessionTransitionPlanner.plan(
            RunSessionSnapshot(AppGameState.GARDEN, RunState.PLAYING),
            RunSessionEvent.GARDEN_BACK_REQUESTED
        )

        assertEquals(
            RunSessionSnapshot(AppGameState.MENU, RunState.PLAYING),
            transition.after
        )
        assertEquals(
            listOf(
                RunSessionEffect.RESET_MENU_RITUAL,
                RunSessionEffect.REFRESH_MENU_COPY,
                RunSessionEffect.PLAY_MENU_MUSIC
            ),
            transition.effects
        )
    }

    @Test
    fun everyInvalidStateEventPairIsACompleteNoOp() {
        val validPairs = setOf(
            RunSessionSnapshot(AppGameState.MENU, RunState.PLAYING) to
                RunSessionEvent.MENU_RUN_REQUESTED,
            RunSessionSnapshot(AppGameState.GARDEN, RunState.PLAYING) to
                RunSessionEvent.GARDEN_RUN_REQUESTED,
            RunSessionSnapshot(AppGameState.GARDEN, RunState.PLAYING) to
                RunSessionEvent.GARDEN_BACK_REQUESTED,
            RunSessionSnapshot(AppGameState.PLAYING, RunState.PLAYING) to
                RunSessionEvent.TERMINAL_COLLISION_COMPLETED,
            RunSessionSnapshot(AppGameState.PLAYING, RunState.DYING) to
                RunSessionEvent.DYING_DURATION_COMPLETED,
            RunSessionSnapshot(AppGameState.PLAYING, RunState.GAME_OVER) to
                RunSessionEvent.REST_TAPPED,
            RunSessionSnapshot(AppGameState.PLAYING, RunState.RESTARTING) to
                RunSessionEvent.RESTART_FADE_COMPLETED
        )

        val snapshots = AppGameState.entries.flatMap { app ->
            RunState.entries.map { run -> RunSessionSnapshot(app, run) }
        }
        snapshots.forEach { snapshot ->
            RunSessionEvent.entries.forEach { event ->
                if ((snapshot to event) in validPairs) return@forEach
                val transition = RunSessionTransitionPlanner.plan(snapshot, event)
                assertEquals(snapshot, transition.after)
                assertTrue(transition.effects.isEmpty())
                assertFalse(transition.changed)
            }
        }
    }
}
