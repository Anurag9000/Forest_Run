# Forest Run — Do not stage optional Seed Orbs through unresolved hazards (2026-09-28)

## Follow-on cross-layer finding

Speed-aware horizontal reachability needs a second contract: the safer farther-ahead Orb can land on or beyond the next authored pending encounter. A reward placed beyond a hazardous obstacle may lure the player into that obstacle and violates the authored requirement that Orb placement feel inviting, not like forced collision bait. The prior clean-pass owner supplied only the departed entity and player; it never checked the next pending hazard before staging.

## Correction

A pure `SeedOrbSpawnPolicy.isClearOfPendingEncounter` predicate conservatively rejects any unresolved entity spanning the player-to-Orb approach corridor, including maximum +/-30px Orb jitter, its 26px core, 12px halo, and an 8px clearance. Invalid blocker geometry rejects the optional Orb. In `EntityManager.resolveCleanPass`, scan only other active PENDING entities and invoke `SeedOrbManager.trySpawn` only when the approach is clear. The passed encounter's outcome, unique reward, clean-pass count, persistence and memory still resolve exactly once. Deterministic test injection of the existing Orb manager permits actual lifecycle tests without probabilistic 95%-spawn flakes.

The policy test covers an in-corridor hazard, one fully behind, one beyond, malformed/empty bounds. A Robolectric manager integration test proves a clean pass still credits progress without an Orb when another hazard blocks the corridor, and that a later clear approach does spawn an Orb.

This is a conservative snapshot filter, not a proof against a future encounter that has not yet spawned, a moving hazard overtaking its original bounds, or physical-device perceptual baiting. Those remain explicit external/sequence acceptance work.
