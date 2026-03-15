# Undertale-Inspired Personality Layer

## Purpose

This document restores the original personality ambition for `Forest Run`. The intended goal is not a literal narrative RPG route system, but it is much more than a few floating text popups. The game should feel inhabited, forgiving, funny, slightly self-aware, and capable of remembering the player.

## Original Dream

The original personality layer included:

- floating flavor text for meaningful interactions
- contextual rest and determination-like quotes
- mercy hearts from near misses
- spare thresholds that change how animals behave
- full-biome friendship or pacifist bonuses
- persistent encounter memory
- repeat-killer deja vu reactions
- costume changes unlocked by repeated encounters
- ghost replay as the spirit of your best self
- expressive, cute, imperfect presentation rather than polished blandness

## What Exists Today

- `FlavorTextManager` exists
- mercy hearts exist
- ghost replay exists
- some animal-specific reactions exist in code
- rest/game-over presentation exists

## What Exists But Is Not Yet Strong Enough

- Cat, Fox, Wolf, Dog, and Hedgehog all have some unique logic in code, but current user feedback says these personalities are not landing clearly enough on device.
- The mercy system exists as counters and some entity-specific consequences, but it does not yet dominate the emotional feel of the run.
- The ghost exists, but current presentation can confuse more than delight.

## What Is Missing

- richer determination-style quote system at rest
- persistent memory manager with encounter counts
- costume overlays
- full deja vu repeat-killer reactions
- stronger dialogue bubble staging
- stronger pacifist/friendship bonuses
- world-level sense that the forest remembers how you behave

## Entity Personality Targets

- `Cat`: should feel small, funny, reward kindness, and sometimes wave off
- `Fox`: should feel sly, playful, reactive, and mirror the player at the right moments
- `Wolf`: should feel threatening but not mindless, with a readable howl-charge drama
- `Dog`: should alternate between hazard chaos and lovable buddy energy
- `Hedgehog`: should be annoying in a cute way, not just invisible punishment
- `Owl`: should feel eerie and intentional, especially in night-biome contexts

## Non-Negotiable Outcome

The personality layer is successful only if a player can finish a session and say things like:

- “the fox copied me”
- “that cat rewarded me”
- “the wolf howled before charging”
- “the dog was weirdly cute”
- “the game noticed my near miss”
- “the forest feels like it remembers me”

If the player instead experiences the entities as anonymous small hazards, the personality layer is not complete regardless of what code paths exist.
