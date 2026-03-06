package com.yourname.forest_run.engine

/**
 * Top-level application game state — Phase 22.
 *
 * MENU     — Main menu / Garden idle scene.
 * PLAYING  — Active run (all physics, entities, HUD live).
 * BLOOM    — 5-second invincibility window (character glows, seed magnet active).
 * REST     — Death / run-end overlay (GameOverScreen shown).
 *
 * Note: The per-run death cycle (DYING → GAME_OVER → RESTARTING) is handled
 * separately by [RunState] and [RunResetManager]. AppGameState wraps the larger
 * lifecycle: Menu → Playing → [Bloom →] Playing → Rest → Menu.
 */
enum class AppGameState {
    MENU,
    GARDEN,
    PLAYING,
    BLOOM,
    REST
}
