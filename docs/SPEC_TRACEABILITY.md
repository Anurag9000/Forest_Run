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
- `Canonized / Not Required`: preserved for source traceability, but intentionally not an active product blocker under current runtime canon

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

These conflicts are preserved intentionally for traceability. Current runtime canon resolves them by default unless a future product decision explicitly reopens one.

## Canonical Resolution Policy

The repo now resolves historical conflicts this way by default:

- current runtime canon wins over older conflicting variants
- exact historical filenames, frame counts, score tables, and save-shape expectations are traceability-only unless they materially improve the shipped game
- broader product-desirable goals stay active: device readability, emotional payoff depth, sanctuary feel, bespoke scenic art, authored score, and release hardening
- old variants are not reopened silently through documentation drift

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
- `Canonized / Not Required`: explicit day-phase runtime restoration is not an active blocker under the current five-biome canon.
- `Partial`: owl/night-specific behavior exists.
- `TODO`: firefly ambience at the full intended density.

#### Cold Start — Garden Main Menu

- `Implemented`: seated/resting menu scene and two-tap stand/run flow.
- `Partial`: ambient garden tone and startup atmosphere now reflect the carried-home emotional state through shared session-arc copy plus sanctuary-derived homecoming badges and ambience.
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
- `Implemented`: Bloom meter, activation, invincibility window, stronger activation spectacle, and stronger screen/world treatment.
- `Partial`: glow, aura, player-following Bloom effects, and the broader transformed-world feel.
- `Canonized / Not Required`: speed doubling during Bloom is not part of the current runtime canon.
- `Partial`: passed obstacles now convert into stronger reward bursts, world bursts, and environmental reactions, but still need final device-proof spectacle.
- `Canonized / Not Required`: the exact continuous old flower-opening behavior is not required so long as Bloom keeps a strong nearby-world reaction.
- `Partial`: music transition into Bloom; stronger final authored audio identity still remains.

#### Chaos Peak — Late Game

- `Partial`: late-game higher density and speed.
- `Partial`: Eagles and Wolves both exist.
- `Implemented`: milestone camera feedback.
- `Partial`: stronger high-wind climax feeling.
- `Partial`: layered fast music.

#### Soft Fall / Rest State

- `Partial`: current runtime restores the return-to-Garden loop, and rest now includes authored recovery/carry-home copy, sanctuary-derived recovery atmosphere, repeat-killer and route-sensitive homecoming beats, and a non-mutating preview of the likely Garden return tone; the full calm reflective sit-down framing is still weaker than the original dream spec.
- `Partial`: mercy-oriented runs now also surface explicit route tiers in the carry-home layer, but the full restorative authored recovery is still incomplete.
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
- `Partial`: mercy-oriented play now also classifies explicit run-level route tiers for carry-home presentation.
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
- `Canonized / Not Required`: a JSON-rich save rewrite is not required if current persistence supports shipped product needs.
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
- `Canonized / Not Required`: exact historical filenames and frame counts are not active product blockers.

Ground flora asset family:

- `Partial`: cactus, lily, hyacinth, eucalyptus, vanilla assets exist in practical form.
- `Canonized / Not Required`: exact overlay and naming parity is not required if runtime readability and mood land.

Tree asset family:

- `Partial`: willow, jacaranda, bamboo, cherry blossom assets exist.
- `Canonized / Not Required`: exact historical segmentation is not required.

Bird asset family:

- `Partial`: owl, duck, eagle, tit, chickadee assets exist.
- `Canonized / Not Required`: exact historical frame-set parity is not required.

Animal asset family:

- `Partial`: wolf, cat, fox, hedgehog, dog assets exist.
- `Canonized / Not Required`: exact historical sheet coverage is not required.

Particles & FX assets:

- `Partial`: jump dust, seed, sparkles, etc. exist in part.
- `Canonized / Not Required`: exact historical particle asset naming is not required.

UI assets:

- `Canonized / Not Required`: exact historical UI asset naming is not required; stronger bespoke HUD art remains product-desirable.

Background assets:

- `TODO`: full bespoke background bitmap suite from old setup guide.

#### Audio File Inventory

- `Partial`: several music and SFX files exist in `res/raw`.
- `Canonized / Not Required`: exact historical file inventory and naming set are not blockers.

#### Theme & Styles

- `Implemented`: no-action-bar / fullscreen gameplay presentation.

#### Loading Assets in Code

- `Implemented`: runtime asset loading helpers/managers exist.

#### Performance Checklist

- `Partial`: delta-time movement, reuse of core objects, some pooling.
- `Partial`: modern deterministic suite and device checklist now cover the active runtime truth.
- `TODO`: finish real-device validation and performance hardening against the modern checklist.

#### Debugging Physics Checklist

- `Partial`: many of the listed mechanics exist.
- `Canonized / Not Required`: the entire historical checklist does not need to survive line-for-line as long as modern deterministic and device gates cover the shipped game.

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
- `Partial`: relationship milestones, return-state data, run summaries, and fragment unlocks now persist; broader repeated-history presentation state is still incomplete.

#### Dynamic Difficulty Curve

- `Implemented`: base speed / spawn interval ramping.
- `Partial`: exact original formulas.

### D. Visual & FX Spec (`947e4e1:docs/VISUAL_FX_SPEC.md`)

#### Parallax Scrolling

- `Implemented`: parallax background system.
- `Partial`: four distinct layers in dream-spec scenic richness.
- `Partial`: biome wash, canopy shade, mist bands, drifting leaves/petals/fireflies, horizon glow, and subtle speed/Bloom world-scale response now exist in the runtime parallax layer.
- `TODO`: full bespoke art, blurred foreground identity, and final phone-proof scenic tuning.

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
- `Canonized / Not Required`: exact historical phase-table restoration and shader-like transitions are not active blockers.

#### Camera Effects

- `Implemented`: screen shake.
- `Partial`: speed-based zoom/pullback.

#### Environmental Bloom Reaction

- `Partial`: nearby flora/tree reactions now exist on Bloom conversions, now backed by stronger world-burst feedback.
- `Canonized / Not Required`: exact continuous pass-by flower opening is not required if the broader Bloom-world reaction lands.

#### Spirit Of The Forest Bloom Visuals

- `Implemented`: aura, activation burst, world-tint transformation, HUD pulse paneling, stronger screen treatment, and larger Bloom conversion spectacle now exist.
- `TODO`: final obstacle-dissolve polish, phone-proof tuning, and any remaining top-end power-fantasy treatment.

#### Ghost Readability

- `Partial`: ghost playback now delays its reveal at run start and suppresses briefly after impacts so the live runner stays visually dominant.
- `TODO`: final on-device threshold tuning and acceptance validation.

### E. Entity Database (`947e4e1:docs/ENTITY_DATABASE.md`)

#### Ground Flora

Lily of the Valley:

- `Implemented`: low hazard and sway.
- `Partial`: night glow, lure line, and lower seed-trap staging now read more clearly in runtime.
- `TODO`: final device-proofing of the lure/trap read.

Hyacinth:

- `Implemented`: brush / mercy zone logic.
- `Partial`: clustered pulse staging now makes the encounter read more like one timing unit.
- `TODO`: stronger long-jump rhythm identity and device-proofing.

Eucalyptus:

- `Partial`: obstacle, sway, and layered gust guides now exist.
- `TODO`: final strong-leaner / high-threat tuning on device.

Vanilla Orchid:

- `Partial`: two colliders, explicit top/bottom hazard staging, and a clearer safe thread now exist.
- `TODO`: final live-play clarity and device-proofing.

Cactus:

- `Implemented`: classic rigid hazard.
- `Partial`: stronger warning staging, history-aware payoff text, and clearer reward feedback now exist.
- `TODO`: broader memory/cosmetic integration and device-proofing.

#### Trees

Weeping Willow:

- `Partial`: presence, hazard role, and stronger curtain-lane / shadow-zone staging now exist.
- `TODO`: final scenic dominance and device-proofing.

Jacaranda:

- `Partial`: tree presence, clearer petal curtain, fuller canopy halo, and stronger pass reward now exist.
- `TODO`: final canopy spectacle tuning and device-proofing.

Bamboo:

- `Partial`: obstacle presence, clearer gap-threading guidance, and stronger precision payoff now exist.
- `TODO`: final device-proofing of the precision read.

Cherry Blossom:

- `Partial`: tree presence, layered gust staging, and stronger petal-storm feel now exist.
- `TODO`: final gust-pressure tuning and device-proofing.

#### Birds

Owl:

- `Implemented`: reactive dive on jump-nearby logic.
- `Partial`: sleeping/perched/noise-punish mood, clearer dive telegraphing, alert ring, stronger amber eye-glow, and relationship-aware alert cueing now exist.
- `TODO`: deeper night drama payoff and device-proofing.

Duck:

- `Implemented`: low-flying hazard, clearer duck-lane teaching role, and explicit down-warning / duck-through payoff.
- `TODO`: final on-device cue polish and stronger quack payoff.

Eagle:

- `Implemented`: dive hazard.
- `Partial`: lock-on cue, target-zone read, relationship-aware mark cueing, and clean-pass reward payoff are materially stronger.
- `TODO`: stronger dramatic cue understanding and device-proofing.

Tit / Chickadee:

- `Implemented`: grouped bird classes.
- `Partial`: stronger sine-wave rhythm identity and clearer warning/payoff staging for tits.
- `Partial`: stronger erratic altitude identity and flutter-path staging for chickadees.

#### Animals

Wolf:

- `Implemented`: howl and charge logic.
- `Partial`: dramatic readability and relationship-aware charge cueing are materially stronger.
- `TODO`: stronger mercy-based spare payoff.

Cat:

- `Implemented`: kindness reward and some spare-like behavior.
- `Partial`: optional reward-hazard readability and relationship-aware near-miss cueing are materially stronger.

Fox:

- `Implemented`: mirror-jump behavior.
- `Partial`: sly timing-game readability, history-aware landing payoff, and spare path clarity are materially stronger.

Hedgehog:

- `Implemented`: non-lethal speed debuff.
- `Partial`: fair visibility, warning-stage messaging, and debuff feedback are materially stronger.
- `TODO`: final fairness under petal-noise and real-device conditions.

Dog:

- `Implemented`: bark-projectile hazard and buddy variant.
- `Partial`: readability and memorability of both modes are materially stronger, with relationship-aware buddy dialogue, longer bonded buddy runs, and more visible celebratory buddy exits.

#### Biome Affinity Map

- `Conflict`: historical six-biome affinity map vs current five-biome cycle.
- `Partial`: current runtime uses the five-biome cycle consistently.
- `TODO`: keep entity affinity docs aligned to the current five-biome runtime instead of restoring the old six-biome map.

#### Entity Class Architecture Note

- `Implemented`: no single generic obstacle class in practice.
- `Partial`: full component-based architecture ambition.

### F. Undertale Personality & Charm (`9d7d455:docs/UNDERTALE_VIBE.md`)

#### Visual Style / Chibi / Eye States

- `Implemented`: separate face layer baseline exists with multiple eye-state reactions.
- `TODO`: full listed eye-state coverage and stronger expressive presentation.
- `TODO`: max-16-color discipline and stronger retro-specific art constraints as policy.

#### Rest Quotes

- `Partial`: quote delivery and fragment-driven selection exist.
- `TODO`: broaden universal, biome-specific, and killer-specific quote pools where they still improve the shipped game.

#### In-Run Flavour Text

- `Partial`: floating flavor text exists.
- `Partial`: popup coverage is broader now through authored hit/stumble/milestone runtime cues and repeat-killer in-run messaging.
- `TODO`: full popup catalog and stronger trigger coverage.

#### Mercy & Pacifist Systems

- `Implemented`: mercy hearts exist.
- `Partial`: near-miss classification and some mercy-linked reactions.
- `Implemented`: dedicated `MercySystem`, spare thresholds, and baseline friendship rewards now exist.
- `Canonized / Not Required`: exact historical score rewards and a mandatory `PACIFIST` banner are not required.
- `Partial`: in-run mercy-miss text and route-reward messaging are materially clearer through first-class route presentation instead of generic reward strings alone.
- `TODO`: stronger heart/effect presentation and fuller route-like payoff.

#### Persistent Memory

- `Implemented`: encounter counters and costume unlock overlays exist in baseline form.
- `Partial`: repeat-killer deja vu now also surfaces in-run through collision flavor, while richer cross-run presentation still remains open.

#### Ghost Run — Spirit Of The Best

- `Implemented`: ghost recorder/player and persistence.
- `Partial`: endpoint behavior / motivational UX, plus delayed reveal and post-impact suppression for cleaner readability.
- `TODO`: overtake wave-off, sparkle vanish, and default clarity policy.

#### Undertale Dog — Buddy Mode

- `Implemented`: buddy mode variant in code.
- `Partial`: player-facing delight and noticeability.

#### Leitmotif System

- `Partial`: music state transitions now also resolve through explicit state-shaped tempo/volume profiles for menu, run layers, Bloom, and rest.
- `Canonized / Not Required`: exact historical note-for-note motif treatment is not required.
- `TODO`: fuller authored leitmotif coverage and stronger state identity.

#### Dialogue Bubbles

- `Implemented`: dedicated dialogue bubble system exists.
- `Partial`: trigger coverage is stronger through new collision and milestone runtime cues, but line tables and breadth still remain open.

#### New Kotlin Systems Summary

- `Implemented`: `FlavorTextManager`, `GhostRecorder`, `GhostPlayer`, `LeitmotifManager` in practical forms.
- `Implemented`: `PersistentMemoryManager`
- `Implemented`: `MercySystem`
- `Implemented`: `PacifistTracker`
- `Implemented`: `DialogueBubbleManager`
- `Implemented`: `CostumeOverlay`

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

- `Implemented`: a central `ReadabilityProfile` now owns spawn pacing plus flora, tree, bird, and animal sizing/staging/mercy-padding baselines.
- `Partial`: many unique systems now read more clearly, and repeated-history / route carry-home is richer, but actual-device validation and final retuning are still required.

### Bloom

- `Implemented`: meter, activation, stronger world-transform baseline, and stronger conversion spectacle.
- `Conflict`: 8 vs 10 seeds, 5s vs 6s, exact effect bundle.
- `Implemented`: runtime canon is resolved to `8` seeds and `6s`.
- `TODO`: continue tightening the last Bloom presentation polish on device and keep docs synchronized to the resolved canon.

### Biomes

- `Implemented`: biome progression exists.
- `Conflict`: five vs six biomes and naming variants.
- `Implemented`: runtime canon is resolved to the current five-biome set.
- `TODO`: keep docs and entity-affinity notes synchronized to that resolved canon.

### Personality / Mercy / Memory

- `Partial`: early foundation exists.
- `TODO`: deepen mercy, friendship, persistent memory, costumes, quote pools, dialogue bubble coverage, and route-like charm beyond the stronger current in-run route presentation baseline.

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
- `Partial`: repeated harm from the same creature now shows up in sanctuary planning, strained-bond `Held At A Distance` return/carry-home beats, `Same Shadow` return/reflection beats, Garden caution moments, Garden reflection fragments, and save-backed tender streak state.
- `Partial`: repeated kindness now also shows up through trust-path sanctuary traces, repeat-friend `Shared Path` traces, warmer rest/Garden fragments, and `Stayed Gentle` / `Kept Finding You` return moments, while route-aware fragments now cover kind as well as merciful/peaceful runs.
- `TODO`: deepen ambience, creature warmth, and broader world-state response.

#### Named Relationship Arcs

- `Implemented`: formal relationship stages now exist for Cat, Fox, Wolf, Dog, Owl, and Eagle.
- `Partial`: relationship-driven dialogue now affects pass, threat, spare, and return tone, and relationship stage now influences encounter generosity and telegraph tuning.
- `Partial`: bonded creatures now influence Garden return moments, visible Garden presence, sanctuary traces, named milestone keepsake rewards, featured home-presence/carry-home wording, and fallback bonded Garden reactions in baseline form.
- `Partial`: Cat, Fox, Wolf, Dog, Owl, and Eagle milestone bonds can now unlock matching costume paths alongside their keepsake rewards, featured sanctuary home-presence surfacing, and bond-specific milestone reaction cues.
- `TODO`: deepen broader milestone presentation and relationship consequences beyond the current keepsake, costume, and home-presence baseline.
- `Partial`: visible warmth from repeated positive interactions and visible caution or tension from repeated negative ones are now more pronounced in play through stronger warm/strained cue swaps, sanctuary traces, and return/fragment payoff; broader coverage still remains open.

#### Personal Return Moments

- `Implemented`: first-run-of-day greetings, long-absence reactions, and rough-run comfort beats in baseline form.
- `Partial`: milestone-sensitive Garden return moments now exist with bonded visiting-creature presentation, stronger kindness/clean-play hooks, explicit repeated-kindness and repeat-friend returns, long-absence repeat-friend warmth, merciful repeat-friend recognition, richer absence-sensitive lines, peaceful-Bloom hush, and Bloom-heavy afterglow returns.
- `TODO`: broaden emotional-state combinations and deepen authored coverage further.

#### Quiet Story Fragments

- `Implemented`: short rest quotes now run through a first-class fragment system.
- `Partial`: rare Garden reflections, weather-linked Garden thoughts, bonded creature thoughts, repeated-harm caution reflections, strained-bond reflections, repeated-kindness warmth reflections, repeated-kindness clean-return reflections, repeat-friend familiarity reflections, merciful repeat-friend reflections, milestone-gentleness reflections, peaceful-Bloom reflections, Bloom-afterglow reflections, and unlockable poetic memory pages now exist in baseline form.
- `TODO`: broaden creature thoughts and weather-linked lines beyond the current Garden-focused baseline.
- `TODO`: preserve emotional mystery through fragment writing instead of explicit exposition.

## Strict Documentation Conclusion

This repo now contains:

- restored dream-spec source docs
- a TODO matrix
- this source-traceability file

What still remains as documentation work:

- `DONE`: canonical resolution policy is now explicit: current runtime truth wins, while exact historical variants remain traceability-only unless re-promoted.
- `TODO`: keep every touched doc synchronized with the resolved canon and active product gaps.
