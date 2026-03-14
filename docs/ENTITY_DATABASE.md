# Entity Database

## Flora

- `Cactus`: static ground hazard
- `LilyOfValley`: ground hazard with sway
- `Hyacinth`: ground hazard with brush / mercy zone
- `Eucalyptus`: ground obstacle
- `VanillaOrchid`: ground obstacle

## Trees

- `WeepingWillow`
- `Jacaranda`
- `Bamboo`
- `CherryBlossom`

Trees are tall scrolling obstacles with biome flavor and particle/sway hooks.

## Birds

- `Duck`: low-flying hazard
- `TitGroup`: flying group hazard
- `ChickadeeGroup`: flying group hazard
- `Owl`: perched bird that can dive if the player jumps nearby
- `Eagle`: aerial hazard

## Animals

- `Cat`: pass reward and optional wave-off behavior
- `Wolf`: howl / charge state machine, stumble collision
- `Fox`: jump-mirror behavior, stumble collision, mercy-based spare path
- `Hedgehog`: ground animal hazard
- `Dog`: hazard mode with bark projectiles, plus buddy variant

## Important Runtime Rule

Entity pass actions are now one-shot per instance. This prevents repeated reward farming and repeated seed orb spawning after the entity has already been passed.
