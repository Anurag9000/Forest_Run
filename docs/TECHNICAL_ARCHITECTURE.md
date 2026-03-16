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
- Partial: several dream-spec systems now exist as first-class modules, including a deterministic encounter verification harness, but fuller authored content tooling and final content depth are still in progress.

## 2. Engine — GameView & Game Loop

The architecture requires:

- `SurfaceView`
- dedicated game thread
- frame-independent `deltaTime`
- centralized update/draw orchestration

### Current Status

- Implemented: game loop and `GameView`.
- Implemented: canonical `RunSummary` payload now bridges the rest screen and Garden carry-home state.

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

- Implemented: garden/menu, playing, Bloom, rest summary, and fade-back-to-Garden routing.
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
- Partial: face layer exists, but stronger authored feel and final ghost/live-player tuning are still needed.

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
- Partial: pacing is materially improved and deterministic encounter verification now includes a broad acceptance suite; the remaining gap is content tuning and true device-proofing, not missing harness coverage.

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
- Partial: repeated-encounter memory, costumes, and persisted run-summary carry-home now exist in baseline form; richer payoff depth is still missing.

## 12. Emotional Memory Architecture

Approved emotional expansion requires explicit systems instead of scattered one-off counters.

Expected systems:

- `ForestMoodSystem` to evaluate recent run tone
- `RelationshipArcSystem` to track creature-specific familiarity and trust
- `ReturnMomentsSystem` to detect re-entry, absence, failure streaks, and milestone-sensitive greetings
- `StoryFragmentSystem` to author and select short poetic fragments for rest, Garden, weather, and creature thought moments

Expected qualities:

- canonical shared state instead of ad hoc booleans
- deterministic milestone evaluation
- authorable content tables for dialogue, quotes, ambience, and visual changes
- presentation hooks shared by rest, Garden, encounters, and persistence

### Current Status

- Implemented: `ForestMoodSystem` now exists as a first-class runtime and persistence layer.
- Implemented: `RelationshipArcSystem` now exists as a first-class runtime and persistence layer for Cat, Fox, Wolf, Dog, Owl, and Eagle.
- Partial: `ReturnMomentsSystem` now exists in baseline form.
- Partial: `ReturnMomentsSystem` now also surfaces repeated-harm caution beats, milestone-bond warmth, Bloom-heavy afterglow returns, and richer absence-sensitive Garden returns; broader authored combinations still need expansion.
- Partial: relationship stages are now persisted and surfaced in creature dialogue, encounter tuning, Garden strongest-bond presentation, bonded visitors, sanctuary traces, baseline milestone keepsake rewards, and tracked live encounter cue swaps for Cat/Fox/Wolf/Dog; deeper authored stage consequences still need expansion.
- Implemented: `StoryFragmentSystem` now exists as a first-class runtime and persistence layer for rest fragments, Garden reflections, and memory-page unlocks.
- Partial: fragment coverage now drives rest quotes, Garden reflection/carry-home presentation, bonded creature thoughts, weather-linked sanctuary writing, repeated-harm caution pages, milestone-gentleness pages, and Bloom-afterglow pages in baseline form.
- Implemented: `GardenSanctuaryPlanner` now derives visible sanctuary ambience, bond traces, and repeated-harm caution traces from mood, summary, and relationship state.
- Implemented: `ReadabilityProfile` now centralizes spawn pacing plus readability baselines across flora, trees, birds, and animals.
- Implemented: `BirdEncounterFlavor` now centralizes authored warning/pass text for the non-relationship bird family instead of leaving bird payoff as scattered fallback strings.
- TODO: persistence schema must still expand further for richer fragment unlock state and broader relationship milestone presentation.

## 13. Relationship Arc Authoring

Expected data per major creature family:

- first encounter
- repeated encounter count
- spared count
- hit count
- trust stage
- last meaningful interaction
- unlocked Garden presence or keepsake state
- associated quote/dialogue pools

### Current Status

- Implemented: formal relationship stages and baseline authoring data now exist for Cat, Fox, Wolf, Dog, Owl, and Eagle.
- Partial: encounter counts, stage persistence, stage-aware dialogue, stage-aware encounter tuning, history-aware live encounter cues, and baseline milestone keepsake/costume reward hooks now exist; deepen Garden presence and broader authored consequences.

## 14. Personal Return Moments Authoring

The game should support intimate return-sensitive moments instead of treating every session start the same.

Expected layers:

- first run of day detection
- long absence detection
- failure streak detection
- milestone-driven Garden greetings
- creature visit triggers
- emotional-state-sensitive dialogue hooks

### Current Status

- Implemented: dedicated return-state data now exists for greetings, absence reactions, rough-run comfort beats, milestone Garden messages, and stronger Bloom / gentle-bond combinations.
- TODO: deepen authoring hooks further and broaden emotional-state coverage beyond the current milestone, Bloom, and gentle-return combinations.

## 14A. Ghost Readability Policy

Expected qualities:

- ghost should teach or motivate without competing with the live player
- early run readability should favor the live runner first
- collisions and stumbles should not create ghost clutter during recovery

### Current Status

- Partial: ghost playback now delays its reveal at run start and briefly suppresses after impacts so the live player remains visually dominant in the most confusing moments.
- TODO: finish final on-device tuning for distance thresholds, fade timing, and overall readability under dense encounters.

## 15. Quiet Story Fragment Authoring

Expected inputs:

- last killer
- recent emotional state
- run tone
- weather or biome context
- relationship stage
- unlocked fragment state

Expected outputs:

- contextual quote selection
- creature thought fragments
- weather-linked lines
- rare Garden reflections
- unlockable memory pages

### Current Status

- Partial: contextual rest quotes and some dialogue systems already exist.
- Partial: milestone-sensitive and Bloom-sensitive Garden fragment selection now exists in baseline form.
- TODO: formalize broader fragment authoring, unlock state, and short-form poetic content selection.

## 16. Dynamic Difficulty Curve

Expected:

- speed increase
- spawn interval pressure
- biome escalation

### Current Status

- Implemented: base scaling.
- TODO: tune against readability and delight, not just raw challenge.
