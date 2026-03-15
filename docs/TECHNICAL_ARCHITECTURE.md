# Forest_Run — Technical Architecture (Restored)

This restores the original technical architecture intent while keeping current implementation gaps explicit.

## 1. Project Structure

The dream architecture includes:

- `MainActivity`
- `engine/` core loop and state
- `entities/` for player, flora, trees, birds, animals
- `systems/` for spawning, biomes, particles, audio, haptics, memory, mercy, ghosting
- `ui/` for HUD, game over, garden, dialogue bubbles
- `utils/` for math, sprite helpers, save helpers

### Current Status

- Implemented: broad package structure exists.
- TODO: several dream-spec systems still do not exist as first-class dedicated modules.

## 2. Engine — GameView & Game Loop

The architecture requires:

- `SurfaceView`
- dedicated game thread
- frame-independent `deltaTime`
- centralized update/draw orchestration

### Current Status

- Implemented: game loop and `GameView`.

## 3. Game State Machine

Dream states:

- `MENU`
- `PLAYING`
- `BLOOM_STATE`
- `REST`

Expected transitions:

- menu to run
- run to Bloom
- Bloom back to run
- run to rest
- rest back into retry flow

### Current Status

- Implemented: menu, playing, Bloom, rest/game-over, restart routing.
- TODO: more fully authored emotional transitions and softer rest presentation.

## 4. Player State Machine

Dream player states include:

- running
- jump start
- jumping
- apex
- falling
- landing
- ducking
- Bloom
- rest

Expected qualities:

- variable jump
- Mario-abort mechanic
- apex hover
- velocity-synced run animation
- squash/stretch

### Current Status

- Implemented: most core player states and apex feel.
- TODO: face layer, stronger authored feel, and cleaner ghost/live-player readability.

## 5. Entity System

The dream architecture expects:

- a base `Entity`
- subclass-specific behavior
- sway component
- particle attachments where needed
- no generic-feeling obstacle pipeline

### Current Status

- Implemented: entity base and concrete classes.
- TODO: ensure architecture supports device-tuned readability and stronger runtime personality delivery.

## 6. EntityManager & Spawner

Expected responsibilities:

- active entity list
- spawn timing
- biome-aware pools
- despawn logic
- collision classification
- pooling/recycling

### Current Status

- Implemented: core manager exists.
- TODO: tune pacing so the forest feels populated and readable rather than sparse.

## 7. BiomeManager

Dream responsibilities:

- biome lookup by distance
- biome-specific pools
- transition orchestration
- atmosphere changes with world progression

### Current Status

- Implemented: five-biome cycle and blending.
- TODO: full scenic transformation and richer ambient system.

## 8. Input Handler

Expected:

- robust touch handling
- tap, hold, swipe-down
- responsive player control

### Current Status

- Implemented.

## 9. Sprite Sheet / Asset Helpers

Expected:

- sprite slicing
- asset loading
- predictable animation plumbing

### Current Status

- Implemented in practical form.

## 10. Haptic Manager

Expected:

- short pulse
- long pulse
- double tap
- medium pulse

### Current Status

- Implemented.
- TODO: final tactile tuning on real hardware.

## 11. Save / Persistence

Dream persistence includes:

- high score
- lifetime seeds
- garden progress
- richer memory systems

### Current Status

- Implemented: score, seeds, garden, ghost.
- TODO: repeated-encounter memory and costumes.

## 12. Dynamic Difficulty Curve

Expected:

- speed increase
- spawn interval pressure
- biome escalation

### Current Status

- Implemented: base scaling.
- TODO: tune against readability and delight, not just raw challenge.
