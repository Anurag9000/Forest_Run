# Forest Run — Compound encounter collision ordering (2026-09-26)

## Ground truth and source trace

The game and `EntityManager.checkCollisions` establish `HIT > STUMBLE > MERCY_MISS`: an actual overlap must not be converted to a near-miss reward merely because some earlier subcomponent is close. Review of all nineteen concrete encounter `onCollision` owners found three multi-hitbox deviations:

- `Dog.onCollision` returned mercy immediately for the first near-miss projectile, before examining any later projectile or the dog's own body.
- `TitGroup.onCollision` returned mercy immediately for an early near bird, before examining the remaining flock.
- `ChickadeeGroup.onCollision` had the same early-return ordering.
- `Bamboo.onCollision` already accumulated near-misses while prioritizing every true top/bottom stalk hit; no change was needed.

These three bugs are observable independent of the global priority selection, because `EntityManager` only sees the already-reduced result from each encounter.

## Remediation and regression

All three encounters now immediately return any direct `HIT` while accumulating near-miss candidates and returning `MERCY_MISS` only after all lethal candidates have been checked. Dog checks all bark projectiles and then the body before awarding a near-miss. The changes preserve existing hitbox geometry, projectile behavior, visual cues, and outcome ownership.

Robolectric tests construct the previously failing mixed-overlap arrangements using the real encounter objects and live sub-hitboxes: a near first projectile plus a direct Dog body hit; a near first flock member plus a direct second bird hit in both Tit and Chickadee. They also assert that an isolated near miss remains mercy when later direct overlaps are removed.

## Boundary

Source-level collision-result correctness is distinct from actual motion/readability acceptance. Exact-head JVM and API-35 validation must still pass, and physical high-speed fairness, telegraph clarity and audiovisual feel remain separate candidate-bound evidence.
