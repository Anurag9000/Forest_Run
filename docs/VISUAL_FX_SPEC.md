# Visual And Feedback Effects

## Implemented

- Screen shake via `CameraSystem`
- Particle presets for jump dust, land thud, bloom aura, death burst, mercy stars, seed collect, and more
- HUD bloom meter and score display
- Ambient biome tint overlay
- Simple menu/garden scene drawing
- Sprite-driven player and entity animation

## Current Presentation Reality

- Gameplay sprites are asset-backed.
- Several backgrounds and menu/garden visuals are still drawn procedurally with shapes, gradients, and tint bands.
- The game is playable and buildable without requiring a full bespoke art pass.

## Audio/Haptic Feedback

- Jump, land, hit, bloom, mercy, bark, howl, and related SFX hooks are wired
- Music transitions across menu, run layers, bloom, and rest
- Haptic pulses for jump, hit, mercy, and score milestones
