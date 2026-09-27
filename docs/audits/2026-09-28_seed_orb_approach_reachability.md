# Forest Run — Seed Orb staging must admit a real jump before passing (2026-09-28)

## Source-derived gap

The prior `SeedOrbSpawnPolicy.forCleanPass` bounded random Orb Y positions to the conservative full-jump envelope, but placed X only 120px or 8% of screen width ahead of the live player. At 2,000px/s that is not enough approach time for the highest admissible Orb. The manager subsequently shifts Orb X by up to 30px to the left, and its 26px pickup core enters contact earlier still. Thus passing the vertical-band property tests did not prove an inviting, attainable reward.

## Correction

Reuse the existing `EncounterActionFeasibility` rise-time model with the full-jump Player constants and the highest allowed Orb, including 10px bob. Compute the minimum horizontal lead from rise time plus a declared 75ms gesture decision and 80ms margin, then account for the canonical 30px left jitter and 26px Orb core. Preserve the existing 120px/8%-screen floor and any farther authored encounter-based position. Share the jitter magnitude as one constant in `SeedOrbManager` and pass the current actual game-state speed from `EntityManager`. Invalid speed fails closed to the supported maximum. Other Orb reward, lifetime, height band, count and random draw order stay intact.

A Robolectric regression launches an actual Player after five 16ms frames, uses the fastest scroll speed and the worst left X jitter/highest seeded Orb, advances both the Player and real bobbing Orb, and checks actual pickup. An independent check verifies speed monotonicity and finite invalid-speed fallback. Existing vertical/compact/malformed geometry sweeps remain.

This is modeled/source-level attainability, not measured human reaction time or visual availability on a compact screen where an Orb may initially be offscreen. The physical-device and mixed-encounter Orb-baiting gates remain OPEN.
