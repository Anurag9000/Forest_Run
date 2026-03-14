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
- `SpriteManager.kt`: sprite loading and fallback generation
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

- Menu and garden taps are routed using actual touch coordinates from `GameView`.
- Bloom now synchronizes gameplay state with player visuals/audio on activation and expiry.
- Entity pass rewards fire once per entity instance instead of every frame after passing.
- Lifetime seed and high score persistence now use the same `SaveManager` store across gameplay and garden UI.

## Known Design Boundaries

- The menu, garden, and parallax background still use intentionally simple drawn shapes/tints in places instead of fully bespoke final art.
- The project is single-module and fully local; there is no networking layer.
