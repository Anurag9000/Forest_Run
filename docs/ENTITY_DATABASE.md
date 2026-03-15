# Entity Database

This document records the intended role of every entity, what is present now, and what still needs work to satisfy the original vision.

## Core Rule

Every entity should be:

- readable at phone scale
- mechanically distinct
- visually distinct
- emotionally distinct

## Flora

- `Cactus`
  Intended: simple readable early hazard with clean silhouette.
  Current: static ground hazard.
  Missing: stronger staging and readability on phone.

- `LilyOfValley`
  Intended: elegant low hazard with special placement and night charm.
  Current: sway hazard.
  Missing: stronger visibility, stronger magical/night identity.

- `Hyacinth`
  Intended: brushable plant with close-call tension.
  Current: brush or mercy zone exists.
  Missing: clearer player-facing distinction between brush and fatal contact.

- `Eucalyptus`
  Intended: taller obstacle with movement personality.
  Current: ground obstacle.
  Missing: stronger environmental drama and legibility.

- `VanillaOrchid`
  Intended: ground obstacle with visual uniqueness.
  Current: obstacle exists.
  Missing: enough runtime clarity to feel memorable.

## Trees

- `WeepingWillow`
  Intended: moody curtain-like obstacle and menu iconography.
  Current: obstacle exists and appears in menu/garden presentation.
  Missing: stronger scenic presence and more authored behavior.

- `Jacaranda`
  Intended: purple mood-shifting tree with petals and visual spectacle.
  Current: tall scrolling obstacle.
  Missing: richer petal atmosphere and more memorable play impact.

- `Bamboo`
  Intended: distinctive constrained-space obstacle.
  Current: tall obstacle.
  Missing: stronger visual readability and special-feel encounter design.

- `CherryBlossom`
  Intended: soft, beautiful, petal-rich tree with emotional lift.
  Current: scrolling obstacle.
  Missing: stronger petal identity and memorable runtime charm.

## Birds

- `Duck`
  Intended: low flyer that teaches ducking.
  Current: low-flying hazard.
  Missing: larger clearer profile and stronger telegraphing.

- `TitGroup`
  Intended: cute grouped flyer with rhythm challenge.
  Current: flying group hazard.
  Missing: enough size and staging to appreciate the group behavior.

- `ChickadeeGroup`
  Intended: energetic chaotic aerial personality.
  Current: flying group hazard.
  Missing: stronger readability and charm.

- `Owl`
  Intended: eerie perched bird that dives if the player jumps nearby.
  Current: perched dive logic exists.
  Missing: stronger night-biome mood and more obvious reactive drama.

- `Eagle`
  Intended: high-threat aerial dive with dramatic cue.
  Current: aerial hazard.
  Missing: stronger spectacle and clearer player perception.

## Animals

- `Cat`
  Intended: kindness bonus, flavor text, possible wave-off behavior.
  Current: pass reward and some spare-like behavior in code.
  Missing: enough runtime clarity for players to reliably notice the personality.

- `Wolf`
  Intended: howl, tension, charge, possible disengage through mercy.
  Current: howl/charge state machine and stumble collision exist.
  Missing: stronger dramatic pacing and easier player recognition.

- `Fox`
  Intended: mirror-jump trickster with sly flavor.
  Current: jump-mirror behavior and mercy spare path exist.
  Missing: stronger telegraphing and cute readability.

- `Hedgehog`
  Intended: tiny nuisance with non-lethal punishment.
  Current: speed-debuff trap.
  Missing: enough visibility to feel fair rather than invisible.

- `Dog`
  Intended: either bark hazard or buddy-mode charm event.
  Current: bark projectile mode and buddy variant exist in code.
  Missing: enough staging and player-facing delight to make the buddy behavior memorable.

## Immediate Entity Priorities

Based on current player feedback, the first entity-level fixes should be:

- increase screen size of birds, plants, trees, and animals
- reduce dead space between encounters
- verify every unique behavior on actual phone hardware
- ensure flavor text and mercy feedback are visible enough to be noticed
- tune ghost presentation so it never obscures entity readability
