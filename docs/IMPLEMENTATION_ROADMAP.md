# Implementation Roadmap To The Original Vision

This roadmap restores the original phase-based ambition while also marking current status. `Done` means implemented in code at a basic level. `Partial` means present but not yet strong, readable, or complete enough to satisfy the vision. `Missing` means not truly built yet.

## Phase 0 To Phase 5: Core App And Baseline Loop

- `Done`: Android project, landscape setup, Gradle build, `SurfaceView` loop
- `Done`: player movement, jump, hold, duck
- `Done`: basic HUD, score, distance, seeds, Bloom meter
- `Partial`: phone-scale readability and presentation clarity

## Phase 6: Final-feel Player Presentation

- `Done`: sprite-driven player animations
- `Partial`: jump and run feel
- `Missing`: full face/eye expression layer from the dream spec
- `Missing`: perfect staging so the player always reads the active body cleanly

## Phase 7 To Phase 12: Entity Foundation And Endless Run

- `Done`: entity base classes, factory, spawn manager, collision loop
- `Done`: 19 entity classes exist
- `Partial`: distinct runtime behavior for many entities
- `Missing`: enough scale, spacing, and staging to make those differences obvious in normal play
- `Missing`: confidence that all entities feel cute, readable, and memorable on device

## Phase 13: Biome Journey

- `Done`: five-biome cycle, color blending, biome-specific pools
- `Partial`: mood-chapter feeling
- `Missing`: full scenic transformation, hand-painted/parallax identity, richer transitions

## Phase 14: Particles And Environmental Life

- `Done`: particle system and multiple presets
- `Partial`: Bloom aura, mercy stars, death bursts, seed collection feedback
- `Missing`: full “breathing forest” density with petals, fireflies, leaf life, atmospheric abundance

## Phase 15: Camera And Feedback Juice

- `Done`: shake and milestone response
- `Partial`: cinematic feel
- `Missing`: all the dream-spec polish beats that make the run feel authored rather than serviceable

## Phase 16: Flavor Text And Reactive Personality

- `Done`: `FlavorTextManager`
- `Partial`: some animal reactions
- `Missing`: enough reliable trigger coverage and visual emphasis to make the system emotionally central
- `Missing`: determination/rest quote richness from the original dream

## Phase 17: Mercy, Spare, And Pacifist Systems

- `Done`: mercy hearts exist
- `Partial`: some mercy-linked entity behavior exists
- `Missing`: fully legible spare events and pacifist-feeling run outcomes
- `Missing`: biome-level friendship bonus and stronger peace-vs-chaos logic

## Phase 18: Persistent Memory

- `Done`: persistent seeds, high score, garden progress, ghost persistence
- `Missing`: persistent encounter memory manager
- `Missing`: costume overlays based on repeated encounters
- `Missing`: repeat-killer deja vu system
- `Missing`: stronger “the forest remembers” cross-session identity

## Phase 19: Ghost Run

- `Done`: ghost recording and playback
- `Partial`: concept exists
- `Missing`: clean player-facing presentation; current user report says it actively confuses the run
- `Missing`: wave-off, overtaken moment, and tasteful default handling

## Phase 20: Leitmotif Audio System

- `Done`: music state transitions and SFX hooks
- `Partial`: layered run audio identity
- `Missing`: fully authored leitmotif treatment across every music state
- `Missing`: stronger emotional scoring and more obvious biome/audio identity

## Phase 21: Haptics

- `Done`: jump, hit, mercy, milestone, and bloom haptic hooks
- `Partial`: perceptual tuning quality

## Phase 22: Full Session Lifecycle

- `Done`: menu -> run -> game over -> restart
- `Done`: menu -> garden -> back
- `Partial`: soft emotional fall and reflective return
- `Missing`: determination-style rest layer and fully authored closure after failure

## Phase 23: Garden Meta-Loop

- `Done`: garden screen, unlock persistence, spend lifetime seeds
- `Partial`: chill restorative feel
- `Missing`: the sense of a truly growing personal sanctuary over many sessions

## Phase 24: Final Art Pass

- `Partial`: imported sprites and procedural scene drawing
- `Missing`: full bespoke background art, hand-crafted layers, scenic richness, strong visual identity across every biome

## Phase 25: Polish Pass

Top priority gaps based on current user feedback:

- `Missing`: increase entity size and on-phone readability
- `Missing`: tune spawn pacing so the forest feels populated and legible
- `Missing`: resolve ghost duplication confusion immediately
- `Missing`: make seeds, Bloom, hearts, and garden loop unmistakable during play
- `Missing`: make every creature’s cute unique behavior actually read during normal play

## Phase 26: Performance And Device Truth

- `Partial`: host-side tests and some device verification exist
- `Missing`: deep manual validation of every entity behavior on real phones
- `Missing`: confidence that presentation quality holds under long sessions

## Phase 27: Product Completion

The project should not be considered complete until the following are true:

- the original dream spec is represented in docs and in the shipped feel of the game
- all major systems are both implemented and player-perceivable
- no core fantasy depends on reading code to believe it exists
- the user can play one session on a phone and immediately feel:
  cute forest, unique creatures, mercy, Bloom, memory, garden meaning
