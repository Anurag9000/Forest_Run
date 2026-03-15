# Technical Architecture

## Runtime Stack

- `MainActivity`: fullscreen host activity
- `GameView`: top-level `SurfaceView`
- `GameThread`: render/update loop
- `GameStateManager`: score, distance, bloom, seeds, persistence-facing run state
- `EntityManager`: spawning, updating, collision checks, pass rewards, seed orb spawning

## Major Packages

### `engine`

- `GameView.kt`: owns the game lifecycle and screen state routing
- `InputHandler.kt`: touch gesture translation
- `GameStateManager.kt`: run-scoped mutable state
- `BiomeManager.kt` / `Biome.kt`: biome cycle and color blending
- `SpriteManager.kt`: sprite loading from `app/src/main/assets/sprites`
- `SpriteSizing.kt`: shared aspect-ratio helpers so imported art is drawn at correct proportions
- `SaveManager.kt`: shared preferences + ghost binary persistence
- `LeitmotifManager.kt` / `SfxManager.kt` / `HapticManager.kt`: feedback systems

### `entities`

- `Player.kt`: player physics and animation state machine
- `Entity.kt`: common entity base class
- `EntityFactory.kt`: concrete entity creation
- `flora/`, `trees/`, `birds/`, `animals/`: 19 gameplay entity implementations

### `systems`

- `ParticleManager.kt`: pooled particles and effect presets
- `SeedOrbManager.kt`: collectible seed orb lifecycle
- `GhostRecorder.kt` / `GhostPlayer.kt`: best-run recording and replay

### `ui`

- `MainMenuScreen.kt`: menu scene with two-tap run start
- `GardenScreen.kt`: persistent unlock screen
- `HUD.kt`: gameplay overlay
- `GameOverScreen.kt`: restart overlay
- `FlavorTextManager.kt`: floating text feedback

## Verified Behavior Notes

- `Final_Assets (2)` is the only checked-in source asset pack; runtime sheets are regenerated from it through `scripts/import_final_assets.py`.
- Menu and garden taps are routed using actual touch coordinates from `GameView`.
- Menu launch and menu-to-playing transition were verified on a Vivo 1933 via instrumentation.
- Gameplay loop advancement and jump input were verified on a Vivo 1933 via instrumentation.
- Biome cycle checkpoints across the full five-biome sequence were verified on a Vivo 1933 via instrumentation.
- Bloom activation/player sync was verified on a Vivo 1933 via instrumentation.
- Collision-driven death, game-over, and restart were verified on a Vivo 1933 via instrumentation.
- Garden navigation, unlock persistence, and return-to-menu flow were verified on a Vivo 1933 via instrumentation.
- All 19 entity types were spawned into a live run and updated on a Vivo 1933 via instrumentation.
- Bloom now synchronizes gameplay state with player visuals/audio on activation and expiry.
- Entity pass rewards fire once per entity instance instead of every frame after passing.
- Lifetime seed and high score persistence now use the same `SaveManager` store across gameplay and garden UI.
- Opening run pacing now seeds far-ahead entities so gameplay does not begin on an empty-looking screen.
- Wolf howl and dog bark SFX are triggered from their actual gameplay state transitions.

## Known Design Boundaries

- The parallax background is still tint-driven and procedural rather than fully hand-painted layered scenery.
- The project is single-module and fully local; there is no networking layer.
