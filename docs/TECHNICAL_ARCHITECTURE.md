# Technical Architecture

## Purpose

This document describes both the current runtime architecture and the architectural gaps that still separate the repo from the original product vision.

## Runtime Stack

- `MainActivity`: fullscreen host activity
- `GameView`: top-level `SurfaceView`, screen routing, update/draw orchestration
- `GameThread`: render/update loop
- `GameStateManager`: score, distance, Bloom, seeds, hearts, persistence-facing state
- `EntityManager`: spawning, updating, collision checks, pass events, seed orb spawning

## Major Packages

### `engine`

- `GameView.kt`: top-level lifecycle and state routing
- `InputHandler.kt`: touch gestures
- `GameStateManager.kt`: run state and progression counters
- `BiomeManager.kt` and `Biome.kt`: five-biome cycle and color blending
- `SpriteManager.kt`: runtime sprite loading
- `SaveManager.kt`: local persistence
- `LeitmotifManager.kt`, `SfxManager.kt`, `HapticManager.kt`: feedback systems

### `entities`

- `Player.kt`: movement, animation state machine, Bloom/rest integration
- `Entity.kt`: base entity abstraction
- `EntityFactory.kt`: concrete creation
- `flora/`, `trees/`, `birds/`, `animals/`: 19 entity implementations

### `systems`

- `ParticleManager.kt`: pooled particles
- `SeedOrbManager.kt`: collectible seed orbs
- `GhostRecorder.kt` and `GhostPlayer.kt`: best-run replay

### `ui`

- `MainMenuScreen.kt`: two-tap run start
- `GardenScreen.kt`: garden unlock view
- `HUD.kt`: score, distance, seeds, Bloom, hearts
- `GameOverScreen.kt`: death/restart overlay
- `FlavorTextManager.kt`: floating text

## Verified Runtime Reality

- Core flow exists.
- Biome cycling exists.
- Bloom and seeds exist in code.
- Garden persistence exists.
- Ghost recording and playback exist.
- Multiple entity-specific behavior classes exist.

## Architectural Gaps To The Dream

### Readability Architecture Gap

The repo lacks a dedicated tuning layer for:

- entity screen scale by class
- per-device readability tuning
- spawn spacing tuned for visibility as well as difficulty
- on-device presentation diagnostics

Without that, dream-spec behavior can exist in code and still fail in play.

### Personality Architecture Gap

The dream vision wanted stronger systems than currently exist:

- persistent encounter memory manager
- costume overlay system
- repeat-killer or deja vu system
- richer rest-quote system
- more robust mercy/spare orchestration beyond isolated entity logic

### Presentation Architecture Gap

The world is still largely procedural/tint-driven in places. The dream vision wanted:

- richer hand-authored biome scenes
- stronger dialogue bubble and character presentation
- more obvious spectacle around Bloom and forest atmosphere

### Ghost UX Gap

Ghost playback exists technically, but current user feedback says its presentation can make the live runner feel visually wrong. That means the architecture needs a better player-facing policy for:

- when the ghost appears
- how visible it is
- whether it should be disabled by default
- how it avoids obscuring the live body

## Known Immediate Product Bugs From User Feedback

- Entity scale and spacing are failing mobile readability.
- Ghost presentation is confusing enough to break trust in the run.
- Implemented systems such as Bloom, mercy, and garden progression are not surfacing clearly enough to the player.

Those are not just balance tasks. They are architecture-to-experience failures and should be treated as first-class product defects.
