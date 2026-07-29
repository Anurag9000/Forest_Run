package com.anurag9000.forestrun.engine

/**
 * Top-level screen/lifecycle state.
 *
 * The active runtime uses [MENU], [GARDEN], and [PLAYING]. Bloom is owned by
 * [GameStateManager] as an orthogonal run-power flag, while death/rest/restart
 * progression is owned by [RunState] and [RunResetManager]. [BLOOM] and [REST]
 * remain reserved compatibility values for older debug integrations; new code
 * must not route ordinary runtime flow through them.
 */
enum class AppGameState {
    MENU,
    GARDEN,
    PLAYING,

    @Deprecated("Bloom is owned by GameStateManager and is orthogonal to app state")
    BLOOM,

    @Deprecated("Rest/death flow is owned by RunState and RunResetManager")
    REST
}
