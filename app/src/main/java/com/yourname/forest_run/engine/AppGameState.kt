package com.yourname.forest_run.engine

/**
 * Top-level application game state — Phase 22.
 *
 * MENU     — Main menu / Garden idle scene.
 * PLAYING  — Active run (all physics, entities, HUD live).
 * BLOOM    — 6-second invincibility window (character glows, pulls in seeds, converts passed encounters).
 * REST     — Death / run-end summary before fading back into the Garden hub.
 *
 * Note: The per-run death cycle (DYING → GAME_OVER → RESTARTING) is handled
 * separately by [RunState] and [RunResetManager]. AppGameState wraps the larger
 * lifecycle: Garden/Menu -> Playing -> [Bloom ->] Playing -> Rest -> Garden.
 */
enum class AppGameState {
    MENU,
    GARDEN,
    PLAYING,
    BLOOM,
    REST
}
