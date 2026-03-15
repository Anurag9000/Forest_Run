# Forest_Run — Game Design Document (Restored)

This document restores the original long-form GDD as the product target. Implementation truth is tracked alongside it in [docs/TODO_MATRIX.md](/home/anurag-basistha/Projects/TODO/Forest_Run/docs/TODO_MATRIX.md).

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
- TODO: full hand-authored scenic backgrounds, stronger atmospheric density, true readable phone-scale art staging.

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

### Current Status

- Implemented: two-tap menu start, run loop, game over, restart, garden unlock persistence.
- TODO: make the whole session arc feel authored, readable, and emotionally coherent in real play.

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
- TODO: validate all touch affordances on device across long sessions and eliminate any confusion caused by ghost overlap or visual clutter.

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

- Implemented: seed orbs, Bloom fill, lifetime seed persistence.
- TODO: fix player-facing visibility. Current user feedback says seeds and Bloom are not reading clearly enough in practice.

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
- TODO: full leitmotif treatment and stronger authored musical identity across all states.

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
- TODO: make the garden feel like a true sanctuary and long-term reward rather than a minimal shop-like screen.
