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
            assertTrue(transition.accepted)
            assertTrue(transition.changed)
        }
        assertFalse(RunSessionEffect.entries.any { it.name == "PLAY_RUN_MUSIC" })
    }

    @Test
    fun menuGardenRequestRefreshesBeforePublishingGardenState() {
        val transition = RunSessionTransitionPlanner.plan(
            RunSessionSnapshot(AppGameState.MENU, RunState.PLAYING),
            RunSessionEvent.MENU_GARDEN_REQUESTED
        )

        assertEquals(
            RunSessionSnapshot(AppGameState.GARDEN, RunState.PLAYING),
            transition.after
        )
        assertEquals(listOf(RunSessionEffect.REFRESH_GARDEN), transition.effects)
        assertTrue(transition.accepted)
        assertTrue(transition.changed)
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
        assertTrue(dying.accepted)

        val gameOver = RunSessionTransitionPlanner.plan(
            dying.after,
            RunSessionEvent.DYING_DURATION_COMPLETED
        )
        assertEquals(RunState.GAME_OVER, gameOver.after.runState)
        assertTrue(gameOver.effects.isEmpty())
        assertTrue(gameOver.accepted)

        val restarting = RunSessionTransitionPlanner.plan(
            gameOver.after,
            RunSessionEvent.REST_TAPPED
        )
        assertEquals(RunState.RESTARTING, restarting.after.runState)
        assertEquals(listOf(RunSessionEffect.BEGIN_RESTART), restarting.effects)
        assertTrue(restarting.accepted)

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
        assertTrue(garden.accepted)
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
        assertTrue(transition.accepted)
    }

    @Test
    fun debugPlayingStateRequestIsAcceptedAndStateAgnosticEvenWhenIdempotent() {
        val playing = RunSessionSnapshot(AppGameState.PLAYING, RunState.PLAYING)
        val snapshots = AppGameState.entries.flatMap { app ->
            RunState.entries.map { run -> RunSessionSnapshot(app, run) }
        }
        snapshots.forEach { snapshot ->
            val transition = RunSessionTransitionPlanner.plan(
                snapshot,
                RunSessionEvent.DEBUG_PLAYING_STATE_REQUESTED
            )
            assertEquals(playing, transition.after)
            assertTrue(transition.effects.isEmpty())
            assertTrue(transition.accepted)
            assertEquals(snapshot != playing, transition.changed)
        }
    }

    @Test
    fun everyInvalidOrdinaryStateEventPairIsAnUnacceptedCompleteNoOp() {
        val validPairs = setOf(
            RunSessionSnapshot(AppGameState.MENU, RunState.PLAYING) to
                RunSessionEvent.MENU_RUN_REQUESTED,
            RunSessionSnapshot(AppGameState.MENU, RunState.PLAYING) to
                RunSessionEvent.MENU_GARDEN_REQUESTED,
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
            RunSessionEvent.entries
                .filterNot { it == RunSessionEvent.DEBUG_PLAYING_STATE_REQUESTED }
                .forEach { event ->
                    if ((snapshot to event) in validPairs) return@forEach
                    val transition = RunSessionTransitionPlanner.plan(snapshot, event)
                    assertEquals(snapshot, transition.after)
                    assertTrue(transition.effects.isEmpty())
                    assertFalse(transition.accepted)
                    assertFalse(transition.changed)
                }
        }
    }
}
