# Forest_Run — Visual & FX Specification

A complete reference for all visual systems: parallax scrolling, procedural wind, particle systems, squash & stretch, camera effects, and dynamic lighting. Every system here is mandatory for the "living forest" feel.

---

## 1. Parallax Scrolling System

The background must have **4 distinct layers**, each scrolling at a different speed to create an illusion of depth.

| Layer | Name | Scroll Speed (% of play area) | Contents |
|---|---|---|---|
| Layer 1 | Far Background | 10% | Distant mountains, sky gradient, clouds |
| Layer 2 | Mid-ground | 30% | Faded background trees, distant hills |
| Layer 3 | Main Play Area | 100% | All obstacles, ground path, flora, animals |
| Layer 4 | Foreground | 150% | Close-up blurred leaves/grass that zip past camera |

### Implementation Notes

- Each layer is a looping bitmap. When the bitmap fully scrolls off-screen left, it is repositioned to the right.
- Layer 1 should be tinted by the current `DayPhase` colour filter.
- Layer 4 elements are **intentionally blurred** (reduce pixel saturation, increase transparency) to simulate shallow depth-of-field and create a sensation of speed.
- **Camera Zoom:** At high speeds, Layer 1 (far background) should subtly **pull back** (scale down slightly) to make the world feel larger and more vast.

---

## 2. The Wind System — `SwayComponent`

All plants and trees must have wind physics. Wind is **never static** — it is driven by a global `WindSpeed` variable that changes with distance/biome.

### The Sin-Wave Sway Formula

```
xOffset = sin(time * speed) * intensity
```

The **top anchor point** of the sprite oscillates horizontally while the **base remains fixed**. This creates a natural sway effect without requiring many animation frames.

### Flora Sway Parameters

| Entity | `speed` | `intensity` | Character |
|---|---|---|---|
| Weeping Willow | 0.5 | 20px | Slow, graceful, heavy curtain |
| Bamboo | 3.0 | 4px | Stiff, quick, tense jitter |
| Cherry Blossom | 1.2 | 12px | Medium, light, floating |
| Jacaranda | 0.8 | 15px | Medium-slow, majestic sweep |
| Eucalyptus | 2.5 | 6px | Fast, whipping, sharp |
| Lily of the Valley | 1.5 | 5px | Gentle, delicate tremble |
| Hyacinth | 1.0 | 7px | Moderate cluster sway |
| Vanilla Vine | 0.9 | 10px | Pendulum-like |
| Cactus | — | — | NO SWAY. Rigid. |

### Wind Speed Variable

- `globalWindSpeed` starts at `1.0` (base multiplier applied to all sway components).
- Increases with distance: every 300m, `globalWindSpeed += 0.1`.
- Cherry Blossom `performUniqueAction()` adds a temporary `+0.5` spike for 3 seconds.
- At max wind, all sway intensities reach 1.5× their base values.
- The character's hair/clothing responds: at `globalWindSpeed > 1.5`, her hair animation plays at 1.5× speed.

---

## 3. Character Animation — Squash & Stretch

The character must feel physically present and responsive. Squash & Stretch is the core animation principle here.

### State-Based Transformations

| State | `scaleY` | `scaleX` | Duration |
|---|---|---|---|
| `RUNNING` (idle) | 1.0 | 1.0 | — |
| `JUMP_START` (launch) | 0.8 | 1.25 | 2 frames — Squash before launch |
| `JUMPING` (ascending) | 1.2 | 0.85 | Full ascending arc — Stretch upward |
| `APEX` (peak) | 1.0 | 1.0 | 1–2 frames — Neutral |
| `FALLING` | 1.15 | 0.9 | Full descending arc |
| `LANDING` | 0.75 | 1.3 | 3 frames — Heavy squash on impact |
| `DUCKING` | 0.7 | 1.15 | While input held |

### Hair/Clothing Secondary Motion

- Hair is a **secondary animated element** on top of the character sprite.
- **Jump trigger:** Hair lags 2 frames behind the body's upward motion, then "snaps" back.
- **Landing trigger:** Hair bounces forward then settles.
- At `globalWindSpeed > 1.0`: hair layers play at `1.0 + (windSpeed - 1.0) * 0.5` speed.

### 48-Frame Run Cycle

- The run cycle is a **48-frame sprite sheet** played at 24fps (2 frames per game frame at 60fps).
- Sheet file: `assets/player_run.png` — single horizontal strip of 48 frames.
- A helper function slices the sheet: `splitSpriteSheet(bitmap, frames = 48) : Array<Rect>`
- States and their frame counts:
  - `RUNNING`: frames 0–47 (loop)
  - `JUMPING`: frames 48–59 (one-shot, then hold last frame)
  - `DUCKING`: frames 60–67 (hold last frame)
  - `LANDING`: frames 68–71 (one-shot, then return to RUNNING)
  - `GAMEOVER / REST`: frames 72–95 (one-shot, then hold last)

---

## 4. Particle Systems — `ParticleManager`

The `ParticleManager` is a singleton that manages all active particle emitters. Each emitter has: `position`, `rate`, `lifetime`, `velocity`, `size`, `colour`, `spread`.

### System 1 — Petal Drift (Cherry Blossom / Jacaranda)

- **Trigger:** Active during Spring Orchard (Cherry Blossom) and Violet Path (Jacaranda) biomes.
- **Particles:** Pink (Cherry Blossom) / Purple (Jacaranda) pixels, 2×2 to 4×4px.
- **Behaviour:** Constant gentle fall from top of screen. Light left-drift. Occasional spiral. Slow velocity.
- **Intensity:** Base rate. Doubles during Cherry Blossom `WindGust` event.
- **Purpose:** Atmospheric + Petal Blinding (hides small hazards).

### System 2 — Dust / Pollen Kicks (Landing Impact)

- **Trigger:** Fires when `player.isGrounded` state changes from `false` to `true`.
- **Particles:** Brown/green earthy pixels, 1×2px.
- **Behaviour:** Burst outward in a fan from feet position. Fast initial velocity, quick fade.
- **Quantity:** 8–12 particles per landing.
- **Purpose:** Physical feedback for landing — the ground reacts.

### System 3 — Pollen Trail (Running Feet)

- **Trigger:** Continuous while `player.state == RUNNING`.
- **Particles:** Glowing green/gold tiny pixels.
- **Behaviour:** Emitted from feet position. Slow upward drift. Long lifetime (1.5s). Very low opacity.
- **Purpose:** Adds magic trail behind the runner.

### System 4 — Seed Orbs

- **Trigger:** Seed pickup.
- **Particles:** Gold sparkle burst from seed position, flying toward Bloom Meter UI.
- **Behaviour:** Short-lived, fast, arc toward meter. `ping` SFX at collection.

### System 5 — Bloom State Trail

- **Trigger:** Active during Bloom State.
- **Particles:** Mixed pink/purple/gold flower petals and sparkles from character.
- **Behaviour:** Explosion of petals behind runner at high rate. Wide spread. Medium lifetime.
- **Purpose:** Makes the Bloom State feel extraordinary.

### System 6 — Fireflies (Night Mode Ambient)

- **Trigger:** Active when `DayPhase == TWILIGHT` or `NIGHT`.
- **Particles:** Tiny yellow-green dots, soft glow halo.
- **Behaviour:** Slow random drift. Blink on/off with ~1.5s interval. Float in background Layer 2.
- **Quantity:** 5–12 on screen at any time.

### System 7 — Lily of the Valley Night Glow

- **Trigger:** Active on each Lily entity when `DayPhase == NIGHT`.
- **Particles:** White/cyan wisps rising from flower tip.
- **Behaviour:** Slow upward drift. Fade out over 2s. Loop.

### System 8 — Kindness Bonus (Cat jump)

- **Trigger:** On Cat Kindness Bonus activation.
- **Particles:** Pink hearts and gold sparkles burst from cat position.
- **Behaviour:** Short burst. Spread upward. Fast fade.

### System 9 — Bark Shockwave (Dog)

- **Trigger:** Dog `performUniqueAction()` fires.
- **Particles / Sprite:** Expanding ring sprite (shockwave), not particle-based — a stretched ellipse that scales and fades.
- **Duration:** 0.4s expand + fade.

---

## 5. Dynamic Lighting — Day/Night Canvas Filter

A `ColorFilter` matrix is applied to the **entire Canvas** and transitions smoothly based on `distanceTravelled`.

### Phase Transition Table

| Phase | Distance Range | `ColorFilter` Values | Notes |
|---|---|---|---|
| Morning | 0–300m | Warm peach (+20R, +5G, -10B) | Soft sunrise |
| Day | 300–700m | Neutral (identity matrix) | Clear natural light |
| Golden Hour | 700–1100m | Warm amber (+30R, +10G, -15B) | Rich gold-orange glow |
| Twilight | 1100–1500m | Deep orange/red (+20R, -5G, -20B) | Fire sky — fireflies activate |
| Night | 1500m+ | Deep violet (-10R, -5G, +25B) | Cool indigo — Owls & glows activate |

### Transitions

- Phase transitions must be **smooth** — interpolate `ColorFilter` over 5 seconds.
- Use `lerp` between the source and target filter matrices.
- Background Layer 1 sky gradient also changes independently to match the palette.

---

## 6. Camera Effects

### Screen Shake

- **Triggers:** Obstacle collision, 1000-point milestone hit.
- **Parameters:** Offset canvas draw position by a random small vector each frame. Dampen over 0.5s.
- **Intensity:** Collision = `±8px`. Milestone = `±4px` (gentler).
- Implemented by offsetting the `Canvas.translate()` call during `onDraw`.

### Camera Zoom (Speed Progression)

- As `scrollSpeed` increases, Layer 1 (background) scale decreases slightly.
- Formula: `layer1Scale = max(0.8f, 1.0f - (scrollSpeed - baseSpeed) * 0.002f)`
- Gives the impression the world gets bigger and more vast as speed increases.
- The character and play-area layer remain at 1.0 scale at all times.

---

## 7. Environmental Interactions — "Bloom" Reaction

When the player runs through a patch of flowers (not necessarily colliding):

- Flowers close to the runner's path play a **"Bloom" animation** — they pop open to full flower instantly.
- This means flowers near the ground that the player passes within a proximity radius (not hitbox) switch to a "bloomed" animation state.
- Effect applies to: Lily of the Valley, Hyacinth, Vanilla.

---

## 8. The "Spirit of the Forest" — Bloom State Visuals

During the 5-second Bloom State, the entire visual presentation changes:

1. **Character:** Glows with a gold/pink halo. Trail of petals behind her.
2. **Background:** Saturation increases by +50% (more vivid, psychedelic).
3. **Floor flowers:** Bloom open as she passes (see Section 7).
4. **Obstacles:** Each obstacle she passes "dissolves" into a burst of petals (seeds fly out).
5. **Sky:** Brief colour flash — screen flashes vibrant green then settles to enhanced saturation.
6. **UI:** Bloom Meter pulses with a ring of light.

---

*Document version: 1.0 | Project: Forest_Run | Last updated: 2026-03-04*
