# Forest_Run — Spec Traceability Matrix

This document is the exhaustive documentation-side traceability layer for the repo as reconstructed from the historical source docs in git and the current codebase.

It answers:

1. What exactly was originally specified?
2. Where did that specification come from?
3. Is it `Implemented`, `Partial`, `TODO`, or `Conflict` in the modern repo?

Status keys:

- `Implemented`: clearly present in the current repo
- `Partial`: present in some form but weaker, incomplete, unclear in play, or materially divergent
- `TODO`: not truly implemented yet
- `Conflict`: historical specs disagree with each other or with current code and need final product-direction resolution

## Source Inventory

| Source | Commit | Purpose |
|---|---|---|
| `docs/GDD.md` | `947e4e1` | original game design document |
| `docs/ANDROID_SETUP.md` | `947e4e1` | setup, manifest, assets, audio, debug checklist |
| `docs/TECHNICAL_ARCHITECTURE.md` | `947e4e1` | class architecture and systems plan |
| `docs/VISUAL_FX_SPEC.md` | `947e4e1` | parallax, particles, wind, lighting, camera |
| `docs/ENTITY_DATABASE.md` | `947e4e1` | all flora, trees, birds, animals, biome affinities |
| `docs/UNDERTALE_VIBE.md` | `9d7d455` | personality, mercy, memory, quotes, leitmotif |
| `docs/IMPLEMENTATION_ROADMAP.md` | `99bb40a` | 27-phase implementation plan |
| `spec.md` | `b3d59d8` | later high-level official spec with additional product claims |

## Canonical Runtime Truth

The repo now treats the following as the frozen coherent canon for implementation:

- `5` runtime biomes: `MEADOW`, `ORCHARD`, `ANCIENT_GROVE`, `DUSK_CANYON`, `NIGHT_FOREST`
- Bloom auto-activates at `8` seeds for `6` seconds
- failure flow is `run -> rest summary -> fade -> Garden -> run`
- tap jump, hold higher jump, swipe-down duck remains the canonical input model
- older conflicting historical ideas are preserved only for source traceability and flavor recovery

## Historical Conflicts Preserved For Traceability

- `Conflict`: biome count is `5` in current code and later docs, but `6` in some historical docs and the original technical architecture / README.
- `Conflict`: Bloom seed threshold is `10` in older GDD and setup checks, `8` in current code and current docs.
- `Conflict`: Bloom duration is `5s` in older docs, `6s` in current code.
- `Conflict`: original GDD frames failure as a soft return to Garden, while older intermediate runtime revisions used a game-over / restart loop before the current rest-summary-to-Garden canon was restored.
- `Conflict`: older spec says jump on one side of screen and duck on the other, while current game uses tap / hold / swipe gestures anywhere.
- `Conflict`: older spec claims codebase is finalized and production-ready, but the actual repo and user feedback clearly contradict that.

These conflicts must be resolved intentionally during implementation, not by accident.

## Current Code Anchor Inventory

Core classes currently present:

- `MainActivity`
- `GameView`, `GameThread`, `GameStateManager`, `EntityManager`, `BiomeManager`, `DifficultyScaler`, `ParallaxBackground`, `LeitmotifManager`, `SfxManager`, `HapticManager`, `SaveManager`
- `Player`, `Entity`, `EntityFactory`, `EntityType`, `PlayerState`, `CollisionResult`
- all current flora, tree, bird, and animal entity classes
- `ParticleManager`, `ParticleEmitter`, `SeedOrbManager`, `GhostRecorder`, `GhostPlayer`
- `MainMenuScreen`, `GardenScreen`, `HUD`, `GameOverScreen`, `FlavorTextManager`

Missing dream-spec dedicated classes called out by historical docs:

- `Implemented`: `PersistentMemoryManager`
- `Implemented`: `MercySystem` as dedicated class
- `Implemented`: `PacifistTracker`
- `Implemented`: `DialogueBubbleManager`
- `Implemented`: `CostumeOverlay`
- `Implemented`: `FaceManager`

## Traceability By Source

### A. Original GDD (`947e4e1:docs/GDD.md`)

#### Vision & Pillars

- `Implemented`: endless runner identity on Android.
- `Partial`: juicy feedback pillar.
- `Partial`: alive breathing-forest pillar.
- `Partial`: readable obstacle pillar.
- `Partial`: rewarding seeds-to-garden pillar.
- `Partial`: behavioural AI pillar.

#### Art Style & Visual Identity

- `Partial`: vibrant pixel-art identity exists in assets.
- `Partial`: colour palette and biome tinting exist.
- `Partial`: “forest is never still” through sway and particles exists in part.
- `TODO`: full hand-authored scenic richness and dense ambient life.

#### Colour Dynamics — Day/Night Cycle

- `Conflict`: original phase table is distance-based morning/day/golden hour/twilight/night, while current repo uses five biome chapters with ambient tint blending.
- `Partial`: darkness and tint transitions exist.
- `TODO`: explicit day-phase system as originally specified if still desired after conflict resolution.
- `Partial`: owl/night-specific behavior exists.
- `TODO`: firefly ambience at the full intended density.

#### Cold Start — Garden Main Menu

- `Implemented`: seated/resting menu scene and two-tap stand/run flow.
- `Partial`: ambient garden tone.
- `TODO`: full “no hard menu buttons, unlocked plants in background” authored richness.
- `TODO`: stronger footstep-synced musical launch feel.

#### Early Game (0–500m)

- `Conflict`: old GDD says only Cactus and Cats early; current spawn logic uses different pools and five-biome system.
- `Partial`: easier opening pacing exists.
- `TODO`: stricter curation of early-game teaching sequence if retained.
- `Partial`: seeds spawn and feed Bloom.

#### Mid Game (500m–1500m)

- `Implemented`: biome transitions every 500m in current design.
- `Partial`: background/spawn shifts.
- `Partial`: speed increase.
- `Partial`: music layering by progression.
- `Partial`: Foxes and Ducks as adapt-to-threat behaviors.

#### Bloom State

- `Conflict`: 10 seeds / 5s / configurable activation in older GDD vs 8 seeds / 6s / auto activation in current code.
- `Implemented`: Bloom meter, activation, invincibility window, and stronger activation spectacle.
- `Partial`: glow, aura, and player-following Bloom effects.
- `TODO`: speed doubling during Bloom as originally specified.
- `Partial`: passed obstacles now convert into stronger reward bursts and environmental reactions, but not yet the full intended “world transforms around you” version.
- `TODO`: psychedelic hyper-saturation and flowers blooming open along the path.
- `Partial`: music transition into Bloom.

#### Chaos Peak — Late Game

- `Partial`: late-game higher density and speed.
- `Partial`: Eagles and Wolves both exist.
- `Implemented`: milestone camera feedback.
- `Partial`: stronger high-wind climax feeling.
- `Partial`: layered fast music.

#### Soft Fall / Rest State

- `Partial`: current runtime restores the return-to-Garden loop, but the full calm reflective sit-down framing and authored emotional recovery are still weaker than the original dream spec.
- `Partial`: rest-like player state exists.
- `Partial`: short death-to-overlay pacing exists.
- `TODO`: true calm reflective end-run summary with seeds, combo/streak, contextual quote, and more restorative emotional framing.

#### Garden Meta-Loop

- `Implemented`: persistent lifetime seeds and unlock screen.
- `Partial`: next locked plant visibility and left-to-right unlock ladder.
- `TODO`: actual feeling of a lush personalized pixel forest over weeks.
- `TODO`: plant placement / deeper personalization if still desired.

#### Input System

- `Implemented`: tap jump, hold higher jump, swipe-down duck.
- `Conflict`: original docs mention multi-touch independence and some later spec mentions left/right side split input.
- `Partial`: variable jump height exists.

#### Scoring System

- `Implemented`: distance score and milestones.
- `Implemented`: seed collection affects progression.
- `Partial`: kindness bonus exists for Cat.
- `Partial`: close call / mercy effects exist.
- `TODO`: exact old scoring numbers and multiplier model if still desired.
- `TODO`: slow-mo sparkle effect on perfect dodge.

#### Seeds

- `Implemented`: collectible seeds and lifetime currency.
- `Partial`: trap placement above hazards.
- `TODO`: obstacle-to-seed conversion during Bloom to full dream-spec degree.

#### Haptics

- `Implemented`: jump, hit, mercy, milestone, Bloom haptic hooks.
- `Partial`: exact original tuning and feel.

#### Save System

- `Implemented`: local high score.
- `Implemented`: garden progress and lifetime seeds.
- `TODO`: JSON-rich garden save as originally described if current storage shape differs.
- `Implemented`: no cloud sync.

#### Audio Design

- `Partial`: menu / run / Bloom / rest music states.
- `Partial`: tempo scaling with run speed.
- `Implemented`: jump, land, howl, bark, Bloom SFX hooks.
- `TODO`: full authored layering and stronger emotional scoring.

#### Unlockables & Progression

- `Implemented`: garden plants and costs in spirit.
- `Partial`: exact unlock catalog and costs may differ.
- `Implemented`: no animal/bird garden unlocks.

### B. Android Setup Guide (`947e4e1:docs/ANDROID_SETUP.md`)

#### Project / Manifest / Activity / Gradle

- `Implemented`: package, Kotlin Android app, API 24+, API 34 target, landscape orientation.
- `Implemented`: immersive fullscreen and `MainActivity` host.
- `Implemented`: haptic permission.
- `Partial`: exact optional wake-lock and final release gradle hardening.
- `Implemented`: Java 17 era toolchain.

#### Asset Folder Structure

Player asset family:

- `Partial`: run / jump / duck / land / rest / Bloom sprite families exist in imported assets.
- `TODO`: exact historical filenames and frame counts preserved line-for-line.

Ground flora asset family:

- `Partial`: cactus, lily, hyacinth, eucalyptus, vanilla assets exist in practical form.
- `TODO`: explicit separate glow overlays and every exact asset naming expectation.

Tree asset family:

- `Partial`: willow, jacaranda, bamboo, cherry blossom assets exist.
- `TODO`: exact background/branch segmentation from old asset plan.

Bird asset family:

- `Partial`: owl, duck, eagle, tit, chickadee assets exist.
- `TODO`: exact idle/dive/specific frame-set coverage from old setup guide.

Animal asset family:

- `Partial`: wolf, cat, fox, hedgehog, dog assets exist.
- `TODO`: exact walk/run/howl/trot/jump/curl/bark sheet coverage.

Particles & FX assets:

- `Partial`: jump dust, seed, sparkles, etc. exist in part.
- `TODO`: full original particle asset list including all specific names.

UI assets:

- `TODO`: explicit custom Bloom meter background/fill art and seed icon set as originally named.

Background assets:

- `TODO`: full bespoke background bitmap suite from old setup guide.

#### Audio File Inventory

- `Partial`: several music and SFX files exist in `res/raw`.
- `TODO`: exact historical file inventory and naming set.

#### Theme & Styles

- `Implemented`: no-action-bar / fullscreen gameplay presentation.

#### Loading Assets in Code

- `Implemented`: runtime asset loading helpers/managers exist.

#### Performance Checklist

- `Partial`: delta-time movement, reuse of core objects, some pooling.
- `TODO`: complete validation of every old checklist item and keep them as explicit regression checks.

#### Debugging Physics Checklist

- `Partial`: many of the listed mechanics exist.
- `TODO`: preserve the entire old checklist as a manual QA suite and verify each item on hardware.

### C. Technical Architecture (`947e4e1:docs/TECHNICAL_ARCHITECTURE.md`)

#### Project Structure

- `Implemented`: broad `engine`, `entities`, `systems`, `ui`, `utils` package structure.
- `Conflict`: some historical systems were planned under `systems/` but current repo places them under `engine/` or omits them entirely.

#### Engine — GameView & GameThread

- `Implemented`: `GameView` and `GameThread`.
- `Implemented`: frame-independent game loop.
- `Partial`: exact old thread timing semantics may differ.

#### Game State Machine

- `Implemented`: app/menu and run state layers exist.
- `Conflict`: current naming and lifecycle differ from some older documents.
- `Partial`: Bloom and rest/game-over orchestration.

#### Player State Machine

- `Implemented`: running, jump start, jumping, apex, falling, landing, ducking, Bloom, rest.
- `Partial`: Mario-abort mechanic exactness.
- `Partial`: animation-FPS sync to speed.

#### Entity System

- `Implemented`: base `Entity`, subclasses, sway component.
- `Partial`: original “component-based system” ambition.

#### EntityManager & Spawner

- `Implemented`: manager, spawning, pooling, collisions.
- `Partial`: original exact spawn/difficulty formulas.

#### BiomeManager

- `Implemented`: biome enum and distance-based cycling.
- `Conflict`: six-biome historical version vs five-biome current version.

#### Input Handler

- `Implemented`: touch-driven jump/hold/duck processing.
- `Conflict`: old side-of-screen input split from `spec.md` not used in current code.

#### Sprite Sheet Helper

- `Implemented`: sprite slicing/animation helper infrastructure in practical form.

#### Haptic Manager

- `Implemented`: `HapticManager`.

#### Save Manager

- `Implemented`: local save manager.
- `Partial`: richer JSON save system and memory fields.

#### Dynamic Difficulty Curve

- `Implemented`: base speed / spawn interval ramping.
- `Partial`: exact original formulas.

### D. Visual & FX Spec (`947e4e1:docs/VISUAL_FX_SPEC.md`)

#### Parallax Scrolling

- `Implemented`: parallax background system.
- `Partial`: four distinct layers in dream-spec scenic richness.
- `TODO`: full bespoke art, blurred foreground identity, and far-background zoom feel.

#### Wind System

- `Implemented`: `SwayComponent`.
- `Partial`: differentiated per-entity tuning.
- `TODO`: full global wind variable behavior, distance-based wind escalation, and hair response.

#### Character Animation — Squash & Stretch

- `Implemented`: core squash/stretch states.
- `TODO`: hair/clothing secondary motion.
- `Partial`: 48-frame-feeling run identity via imported art.

#### Particle Systems

- `Implemented`: particle manager and multiple effect presets.
- `Partial`: petal drift, seed, mercy, Bloom, death, movement feedback in some form.
- `TODO`: exact full nine-system suite at intended density and scenic quality.

#### Dynamic Lighting

- `Partial`: tint and darkness overlays.
- `TODO`: exact phase table and smooth hardware-shader-like transitions if still desired.

#### Camera Effects

- `Implemented`: screen shake.
- `Partial`: speed-based zoom/pullback.

#### Environmental Bloom Reaction

- `Partial`: nearby flora/tree reactions now exist on Bloom conversions.
- `TODO`: nearby flowers blooming open as the runner passes in the full intended sense.

#### Spirit Of The Forest Bloom Visuals

- `Partial`: aura, activation burst, HUD conversion feedback, and stronger screen treatment now exist.
- `TODO`: stronger world transformation, obstacle dissolves, UI pulse ring, and full power-fantasy spectacle.

### E. Entity Database (`947e4e1:docs/ENTITY_DATABASE.md`)

#### Ground Flora

Lily of the Valley:

- `Implemented`: low hazard and sway.
- `Partial`: night glow in spirit.
- `TODO`: strong lure/distraction role and above-it seed-trap identity.

Hyacinth:

- `Implemented`: brush / mercy zone logic.
- `TODO`: explicit group-of-three as one logical timing unit.
- `TODO`: stronger long-jump rhythm identity and pollen personality.

Eucalyptus:

- `Partial`: obstacle and sway.
- `TODO`: trapezoid-feeling hitbox and strong leaner identity.

Vanilla Orchid:

- `Partial`: obstacle exists.
- `TODO`: true two-collider low+high window and visual clarity.

Cactus:

- `Implemented`: classic rigid hazard.
- `Partial`: stronger warning staging and repeat-killer flavor response now exist.
- `TODO`: broader memory/cosmetic integration and device-proofing.

#### Trees

Weeping Willow:

- `Partial`: presence and hazard role.
- `TODO`: obscuring-curtain gameplay and shadow-zone effect.

Jacaranda:

- `Partial`: tree presence, clearer petal curtain, and stronger pass reward now exist.
- `TODO`: full-screen petal-canopy spectacle and device-proofing.

Bamboo:

- `Partial`: obstacle presence.
- `TODO`: full gap-threading precision gameplay.

Cherry Blossom:

- `Partial`: tree presence, gust staging, and stronger petal-storm feel now exist.
- `TODO`: full gust-pressure behavior and device-proofing.

#### Birds

Owl:

- `Implemented`: reactive dive on jump-nearby logic.
- `Partial`: sleeping/perched/noise-punish mood, clearer dive telegraphing, alert ring, and stronger amber eye-glow now exist.
- `TODO`: deeper night drama payoff and device-proofing.

Duck:

- `Implemented`: low-flying hazard and clearer duck-lane teaching role.
- `TODO`: final on-device cue polish and stronger quack payoff.

Eagle:

- `Implemented`: dive hazard.
- `Partial`: lock-on cue, target-zone read, and clean-pass reward payoff are materially stronger.
- `TODO`: stronger dramatic cue understanding and device-proofing.

Tit / Chickadee:

- `Implemented`: grouped bird classes.
- `Partial`: stronger sine-wave rhythm identity for tits.
- `Partial`: stronger erratic altitude identity for chickadees.

#### Animals

Wolf:

- `Implemented`: howl and charge logic.
- `Partial`: dramatic readability is materially stronger.
- `TODO`: stronger mercy-based spare payoff.

Cat:

- `Implemented`: kindness reward and some spare-like behavior.
- `Partial`: optional reward-hazard readability is materially stronger.

Fox:

- `Implemented`: mirror-jump behavior.
- `Partial`: sly timing-game readability and spare path clarity are materially stronger.

Hedgehog:

- `Implemented`: non-lethal speed debuff.
- `Partial`: fair visibility is materially stronger.
- `TODO`: final fairness under petal-noise and real-device conditions.

Dog:

- `Implemented`: bark-projectile hazard and buddy variant.
- `Partial`: readability and memorability of both modes are materially stronger.

#### Biome Affinity Map

- `Conflict`: historical six-biome affinity map vs current five-biome cycle.
- `TODO`: final reconciled biome-to-entity affinity map.

#### Entity Class Architecture Note

- `Implemented`: no single generic obstacle class in practice.
- `Partial`: full component-based architecture ambition.

### F. Undertale Personality & Charm (`9d7d455:docs/UNDERTALE_VIBE.md`)

#### Visual Style / Chibi / Eye States

- `Implemented`: separate face layer baseline exists with multiple eye-state reactions.
- `TODO`: full listed eye-state coverage and stronger expressive presentation.
- `TODO`: max-16-color discipline and stronger retro-specific art constraints as policy.

#### Rest Quotes

- `TODO`: universal determination-like quotes.
- `TODO`: biome-specific quote pools.
- `TODO`: killer-specific quote pools.

#### In-Run Flavour Text

- `Partial`: floating flavor text exists.
- `TODO`: full popup catalog and stronger trigger coverage.

#### Mercy & Pacifist Systems

- `Implemented`: mercy hearts exist.
- `Partial`: near-miss classification and some mercy-linked reactions.
- `Implemented`: dedicated `MercySystem`, spare thresholds, and baseline friendship rewards now exist.
- `TODO`: exact score rewards, stronger heart/effect presentation, `PACIFIST` banner, and fuller route-like payoff.

#### Persistent Memory

- `Implemented`: encounter counters and costume unlock overlays exist in baseline form.
- `TODO`: deeper deja vu repeat-killer system and richer cross-run presentation.

#### Ghost Run — Spirit Of The Best

- `Implemented`: ghost recorder/player and persistence.
- `Partial`: endpoint behavior / motivational UX.
- `TODO`: overtake wave-off, sparkle vanish, and default clarity policy.

#### Undertale Dog — Buddy Mode

- `Implemented`: buddy mode variant in code.
- `Partial`: player-facing delight and noticeability.

#### Leitmotif System

- `Partial`: music state transitions and tempo scaling.
- `TODO`: exact E-G-A-C-B leitmotif treatment in all states.

#### Dialogue Bubbles

- `TODO`: dedicated bubble system and full animal-trigger line table.

#### New Kotlin Systems Summary

- `Implemented`: `FlavorTextManager`, `GhostRecorder`, `GhostPlayer`, `LeitmotifManager` in practical forms.
- `TODO`: `PersistentMemoryManager`
- `TODO`: `MercySystem`
- `TODO`: `PacifistTracker`
- `TODO`: `DialogueBubble`
- `TODO`: `CostumeOverlay`

#### Design Mantras

- `TODO`: enforce as actual implementation review criteria.

### G. Implementation Roadmap (`99bb40a:docs/IMPLEMENTATION_ROADMAP.md`)

The roadmap itself is a spec source. Every phase is intended scope unless later consciously removed.

#### Phase 0 to Phase 2

- `Implemented`: project skeleton, loop, input.

#### Phase 3 to Phase 7

- `Implemented`: player, parallax, HUD, sprites, entity foundation in broad form.
- `Partial`: final-feel player presentation and face system.

#### Phase 8 to Phase 11

- `Partial`: all flora/tree/bird/animal class families exist.
- `TODO`: many per-entity dream-spec subdetails remain incomplete.

#### Phase 12 to Phase 17

- `Implemented`: spawner, biomes, particles, flavor text, mercy foundation.
- `Partial`: camera polish and pacifist/friendship systems.

#### Phase 18 to Phase 23

- `Partial`: ghost and garden meta-loop exist.
- `TODO`: persistent memory, costumes, richer rest state, full garden sanctuary feel.

#### Phase 24 to Phase 27

- `TODO`: full background art pass.
- `TODO`: full polish pass.
- `TODO`: full performance audit.
- `TODO`: true release/publishing readiness.

### H. Official Spec (`b3d59d8:spec.md`)

This later doc adds or sharpens several requirements that were not always expressed the same way elsewhere.

#### Core Player Mechanics & Physics

- `Conflict`: spec says right side jump and left side duck, current input model differs.
- `Implemented`: variable jump, apex easing, duck hitbox reduction in spirit.
- `Partial`: exact Mario-abort and side-specific control split.

#### Morality / Storyline System

- `Partial`: mercy and stumble logic exist.
- `TODO`: organic invisible morality system as a fully coherent route layer.
- `TODO`: full “animals cease attacking after enough Mercy” world-state change.

#### Entity Kingdom

- `Partial`: airborne hazard families exist.
- `Partial`: botanical/biological hazards exist.
- `TODO`: stronger phone-readable delivery of all that logic.

#### Economy & Meta-Progression

- `Implemented`: seeds, Bloom, ghost, garden progression in broad form.
- `Partial`: exact intended dramatic economy loop.
- `TODO`: sanctuary rebuilding narrative frame.

#### Audio & Visual Impact

- `Partial`: parallax, particles, haptics, and music systems exist.
- `Conflict`: later official spec claims things like hardware shaders, 512-particle impacts, and “completely finalized, stable, production-ready,” which do not match present reality.
- `TODO`: only carry forward the actual product-desirable parts of this later doc, not its inaccurate completion claim.

## Cross-Document Requirement Families

These synthesize the entire historical spec set into unified workstreams.

### Product Fantasy

- `Implemented`: endless runner in a forest.
- `Partial`: cottagecore, breathing, inhabited forest feel.
- `TODO`: fully authored emotional product arc.

### Readability

- `Implemented`: a central `ReadabilityProfile` now owns spawn pacing plus core bird/animal sizing, telegraph, and mercy-padding baselines.
- `Partial`: many unique systems now read more clearly, but final flora/tree coverage and actual-device validation are still required.

### Bloom

- `Implemented`: meter, activation, and stronger spectacle baseline.
- `Conflict`: 8 vs 10 seeds, 5s vs 6s, exact effect bundle.
- `TODO`: continue tightening the full final Bloom presentation and document the resolved canonical spec everywhere.

### Biomes

- `Implemented`: biome progression exists.
- `Conflict`: five vs six biomes and naming variants.
- `TODO`: choose one canonical biome set and bring all docs and code into alignment.

### Personality / Mercy / Memory

- `Partial`: early foundation exists.
- `TODO`: full mercy, friendship, persistent memory, costumes, quote pools, dialogue bubbles, route-like charm.

### Ghost

- `Implemented`: ghost baseline.
- `TODO`: make it emotionally useful without ever confusing the player.

### Garden

- `Implemented`: unlock screen and persistence.
- `TODO`: sanctuary feel, richer visual integration, stronger meta emotional payoff.

### Audio

- `Partial`: state-based audio exists.
- `TODO`: full leitmotif-driven score with stronger biomes and Bloom identity.

### Art / FX

- `Partial`: sprites, sway, particles, tinting.
- `TODO`: final handcrafted background art, lighting, petals, fireflies, and power moments.

### Release Readiness

- `TODO`: product is not yet at the level promised by historical docs and user ambition.

### I. Approved Emotional Expansion Addendum (2026-03-15)

These items are not from the earliest historical repo docs. They are explicit user-approved expansion scope added after dream-spec restoration and should now be treated as authoritative TODO targets.

#### Forest Memory Layer

- `Implemented`: a world-tone system now classifies recent runs into gentle, reckless, fearful, and steady.
- `Partial`: world tone now affects Garden palette, sanctuary ambience, and rest/Garden framing.
- `TODO`: deepen ambience, creature warmth, and broader world-state response.

#### Named Relationship Arcs

- `Implemented`: formal relationship stages now exist for Cat, Fox, Wolf, Dog, Owl, and Eagle.
- `Partial`: relationship-driven dialogue now affects pass, threat, spare, and return tone, and Garden surfaces the strongest bond.
- `Partial`: bonded creatures now influence Garden return moments, visible Garden presence, and sanctuary traces in baseline form.
- `TODO`: deepen encounter shifts, milestone presentation, and broader relationship consequences.
- `TODO`: make visible warmth from repeated positive interactions and visible caution or tension from repeated negative ones more pronounced in play.

#### Personal Return Moments

- `Implemented`: first-run-of-day greetings, long-absence reactions, and rough-run comfort beats in baseline form.
- `Partial`: milestone-sensitive Garden return moments now exist with bonded visiting-creature presentation and stronger kindness/clean-play hooks.
- `TODO`: broaden emotional-state combinations and deepen authored coverage further.

#### Quiet Story Fragments

- `Implemented`: short rest quotes now run through a first-class fragment system.
- `Partial`: rare Garden reflections, weather-linked Garden thoughts, bonded creature thoughts, and unlockable poetic memory pages now exist in baseline form.
- `TODO`: broaden creature thoughts and weather-linked lines beyond the current Garden-focused baseline.
- `TODO`: preserve emotional mystery through fragment writing instead of explicit exposition.

## Strict Documentation Conclusion

This repo now contains:

- restored dream-spec source docs
- a TODO matrix
- this source-traceability file

What still remains as documentation work:

- `TODO`: finalize canonical resolutions for historical conflicts and update every doc to a single non-conflicting product truth once those calls are made.
