package com.anurag9000.forestrun.engine

/** Immutable pair of the two orthogonal top-level runtime state owners. */
internal data class RunSessionSnapshot(
    val appState: AppGameState,
    val runState: RunState
)

/** Events that are allowed to request a top-level run/session transition. */
internal enum class RunSessionEvent {
    MENU_RUN_REQUESTED,
    MENU_GARDEN_REQUESTED,
    GARDEN_RUN_REQUESTED,
    GARDEN_BACK_REQUESTED,
    TERMINAL_COLLISION_COMPLETED,
    DYING_DURATION_COMPLETED,
    REST_TAPPED,
    RESTART_FADE_COMPLETED,
    DEBUG_PLAYING_STATE_REQUESTED
}

/** Ordered effects the live owner must apply around a planned transition. */
internal enum class RunSessionEffect {
    PREPARE_FRESH_RUN,
    RESET_MENU_RITUAL,
    REFRESH_MENU_COPY,
    TRIGGER_DEATH,
    BEGIN_RESTART,
    EXECUTE_RUN_RESET,
    RESET_GHOST_RECORDER,
    RELOAD_GHOST,
    REFRESH_GARDEN,
    PLAY_MENU_MUSIC
}

internal data class RunSessionTransition(
    val before: RunSessionSnapshot,
    val after: RunSessionSnapshot,
    val effects: List<RunSessionEffect>
) {
    val changed: Boolean
        get() = before != after || effects.isNotEmpty()
}

/**
 * Pure transition table for screen, death, Rest, restart, Garden, and debug
 * state publication routing.
 *
 * Bloom is intentionally absent because it remains an orthogonal power flag.
 * Invalid or stale ordinary events fail closed as complete no-ops, preventing a
 * delayed tap or duplicated callback from skipping required terminal/reset
 * phases. DEBUG_PLAYING_STATE_REQUESTED is the sole deliberately state-agnostic
 * event: only debuggable launch code emits it, after validating/preparing its
 * scenario or auto-start payload, so debug tooling does not mutate the two
 * top-level state fields behind the planner's back.
 *
 * PREPARE_FRESH_RUN retains run-start music ownership to match the current live
 * reset boundary; the table therefore never emits a duplicate music effect.
 */
internal object RunSessionTransitionPlanner {
    fun plan(
        current: RunSessionSnapshot,
        event: RunSessionEvent
    ): RunSessionTransition {
        val transition = when {
            event == RunSessionEvent.DEBUG_PLAYING_STATE_REQUESTED ->
                current.to(
                    appState = AppGameState.PLAYING,
                    runState = RunState.PLAYING
                )

            current == RunSessionSnapshot(AppGameState.MENU, RunState.PLAYING) &&
                event == RunSessionEvent.MENU_RUN_REQUESTED ->
                current.to(
                    appState = AppGameState.PLAYING,
                    runState = RunState.PLAYING,
                    effects = listOf(RunSessionEffect.PREPARE_FRESH_RUN)
                )

            current == RunSessionSnapshot(AppGameState.MENU, RunState.PLAYING) &&
                event == RunSessionEvent.MENU_GARDEN_REQUESTED ->
                current.to(
                    appState = AppGameState.GARDEN,
                    runState = RunState.PLAYING,
                    effects = listOf(RunSessionEffect.REFRESH_GARDEN)
                )

            current == RunSessionSnapshot(AppGameState.GARDEN, RunState.PLAYING) &&
                event == RunSessionEvent.GARDEN_RUN_REQUESTED ->
                current.to(
                    appState = AppGameState.PLAYING,
                    runState = RunState.PLAYING,
                    effects = listOf(RunSessionEffect.PREPARE_FRESH_RUN)
                )

            current == RunSessionSnapshot(AppGameState.GARDEN, RunState.PLAYING) &&
                event == RunSessionEvent.GARDEN_BACK_REQUESTED ->
                current.to(
                    appState = AppGameState.MENU,
                    runState = RunState.PLAYING,
                    effects = listOf(
                        RunSessionEffect.RESET_MENU_RITUAL,
                        RunSessionEffect.REFRESH_MENU_COPY,
                        RunSessionEffect.PLAY_MENU_MUSIC
                    )
                )

            current == RunSessionSnapshot(AppGameState.PLAYING, RunState.PLAYING) &&
                event == RunSessionEvent.TERMINAL_COLLISION_COMPLETED ->
                current.to(
                    appState = AppGameState.PLAYING,
                    runState = RunState.DYING,
                    effects = listOf(RunSessionEffect.TRIGGER_DEATH)
                )

            current == RunSessionSnapshot(AppGameState.PLAYING, RunState.DYING) &&
                event == RunSessionEvent.DYING_DURATION_COMPLETED ->
                current.to(
                    appState = AppGameState.PLAYING,
                    runState = RunState.GAME_OVER
                )

            current == RunSessionSnapshot(AppGameState.PLAYING, RunState.GAME_OVER) &&
                event == RunSessionEvent.REST_TAPPED ->
                current.to(
                    appState = AppGameState.PLAYING,
                    runState = RunState.RESTARTING,
                    effects = listOf(RunSessionEffect.BEGIN_RESTART)
                )

            current == RunSessionSnapshot(AppGameState.PLAYING, RunState.RESTARTING) &&
                event == RunSessionEvent.RESTART_FADE_COMPLETED ->
                current.to(
                    appState = AppGameState.GARDEN,
                    runState = RunState.PLAYING,
                    effects = listOf(
                        RunSessionEffect.EXECUTE_RUN_RESET,
                        RunSessionEffect.RESET_GHOST_RECORDER,
                        RunSessionEffect.RELOAD_GHOST,
                        RunSessionEffect.REFRESH_GARDEN,
                        RunSessionEffect.PLAY_MENU_MUSIC
                    )
                )

            else -> RunSessionTransition(current, current, emptyList())
        }
        return transition
    }

    private fun RunSessionSnapshot.to(
        appState: AppGameState,
        runState: RunState,
        effects: List<RunSessionEffect> = emptyList()
    ): RunSessionTransition = RunSessionTransition(
        before = this,
        after = RunSessionSnapshot(appState, runState),
        effects = effects
    )
}
