# Forest_Run — Game Design Document (GDD)

**Project Name:** Forest_Run  
**Platform:** Android (Android Studio)  
**Language:** Kotlin  
**Package Name:** `com.yourname.forest_run`  
**Package Name:** `com.yourname.forest_run`  
**Screen Orientation:** Landscape / `sensorLandscape`  
**Target FPS:** 60 FPS (frame-independent, supporting 60Hz and 120Hz displays)  
**Version:** 1.0 — Design Freeze

---

## 1. Vision & Concept

Forest_Run is a **high-fidelity, colorful 2D endless runner** for Android. It is a spiritual successor to the Chrome Dino game, but reimagined as a lush, living **"Cottagecore" forest experience**. The aesthetic is inspired by **Studio Ghibli** and **Stardew Valley** — every action has a natural physical reaction, and the forest feels like it is breathing.

### Core Design Pillars

| Pillar | Description |
|---|---|
| **Juicy** | Every input triggers satisfying visual + haptic + audio feedback |
| **Alive** | The world breathes — wind, petals, fireflies, day/night shift |
| **Readable** | Each obstacle has a unique visual identity so the player learns fast |
| **Rewarding** | Seeds → Garden meta-loop gives every run long-term meaning |
| **Behavioural AI** | Animals react to the player; the forest feels inhabited |

---

## 2. Art Style & Visual Identity

- **Art Style:** High-saturation vibrant **pixel art**, think Ghibli × Stardew Valley.
- **Colour Palette:** Deep forest greens, floral pastels (pinks, purples, teals), earthy browns, warm gold (Golden Hour), deep violet (Night/Twilight).
- **No static frames.** All flora sways; all particles drift. The forest is never still.

### Colour Dynamics — Day/Night Cycle

The Canvas `ColorFilter` shifts gradually based on score/distance:

| Phase | Distance | Canvas Tint |
|---|---|---|
| Morning | 0–300m | Soft Peach / Warm Pink |
| Day | 300–700m | Clear Blue-White (neutral) |
| Golden Hour | 700–1100m | Warm Gold / Amber |
| Twilight | 1100–1500m | Deep Orange / Red-Orange |
| Night | 1500m+ | Deep Violet / Indigo |

Night mode activates the **Lily of the Valley glow** and enables **Owl spawning**.  
Fireflies appear as ambient particles once dusk begins.

---

## 3. Game Flow — Full Session Lifecycle

### 3.1 Cold Start — The Garden (Main Menu)

- The player opens the app and sees the **woman sitting under a Weeping Willow** in her personal garden.
- **No hard menu buttons.** The garden shows all unlocked plants in the background.
- Ambient forest sounds play: birds chirping, wind through leaves.
- **First tap:** Woman stands up and enters a "Ready" pose.
- **Second tap:** She begins her run. The main game loop initiates.
- A low-fi rhythmic acoustic beat fades in, **synced to her footstep cadence**.

### 3.2 Acceleration Phase — Early Game (0–500m)

- **Biome:** The Home Grove (soft greens, simple terrain).
- **Obstacles:** Only Cactus and Cats appear.
- Scroll speed starts at ~10 pixels/frame.
- Music: simple drum pattern only.
- Seeds begin spawning. Each collected flies into the **Bloom Meter** with a `ping` sound.

### 3.3 State Shift — Mid Game (500m–1500m)

- Every 500m, a **BiomeTransition** event fires.
- Background fades; new dominant trees and colors enter.
- Obstacle spawn pool updates to biome-specific entities.
- Speed increases 5% every 250m.
- Music: bass line and flute melody layer in.
- Foxes and Ducks are introduced. Player must adapt tactics.

### 3.4 Bloom State — Power-Up Phase

- Triggered when **10 Seeds** are collected (Bloom Meter fills).
- Can be activated by tap or auto-triggering (configurable).
- **Duration:** 5 seconds.
- **Effects:**
  - Woman glows with petal trail behind her feet.
  - Full invincibility — she passes through all obstacles.
  - Speed = 2× normal speed.
  - Obstacles she passes are converted into bonus Seeds.
  - Background becomes hyper-saturated ("psychedelic forest").
  - Flowers along the path visually bloom open as she passes them.
  - Music becomes orchestral and triumphant.
- After Bloom State ends, normal play resumes with a smooth transition.

### 3.5 Chaos Peak — Late Game (1500m+)

- **Biome:** The Stormy Ridge / Deep Thicket.
- Eagles dive from above while Wolves sprint simultaneously.
- Screen shakes subtly at 1000-point milestones.
- Wind speed maximum — all flora oscillates at maximum intensity.
- Music is fully layered, high tempo.

### 3.6 The Soft Fall — Game Over (REST State)

- On collision, the character **does not "die"**.
- She stumbles, slows, and **sits down on the grass**, looking tired but peaceful.
- The forest continues scrolling gently in the background.
- A calm end-run summary appears:
  - Distance travelled (e.g., `1,842 metres`)
  - Seeds collected (e.g., `15 Seeds`)
  - Highest combo/streak
  - New best distance (if applicable)
- Transition back to the Garden.

### 3.7 Meta-Loop — The Garden

- Seeds collected during the run are used to **grow new plants** in the garden.
- Each plant from the Entity Database can be unlocked and placed.
- Over weeks of play, the garden becomes a lush, personalized pixel forest.
- The next locked plant is always visible (greyed out), creating a clear reward target.
- **Example:** "You need 10 more Seeds to unlock Vanilla."

---

## 4. Core Input System

| Input | Action |
|---|---|
| **Single Tap** | Standard jump (fixed height) |
| **Hold / Long Press** | High jump — height proportional to hold duration |
| **Swipe Down** | Duck / Slide |
| **Multi-touch supported** | Jump and duck inputs evaluated independently |

**Jump Physics:** Jump height is strictly tied to touch duration (`gravity vs uplift`). Short tap = low hop. Full hold = maximum arc. This gives the player complete vertical control.

---

## 5. Scoring System

| Event | Score |
|---|---|
| Distance (per metre) | +1 point |
| Seed collected | +10 points |
| Cat "Kindness Bonus" (jumped over) | Score multiplier ×2 |
| Close Call (pixel-perfect dodge) | +50 points + Slow-Mo sparkle |
| 1000-point milestone | Screen shake reward |

### Nature's Grace — Close Call Multiplier
- If the player narrowly misses an animal (pixel-perfect clearance), the screen flashes a **vibrant green border**.
- The score multiplier increases.
- A "Slow-Motion Sparkle" effect triggers for ~0.5 seconds.

---

## 6. Collectibles — Seeds

- Seeds are **vibrant glowing orbs** scattered throughout the run.
- Some Seeds are placed above hazards (e.g., above Lily of the Valley) as deliberate traps.
- Seeds also drop when the player enters Bloom State and passes obstacles.
- Seeds feed two systems:
  1. **In-run:** Fill the Bloom Meter (10 Seeds = Bloom).
  2. **Meta-game:** Currency for unlocking garden plants.

---

## 7. Haptic Feedback

| Event | Haptic |
|---|---|
| Jump | Short pulse |
| Bloom State activates | Long sustained pulse |
| Game Over / Collision | Long strong pulse |
| Close Call | Subtle double-tap pulse |
| 1000-point milestone | Medium pulse |

Implementation: Android `Vibrator` service via `VibrationEffect`.

---

## 8. Save System

- **Local High Score** stored in `SharedPreferences`.
- **Garden Progress** saved as a JSON file in internal storage:
  - Which plants are unlocked
  - Total seeds collected (lifetime)
  - Last biome reached
- No cloud sync in v1.0.

---

## 9. Audio Design

### Music — Adaptive / Procedural

| Game State | Audio |
|---|---|
| Garden / Menu | Soft ambient acoustic, gentle wind |
| Running (Early) | Simple drum beat only |
| Running (Mid) | Bass line + flute melody layer added |
| Running (Late/Fast) | Full layered track, higher tempo |
| Bloom State | Orchestral, triumphant swell |
| REST / Game Over | Calm, slow outro |

**Dynamic Tempo:** Music tempo increases proportionally with `scrollSpeed` to keep the player's heart rate synced with the game.

### SFX

| Event | Sound |
|---|---|
| Seed collected | Soft `ping` chime |
| Jump | Whoosh |
| Landing | Soft thud + grass rustle |
| Dog (pre-spawn cue) | Bark sound 1 second before Dog appears on screen |
| Eagle (pre-spawn cue) | Screech audio cue before diagonal dive |
| Wolf howl | Howl triggers before it charges |
| Bloom State start | Magical chime + swell |
| Game Over | Gentle exhale / sit-down sound |

---

## 10. Unlockables & Progression

| Item | Cost (Seeds) | Description |
|---|---|---|
| Cactus (Garden) | 5 Seeds | First unlock, classic |
| Lily of the Valley | 10 Seeds | Glows at night |
| Hyacinth | 15 Seeds | Cluster variant |
| Eucalyptus | 20 Seeds | Tall, sways in wind |
| Vanilla Orchid | 25 Seeds | Low hanging vine |
| Cherry Blossom Tree | 40 Seeds | Spawns petal particles |
| Weeping Willow | 50 Seeds | Trailing curtain leaves |
| Jacaranda | 60 Seeds | Purple canopy, petal rain |
| Bamboo | 30 Seeds | Dense stalks |

Animals/Birds are not unlockable in garden (v1.0 scope).

---

*Document version: 1.0 | Project: Forest_Run | Last updated: 2026-03-04*
