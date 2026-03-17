# Forest_Run — Technical Architecture (Restored)

This restores the original technical architecture intent while keeping current implementation gaps explicit.

Canonical runtime truth wins when historical architecture notes disagree. Older six-biome variants, split-input notes, exact save-shape expectations, and exact package-placement expectations are preserved for traceability, not as active implementation blockers.

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

- Implemented: garden/menu, playing, Bloom, rest summary, and fade-back-to-Garden routing, with a stronger Bloom world-state presentation layer.
- PARTIAL: menu, rest, and Garden now share session-derived copy plus sanctuary-derived arrival badges and atmosphere cues; finish the last authored recovery richness and real-device proof.

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
- Partial: biome tinting now also drives stronger parallax wash, canopy shade, mist, drifting ambient life, and subtle speed/Bloom world-scale response.
- TODO: full scenic transformation and bespoke background art.

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
- Partial: repeated-encounter memory, costumes, persisted run-summary carry-home, relationship milestones, return-state data, fragment unlocks, and pacifist route tier carry-over now exist in stronger form, now including milestone-earned Dog/Owl/Eagle wardrobe rewards and sanctuary reward/cosmetic carry-home surfacing; richer payoff depth is still missing.

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
- Partial: `ReturnMomentsSystem` now also surfaces repeated-harm caution beats, repeat-killer `Same Shadow` returns, repeated-kindness `Stayed Gentle` returns, repeat-friend `Kept Finding You` returns, long-absence repeat-friend `Still Here` returns, merciful repeat-friend recognition, peaceful-Bloom hush returns, cleaner familiar-return beats, kind/merciful/peaceful route returns, milestone-bond warmth, Bloom-heavy afterglow returns, and richer absence-sensitive Garden returns; broader authored combinations still need expansion.
- Implemented: `ReturnMomentsSystem` now also exposes a non-mutating preview path so rest and startup flow can foreshadow Garden return beats without consuming return-state persistence.
- Partial: relationship stages are now persisted and surfaced in creature dialogue, encounter tuning, Garden strongest-bond presentation, bonded visitors, sanctuary traces, baseline milestone keepsake rewards, and tracked live encounter cue swaps for Cat/Fox/Wolf/Dog; deeper authored stage consequences still need expansion.
- Implemented: `StoryFragmentSystem` now exists as a first-class runtime and persistence layer for rest fragments, Garden reflections, and memory-page unlocks.
- Partial: fragment coverage now drives rest quotes, Garden reflection/carry-home presentation, bonded creature thoughts, weather-linked sanctuary writing, repeated-harm caution pages, repeated-kindness warmth pages, repeated-kindness clean-return pages, repeat-friend familiarity pages, merciful repeat-friend pages, repeat-killer `Same Shadow` pages, milestone-gentleness pages, peaceful-Bloom pages, Bloom-afterglow pages, and baseline kind/peaceful-route reflections.
- Implemented: `GardenSanctuaryPlanner` now derives visible sanctuary ambience, bond traces, repeated-harm caution traces, repeated-kindness trust traces, repeat-friend `Shared Path` traces, arrival badges, mist bands, lantern glows, and ground-light carry-home cues from mood, summary, and relationship state.
- Implemented: `SessionArcComposer` now centralizes authored startup, Garden-arrival, and rest carry-home copy so the macro loop uses shared emotional state instead of disconnected screen-local strings, while menu/rest/Garden visuals now read from the same sanctuary state.
- Implemented: `ReadabilityProfile` now centralizes spawn pacing plus readability baselines across flora, trees, birds, and animals.
- Implemented: `FloraEncounterFlavor` now centralizes authored flora payoff text so lure/rhythm/window feedback stops living as scattered fallback strings.
- Implemented: `TreeEncounterFlavor` now centralizes authored tree payoff text so curtain/canopy/gap/gust feedback stays consistent instead of scattering across tree classes.
- Implemented: `BirdEncounterFlavor` now centralizes authored warning/pass text for the non-relationship bird family instead of leaving bird payoff as scattered fallback strings.
- Implemented: `AnimalEncounterFlavor` now centralizes non-relationship animal fairness/payoff text where relationship logic is not the right source of truth.
- Partial: persistence schema now also carries kindness/tender streak state plus last-run pacifist route tier for cross-run emotional payoff, while repeat-killer escalation and repeat-friend familiarity are now derived from the persisted hit/kindness history.
- TODO: expand persistence further for richer fragment unlock state and broader relationship milestone presentation.

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
- Partial: encounter counts, stage persistence, stage-aware dialogue, stage-aware encounter tuning, history-aware live encounter cues, and broader milestone keepsake/costume reward hooks now exist across all tracked creatures; deepen Garden presence and broader authored consequences.

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

- Implemented: dedicated return-state data now exists for greetings, absence reactions, rough-run comfort beats, milestone Garden messages, stronger Bloom / gentle-bond combinations, and peaceful/merciful route return beats.
- Partial: rest flow can now preview return-state tone before the Garden transition without mutating saved greeting state.
- TODO: deepen authoring hooks further and broaden emotional-state coverage beyond the current milestone, Bloom, gentle-return, route-tier, and baseline preview combinations.

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

- Partial: contextual rest quotes and some dialogue systems already exist, and rest quotes now read full run state instead of only biome/killer context.
- Partial: milestone-sensitive, Bloom-sensitive, and route-sensitive Garden fragment selection now exists in baseline form.
- TODO: formalize broader fragment authoring, unlock state, and short-form poetic content selection.

## 15B. Mercy Route Presentation

Expected qualities:

- mercy-oriented runs should have an explicit route feel, not only counters
- route tone should travel through run rewards, rest, Garden, and persistence
- peaceful runs should read differently from merely gentle runs

### Current Status

- Implemented: `PacifistTracker` now classifies `Kind`, `Merciful`, and `Peaceful` route tiers from live run behavior.
- Partial: route tiers now flow through `RunSummary`, Garden/rest presentation, return moments, sanctuary carry-home, fragment unlocks, startup/homecoming tone, and stronger in-run mercy/reward language in baseline form.
- TODO: deepen route consequences further into broader world-state and production presentation.

## 15A. Session Arc Composition

Expected qualities:

- startup, rest, and Garden return should feel like one authored loop
- menu copy should reflect the last carried-home emotional state
- rest should preview what kind of homeward reception the player is returning to
- Garden should preserve continuity even when no special return beat fires

### Current Status

- Implemented: `SessionArcComposer` now derives menu atmosphere, rest recovery copy, carry-home wording, and fallback Garden arrival lines from the same saved emotional state.
- TODO: expand authored variety further and finish device-proofing the macro flow.

## 16. Dynamic Difficulty Curve

Expected:

- speed increase
- spawn interval pressure
- biome escalation

### Current Status

- Implemented: base scaling.
- TODO: tune against readability and delight, not just raw challenge.
