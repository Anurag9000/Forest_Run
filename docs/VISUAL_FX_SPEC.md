# Forest_Run — Visual & FX Specification (Restored)

This restores the original visual and feedback ambition as an active target.

## 1. Parallax Scrolling System

The dream world uses four layers:

| Layer | Name | Speed | Contents |
|---|---|---|---|
| 1 | Far Background | 10% | mountains, sky, clouds |
| 2 | Mid-ground | 30% | distant trees, hills |
| 3 | Main Play Area | 100% | obstacles, ground, flora, animals |
| 4 | Foreground | 150% | close-up leaves/grass |

### Current Status

- Implemented: parallax background in procedural/tint-driven form.
- TODO: full bespoke background art and stronger sense of depth.

## 2. Wind System — SwayComponent

All plants and trees should feel wind-affected. Wind should vary by biome and progression.

Dream traits:

- sine-wave sway
- unique per-entity speed/intensity
- rising wind over distance
- temporary wind spikes from environmental interactions

### Current Status

- Implemented: sway component exists.
- TODO: fuller world-level wind drama and stronger scenic payoff.

## 3. Character Animation — Squash & Stretch

Dream traits:

- launch squash
- jump stretch
- landing squash
- duck compression
- secondary hair/clothing motion
- fast readable 48-frame-feeling run cycle

### Current Status

- Implemented: core squash/stretch states.
- TODO: full expressive secondary motion and richer presentation.

## 4. Particle Systems

Dream particle families include:

- petal drift
- dust/pollen kicks
- running pollen trail
- seed orb bursts
- Bloom trail
- fireflies
- Lily glow wisps
- kindness bonus particles
- bark shockwaves

### Current Status

- Implemented: multiple particle presets including Bloom, death, mercy, seed, and movement effects.
- TODO: full living-forest density and stronger scenic saturation.

## 5. Dynamic Lighting — Day/Night Canvas Filter

Dream lighting phases:

- Morning
- Day
- Golden Hour
- Twilight
- Night

Expected:

- smooth transitions
- sky shifts
- glow interactions
- fireflies and night-specific ambience

### Current Status

- Implemented: biome tinting and darkness overlay.
- TODO: richer authored lighting mood and stronger visual identity per phase.

## 6. Camera Effects

Dream effects:

- collision shake
- milestone shake
- subtle background scale/zoom logic at speed

### Current Status

- Implemented: shake and feedback.
- TODO: complete cinematic polish.

## 7. Environmental Interactions — Bloom Reaction

Dream expectation:

- nearby flowers bloom as the runner passes
- the world reacts to the player’s energy and path

### Current Status

- TODO: fuller environmental Bloom reaction system.

## 8. Spirit Of The Forest — Bloom State Visuals

During Bloom, the dream presentation includes:

- glowing heroine
- petal trail
- more saturated world
- obstacles dissolving or transforming into reward
- floor flowers opening
- UI pulse and power fantasy clarity

### Current Status

- Implemented: Bloom state, Bloom meter, aura effects.
- TODO: make Bloom feel spectacular, obvious, and unforgettable in actual play.
