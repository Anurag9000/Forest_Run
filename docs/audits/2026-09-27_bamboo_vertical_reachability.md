# Forest Run — Bamboo randomized vertical gap is within Player reach (2026-09-27)

## Reproduced source defect

The previous constructor sampled centre uniformly from `gapHeight` through `groundY-gapHeight`. On the reference 1080px/groundY=885.6px layout, this admitted a 150px centre with a gap extending only from Y=75 to 225. The Player's actual maximum-jump body cannot ascend far enough to occupy that gap. Five visible stalks could therefore present a path with no physically reachable answer despite the spec requiring Bamboo's precision gap to remain viable.

## Change

Sample from a range derived from Player's current BASE_HEIGHT, HITBOX_INSET, MAX_JUMP_FORCE and GRAVITY, accounting for the maximum capped 0.05-second semi-implicit Euler step and an additional 20px vertical margin. Retain the original maximum centre and actual gap height. Fail explicitly for nonfinite or impossibly short geometry rather than quietly inventing an unreachable opening. The authored stalk geometry, distinctive featured gap, random seed behavior, obstacles, sprites and scoring remain unchanged.

Robolectric tests contrast the previously impossible low centre, exercise 96 seeded actual Bamboo instances against the actual Player's sampled full-jump collision body, check the lower/middle/upper reachable bounds at both supported speeds, and fail closed on invalid geometry.

## Interpretation

This establishes one-encounter vertical reachability in the supported reference geometry, not every horizontal timing, action-switch or mixed encounter pair, nor physical/human creative approval. Exact-head host and connected CI are required before marking the implementation tested.
