# Forest_Run — Game Design Document (Restored)

This document restores the original long-form GDD as the product target. Implementation truth is tracked alongside it in [docs/TODO_MATRIX.md](/home/anurag-basistha/Projects/TODO/Forest_Run/docs/TODO_MATRIX.md).

When this restored source document conflicts with current canon, current canon wins: `5` biomes, `8` seeds / `6s` Bloom, gesture-anywhere jump/hold/duck input, and `run -> rest -> Garden` flow. Older exact variants remain useful for mood and reference, not as active blockers.

**Project Name:** Forest_Run  
**Platform:** Android (Android Studio)  
**Language:** Kotlin  
**Package Name:** `com.yourname.forest_run`  
**Screen Orientation:** Landscape / `sensorLandscape`  
**Target FPS:** 60 FPS  
**Vision Status:** Active target, not abandoned

---

## 1. Vision & Concept

Forest_Run is a high-fidelity, colorful 2D endless runner for Android. It is a spiritual successor to the Chrome Dino game, but reimagined as a lush, living cottagecore forest experience. The aesthetic is inspired by Studio Ghibli and Stardew Valley. Every action should have a natural physical reaction, and the forest should feel like it is breathing.

### Core Design Pillars

| Pillar | Description |
|---|---|
| Juicy | Every input triggers satisfying visual, haptic, and audio feedback |
| Alive | The world breathes: wind, petals, fireflies, day/night shift |
| Readable | Each obstacle has a unique visual identity so the player learns fast |
| Rewarding | Seeds -> Garden meta-loop gives every run long-term meaning |
| Behavioural AI | Animals react to the player; the forest feels inhabited |

### Current Status

- Implemented: baseline endless runner, biomes, seeds, Bloom, some entity personality, garden persistence.
- Partial: bird-family ordinary-play warnings and payoffs are now materially more explicit instead of relying on generic pass text alone.
- Partial: tree-family ordinary-play staging is now materially stronger, with clearer curtain, canopy, precision-gap, and gust reads.
- TODO: deliver the full intended feeling on device. Current user feedback says the world is too small, too sparse, and too unclear.

---

## 2. Art Style & Visual Identity

- Art Style: high-saturation vibrant pixel art, Ghibli x Stardew Valley.
- Colour Palette: deep forest greens, floral pastels, earthy browns, warm gold, deep violet.
- No static frames. All flora sways; all particles drift. The forest is never still.

### Colour Dynamics — Day/Night Cycle

| Phase | Distance | Canvas Tint |
|---|---|---|
| Morning | 0–300m | Soft Peach / Warm Pink |
| Day | 300–700m | Clear Blue-White |
| Golden Hour | 700–1100m | Warm Gold / Amber |
| Twilight | 1100–1500m | Deep Orange / Red-Orange |
| Night | 1500m+ | Deep Violet / Indigo |

Night mode activates Lily of the Valley glow and Owl spawning. Fireflies appear once dusk begins.

### Current Status

- Implemented: biome tinting and darkness overlays, sprite import pipeline.
- Partial: run-time parallax now carries stronger biome wash, canopy shade, mist, drifting leaves/petals/fireflies, and subtle speed/Bloom scale response in addition to the sanctuary/menu/rest ambience pass.
- TODO: full hand-authored scenic backgrounds, stronger atmospheric density, and true readable phone-scale art staging on actual phones.

---

## 3. Game Flow — Full Session Lifecycle

### 3.1 Cold Start — The Garden

- Open app to see the woman sitting under a Weeping Willow in her personal garden.
- No hard menu buttons.
- Garden shows unlocked plants in the background.
- Ambient forest audio plays.
- First tap: woman stands up.
- Second tap: she begins her run.
- A rhythmic acoustic beat fades in, synced to her footstep cadence.

### 3.2 Acceleration Phase — Early Game (0–500m)

- Biome: Home Grove.
- Obstacles: simple early hazards.
- Scroll speed begins gentle and readable.
- Seeds begin spawning.
- Music starts minimal.

### 3.3 State Shift — Mid Game (500m–1500m)

- Every 500m, a biome transition event fires.
- Background and dominant colors shift.
- Spawn pools change.
- Speed rises.
- Music layers deepen.
- Foxes, Ducks, and more complex threats are introduced.

### 3.4 Bloom State — Power-Up Phase

- Triggered when the Bloom meter fills.
- Full invincibility for a limited window.
- Petal trail, audiovisual surge, world transformation.
- Obstacles passed should convert into bonus reward.

### 3.5 Chaos Peak — Late Game

- Dense overlapping threat patterns.
- Maximum wind and atmosphere.
- Strong milestone feedback.
- Fully layered music.

### 3.6 The Soft Fall — Game Over / Rest

- On collision, the character should not feel cheaply “deleted.”
- She stumbles, slows, and sits down tired but peaceful.
- The forest continues gently.
- A calm end-run summary appears.
- The emotional flow should support returning to the garden and trying again.

### 3.7 Meta-Loop — The Garden

- Seeds collected during the run are used to grow new plants.
- The garden becomes a personalized forest over many sessions.
- The next locked plant is always visible as motivation.

### 3.8 Emotional Retention Layer

This game should keep players returning because the forest feels personal, not because numbers merely go up. The world should seem to recognize tone, remember patterns, and slowly become emotionally closer to the player.

#### 3.8.1 Forest Memory Layer

- The world should remember how the player has been moving through it, not only totals.
- Gentle, patient recent play should make the Garden feel calmer and more welcoming.
- Repeated panic, repeated collisions, or repeated harm from the same creature should create emotional consequences in presentation.
- Repeated kindness should produce visible trust signs, calmer ambience, and a stronger sense of sanctuary.
- The player should feel that the forest has formed a soft opinion about them.

Current baseline:

- recent run tone now affects Garden ambience and carry-home framing
- repeated harm from the same creature now surfaces as caution in sanctuary traces, `Same Shadow` repeat-killer beats, Garden return moments, Garden reflections, and tender memory streaks
- repeated kindness now surfaces through trust-path traces, repeat-friend `Shared Path` traces, warmer carry-home lines, rest fragments, and dedicated gentle-return beats
- broader creature warmth and richer world-state consequence are still TODO, even though sanctuary/homecoming atmosphere is now materially stronger

#### 3.8.2 Named Relationship Arcs

- Cat, Fox, Wolf, Dog, Owl, and Eagle should evolve beyond obstacle classes into remembered relationships.
- Each major creature should have first-impression, recognition, trust, and milestone states.
- Relationship stages should influence dialogue, encounter behavior, Garden presence, cosmetics, and post-run reflection.
- Positive repetition should create warmth and familiarity.
- Negative repetition should create caution, fear, disappointment, or tension where appropriate.

Current baseline:

- major-creature relationship stages now exist in runtime and persist across sessions
- stage-aware lines now affect pass, threat, spare, and return tone for Cat, Fox, Wolf, Dog, Owl, and Eagle
- ordinary encounter beats now also swap Cat near-miss, Fox landing, Wolf charge, and Dog buddy dialogue based on relationship history instead of generic fallback cues
- Garden now surfaces the strongest current bond and a baseline bond reward
- milestone bonds now leave named keepsake traces in the sanctuary, with matching costume paths for Cat, Fox, and Wolf
- deeper Garden presence and broader behavior shifts are still TODO

#### 3.8.3 Personal Return Moments

- The game should notice when the player comes back.
- The first run of the day should be able to greet the player in a special way.
- After several failed runs, the Garden should sometimes respond with comfort.
- After a long absence, the forest should react like it noticed the player was gone.
- Personal milestones should occasionally cause a favorite creature or trusted presence to appear in the Garden.
- Some dialogue should exist only after specific emotional states such as many deaths, many mercies, long streaks, or a first major Bloom.

Current baseline:

- the Garden can now answer long absences, rough streaks, stronger bonds, Bloom-heavy runs, repeated kindness, repeat-friend familiarity, repeat-killer history, kind/merciful/peaceful routes, and gentle milestone returns with distinct return moments
- milestone bonds now produce warmer and more specific return lines instead of only generic greetings
- rest can now preview the likely homeward tone before the Garden transition without consuming the saved return beat
- peaceful and merciful runs can now surface distinct homeward return beats instead of only borrowing generic gentle-return lines
- broader authored combinations are still TODO, even though startup/rest/Garden continuity now shares stronger carry-home and homecoming atmosphere

#### 3.8.4 Quiet Story Fragments

- The game should prefer small poetic fragments over heavy exposition.
- Rest quotes should stay short and emotionally suggestive.
- Creature thoughts should appear in small one-line moments.
- Weather-linked thoughts and rare Garden reflections should reveal feeling without lore-dumping.
- Unlockable memory pages should be poetic, fragmentary, and intimate rather than encyclopedic.

Current baseline:

- rest quotes now route through a fragment system instead of only hard-coded quote branches
- Garden can now surface baseline reflection fragments, weather-linked thoughts, bonded creature thoughts, repeated-harm caution reflections, repeated-kindness warmth reflections, repeat-friend familiarity reflections, repeat-killer `Same Shadow` reflections, milestone-gentleness reflections, Bloom-afterglow reflections, kind/peaceful-route reflections, and track unlocked memory pages
- broader creature-thought coverage and richer weather-linked fragments are still TODO

### Current Status

- Implemented: two-tap menu start, run loop, game over, restart, garden unlock persistence.
- Partial: menu startup and rest recovery now share authored copy derived from the same saved emotional state, so the session begins and ends with clearer continuity.
- Partial: mercy-oriented runs now also travel home as explicit route tiers instead of only appearing as near-miss or spare counters.
- Partial: the remaining animal-family ordinary-play payoffs are stronger, with clearer low-threat fairness and more visible bond reward feedback.
- PARTIAL: the session arc now shares startup/rest/Garden copy and sanctuary-derived homecoming atmosphere; finish the full authored pacing, launch feel, and restorative coherence in real play.

---

## 4. Core Input System

| Input | Action |
|---|---|
| Single Tap | Standard jump |
| Hold / Long Press | High jump proportional to hold duration |
| Swipe Down | Duck / Slide |
| Multi-touch supported | Jump and duck independently |

Jump height is tied to touch duration. Short tap = low hop. Full hold = maximum arc.

### Current Status

- Implemented: tap, hold, swipe-down.
- Partial: ghost playback now delays reveal and suppresses briefly after impacts so the live runner stays clearer during crowded moments.
- TODO: validate all touch affordances on device across long sessions and finish eliminating any confusion caused by ghost overlap or visual clutter.

---

## 5. Scoring System

| Event | Score |
|---|---|
| Distance | +1 point per metre |
| Seed collected | +10 points |
| Cat Kindness Bonus | multiplier reward |
| Close Call | score bonus + spectacle |
| 1000-point milestone | feedback reward |

### Nature's Grace — Close Call Multiplier

- Narrow animal clears should trigger a vibrant border flash.
- Score reward and spectacle should reinforce close-call mastery.

### Current Status

- Implemented: score, distance, milestones, mercy hearts.
- TODO: stronger close-call payoff, more obvious score logic, and better player understanding of kindness / mercy bonuses.

---

## 6. Collectibles — Seeds

- Seeds are vibrant glowing orbs scattered throughout the run.
- Some are placed as tempting traps above hazards.
- Seeds can also drop from interactions and Bloom sequences.
- Seeds feed two systems:
  1. In-run Bloom progression
  2. Meta-game garden currency

### Current Status

- Implemented: seed orbs, Bloom fill, lifetime seed persistence, and stronger Bloom world-shift feedback.
- Implemented: several flora now stage tempting seed and timing reads more explicitly in ordinary play.
- PARTIAL: player-facing Bloom readability is materially stronger through HUD/presentation updates; final proof still depends on real-device validation.

---

## 7. Haptic Feedback

| Event | Haptic |
|---|---|
| Jump | Short pulse |
| Bloom activates | Long sustained pulse |
| Collision / game over | Long strong pulse |
| Close Call | Double-tap pulse |
| 1000-point milestone | Medium pulse |

### Current Status

- Implemented: core haptic hooks.
- TODO: perceptual tuning on actual phones.

---

## 8. Save System

- Local high score stored in SharedPreferences.
- Garden progress stored locally.
- Lifetime seeds persist.
- No cloud sync in v1.0 target.

### Current Status

- Implemented: high score, lifetime seeds, garden progress, ghost save.
- TODO: persistent memory systems beyond that, such as repeated-encounter memory and costumes.
- TODO: expand persistence into emotional memory, creature relationship states, sanctuary traces, and failure-context carryover.

---

## 9. Audio Design

### Music — Adaptive / Procedural

| Game State | Audio |
|---|---|
| Garden / Menu | Soft ambient acoustic |
| Running Early | Simple drum beat |
| Running Mid | Bass + flute layer |
| Running Late | Full layered track |
| Bloom State | Orchestral triumphant swell |
| Rest | Calm slow outro |

Dynamic tempo should scale with scroll speed.

### SFX

| Event | Sound |
|---|---|
| Seed collected | Soft ping |
| Jump | Whoosh |
| Landing | Soft thud + grass rustle |
| Dog telegraph | Bark cue |
| Eagle telegraph | Screech cue |
| Wolf telegraph | Howl cue |
| Bloom start | Magical chime |
| Rest / game over | Gentle exhale / settle |

### Current Status

- Implemented: core music states and many SFX hooks.
- Partial: music states now also resolve through explicit state-shaped tempo/volume profiles so menu/rest stay softer, late run grows fuller, and Bloom peaks harder.
- TODO: full leitmotif treatment and stronger authored musical identity across all states.
- TODO: use music and ambience to reinforce comfort, trust, grief, and return-to-sanctuary feeling.

---

## 10. Unlockables & Progression

| Item | Cost (Seeds) | Description |
|---|---|---|
| Cactus | 5 | First unlock |
| Lily of the Valley | 10 | Night glow |
| Hyacinth | 15 | Cluster variant |
| Eucalyptus | 20 | Tall sway |
| Vanilla Orchid | 25 | Low hanging vine |
| Cherry Blossom Tree | 40 | Petal particles |
| Weeping Willow | 50 | Curtain leaves |
| Jacaranda | 60 | Purple canopy |
| Bamboo | 30 | Dense stalks |

Animals and birds were not intended as v1.0 garden unlockables.

### Current Status

- Implemented: left-to-right plant card unlock flow with lifetime seeds.
- PARTIAL: the garden now feels more sanctuary-like through visible traces, mood ambience, homecoming badges, mist, lantern glow, and stronger carry-home lighting; finish the full long-term restorative scene.
- TODO: make the world emotionally remember the player through tone, return moments, relationship traces, and quiet poetic fragments.
