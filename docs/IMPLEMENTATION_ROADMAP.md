# Forest_Run — Restored Implementation Roadmap

This restores the original roadmap as the required implementation target. Items that are not fully done belong in TODO. The exhaustive current gap list lives in [docs/TODO_MATRIX.md](/home/anurag-basistha/Projects/TODO/Forest_Run/docs/TODO_MATRIX.md).

## Phase Status Summary

| Phase | Status | Notes |
|---|---|---|
| 0 | Done | project skeleton exists |
| 1 | Done | game loop exists |
| 2 | Done | input system exists |
| 3 | Partial | player exists but dream-spec feel still needs work |
| 4 | Partial | parallax exists, art still incomplete |
| 5 | Partial | HUD exists, readability is materially improved, and Bloom power-state clarity is much stronger; final device-proof polish still needs work |
| 6 | Partial | player sprites exist, face overlay dream not complete |
| 7 | Done | base entity framework exists |
| 8 | Partial | flora classes exist and now have stronger lure, rhythm, gust, and safe-thread staging, but final device tuning and cactus memory payoff still need work |
| 9 | Partial | tree classes exist and now have stronger curtain, canopy, precision-gap, and gust staging, but final scenic richness and device tuning still need work |
| 10 | Partial | bird classes exist, and ordinary-play warning/payoff staging is stronger, but final readability and device validation still need work |
| 11 | Partial | animal classes exist, personality delivery insufficient |
| 12 | Done | entity manager and spawner exist |
| 13 | Partial | biome cycle exists, full mood transformation incomplete |
| 14 | Partial | particle system exists, breathing-forest density incomplete |
| 15 | Partial | camera effects exist |
| 16 | Partial | flavor text exists, full quote/dialogue coverage missing |
| 17 | Partial | mercy, pacifist, and friendship systems exist; deeper route-like payoff still missing |
| 18 | Partial | persistent encounter memory and costumes now exist in baseline form |
| 19 | Partial | ghost exists, now delays reveal and suppresses after impacts for clearer live play, and still needs final tuning and validation |
| 20 | Partial | audio states exist, full leitmotif system incomplete |
| 21 | Partial | haptics exist, tuning incomplete |
| 22 | Partial | canonical lifecycle, run-summary carryover, and a stronger rest carry-home preview now exist; full authored recovery richness still incomplete |
| 23 | Partial | garden exists as real hub with wardrobe, memory stats, last-run carry-home, mood ambience, bond traces, and fallback arrival continuity; full sanctuary feel still incomplete |
| 24 | TODO | full bespoke background artwork not finished |
| 25 | TODO | major polish pass still required |
| 26 | TODO | full performance/device truth validation incomplete |
| 27 | TODO | product not ready for final store release quality |

## Original Phase Targets

### Phase 0 — Android Studio Project Skeleton

Goal: correct Android project structure, orientation, and full-screen setup.

### Phase 1 — Core Game Loop Engine

Goal: stable `SurfaceView` loop with frame-independent timing.

### Phase 2 — Input System

Goal: correct classification of tap, hold, and swipe-down.

### Phase 3 — Player Class & Physics

Goal: variable-height jump, ducking, squash/stretch, satisfying feel.

### Phase 4 — Parallax Background System

Goal: four background layers with seamless looping.

### Phase 5 — HUD

Goal: score, seeds, Bloom meter, readable pixel-font UI.

### Phase 6 — Sprite System & Player Sprites

Goal: full player animation suite, contextual face overlay.

### Phase 7 — Base Entity System & SwayComponent

Goal: reusable entity base and wind sway architecture.

### Phase 8 — Ground Flora

Goal: all five flora implemented with unique behaviors and readable hitboxes.

### Phase 9 — Trees

Goal: all four trees implemented as space-constraining scenic hazards.

### Phase 10 — Birds

Goal: all five birds implemented with clear unique aerial logic.

### Phase 11 — Animals

Goal: all five animals implemented with personality, AI, and mercy hooks.

### Phase 12 — EntityManager & Spawner

Goal: endless spawning, pooling, difficulty ramp, collision classification.

### Phase 13 — Biome System

Goal: biome transitions every 500m with visual, spawn, and atmosphere changes.

### Phase 14 — Full Particle System

Goal: petals, dust, pollen, seed bursts, Bloom trails, fireflies, glow, kindness bursts.

### Phase 15 — Camera Effects

Goal: screen shake and speed-based world scale.

### Phase 16 — Flavour Text System

Goal: event popups plus contextual rest quotes and dialogue bubbles.

### Phase 17 — Mercy & Pacifist Systems

Goal: mercy hearts, spare events, friendship bonus.

### Phase 18 — Persistent Memory System

Goal: remembered encounters, costumes, deja vu.

### Phase 19 — Ghost Run System

Goal: motivational best-run ghost that feels haunting, not broken.

### Phase 20 — Leitmotif Audio System

Goal: music states unified by a common forest motif.

### Phase 21 — HapticManager

Goal: every important event has satisfying physical feedback.

### Phase 22 — Full Game State System

Goal: full menu -> run -> Bloom -> rest -> restart lifecycle.

### Phase 23 — Garden Screen

Goal: persistent meta-loop where the forest grows with the player.

### Phase 23A — Forest Memory Layer

Goal: make the world remember tone, not only totals.

### Phase 23B — Relationship Arcs

Goal: turn major creatures into remembered relationships rather than obstacle classes.

### Phase 23C — Personal Return Moments

Goal: make the game notice when the player returns, struggles, or reaches personal milestones.

### Phase 23D — Quiet Story Fragments

Goal: tell more through poetic fragments, creature thoughts, and rare reflections without overexplaining.

### Phase 24 — Real Background Artwork

Goal: replace remaining procedural placeholder scenery with final art.

### Phase 25 — Polish Pass

Goal: every detail that turns the game from functional into memorable.

### Phase 26 — Performance Audit

Goal: stable 60 FPS on real hardware.

### Phase 27 — Google Play Preparation

Goal: complete, shippable, polished store-ready product.

## Non-Negotiable TODO Priorities

- DONE: entity size/readability on phone materially improved
- DONE: spawn sparsity materially reduced
- DONE: ghost duplication confusion materially reduced
- DONE: formal device acceptance checklist now exists and mirrors deterministic scenario names
- TODO: verify every entity behavior on actual device
- DONE: seeds, Bloom, mercy hearts, and garden loop are materially clearer in play
- PARTIAL: persistent memory, pacifist, dialogue, mercy, costumes, and facial presentation systems now exist, including baseline relationship keepsake rewards plus save-backed kindness/tender streak carry-over; finish deeper cross-run payoff and richer route feel
- PARTIAL: forest mood, personal return moments, major-creature relationship arcs, and quiet story fragments now exist as explicit implementation work, including repeated-harm caution beats, repeated-kindness warmth beats, milestone-sensitive warmth, Bloom-afterglow baseline, and tracked live encounter cue swaps; deeper authored payoff still needs to be built
- PARTIAL: startup, rest, and Garden now share a baseline authored session-arc copy layer; finish full sanctuary-grade atmosphere and recovery flow
