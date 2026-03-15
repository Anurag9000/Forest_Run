# Visual And Feedback Effects Spec

## Original Dream

The intended presentation was:

- lush and alive, never static
- big readable sprites on phone
- constant subtle life in wind, petals, leaf motion, and ambient particles
- Bloom as a huge transformation moment
- strong juice on jumps, landings, near misses, milestones, and soft failure
- hand-authored biome mood, not just color tint changes

## Implemented Today

- screen shake via `CameraSystem`
- multiple particle presets
- HUD for score, distance, seeds, Bloom, and hearts
- ambient tint and darkness overlays
- menu/garden scene drawing
- sprite-driven player and entity animation
- audio and haptic hooks for several actions

## Missing Or Too Weak

- entity scale is not yet comfortably readable on phone
- encounter framing is too sparse to show off the art and behaviors properly
- backgrounds remain too procedural in places
- Bloom does not yet feel spectacular enough relative to the dream
- the world still lacks the full “breathing forest” density originally imagined

## Immediate Visual Priorities

- enlarge entity presentation and improve on-phone readability
- tune spawn cadence so the player actually sees and feels the forest inhabitants
- reduce ghost visual confusion
- make seeds, Bloom, and mercy feedback impossible to miss
- deepen biome atmosphere beyond tint shifts alone
