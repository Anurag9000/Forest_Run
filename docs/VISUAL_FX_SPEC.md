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

- Partial: parallax background now layers biome wash, canopy shade, mist bands, drifting leaves/petals/fireflies, stronger wind ribbons, horizon glow, and subtle speed/Bloom world-scale response on top of the procedural base.
- Partial: menu, rest, and Garden now use sanctuary-derived mist, lantern glow, ground-light bloom, and homecoming badge presentation to make the loop feel less flat.
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
- Partial: world-level wind ribbons now read more strongly through the parallax layer.
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
- Partial: the parallax layer now contributes drifting petals, leaves, fireflies, and mist so the world feels less empty even outside discrete emitters.
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
- Partial: runtime now adds stronger horizon glow, canopy shade, and night-sensitive firefly density to the background layer.
- TODO: richer authored lighting mood and stronger visual identity per phase.

## 6. Camera Effects

Dream effects:

- collision shake
- milestone shake
- subtle background scale/zoom logic at speed

### Current Status

- Implemented: shake and feedback.
- Partial: subtle background scale/zoom logic at speed and during Bloom now exists in the parallax layer.
- TODO: complete cinematic polish.

## 7. Environmental Interactions — Bloom Reaction

Dream expectation:

- nearby flowers bloom as the runner passes
- the world reacts to the player’s energy and path

### Current Status

- Partial: environmental Bloom reactions now exist on Bloom conversions for flora and trees, now with stronger world-burst feedback.
- TODO: broaden those reactions into fuller nearby-flower response and final device-proof scenic polish.

## 8. Spirit Of The Forest — Bloom State Visuals

During Bloom, the dream presentation includes:

- glowing heroine
- petal trail
- more saturated world
- obstacles dissolving or transforming into reward
- floor flowers opening
- UI pulse and power fantasy clarity

### Current Status

- Implemented: Bloom state, Bloom meter, aura effects, activation burst, stronger full-screen treatment, broader world tinting, and stronger HUD power framing.
- Partial: player-following Bloom trail, stronger conversion feedback, transformed-world presentation, and stronger parallax-world ambience now exist in runtime.
- TODO: make Bloom feel fully spectacular, obvious, and unforgettable on actual phone hardware.
