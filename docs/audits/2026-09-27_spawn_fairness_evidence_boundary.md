# Forest Run — Spawn-gap arithmetic is not physical fairness certification (2026-09-27)

## Verified evidence-contract issue

The previous `SpawnFairnessObservation.isFiniteAndFair` effectively called an observation fair when it was finite, its origin gap lay inside declared constants and its lead time met `SPAWN_GAP_MIN_PX / MAX_SCROLL_SPEED`. At the advertised limits this is 780 / 2,000 = 0.39 seconds. The floor is derived from the same constants that produce it, so the assertion validates origin-gap arithmetic and bounds, **not** the player's ability to finish one action and respond to the next encounter. The Player's idealized minimum tap ballistic flight alone is 2 * 900 / 3,000 = 0.60 seconds before considering landing and differing encounter geometry. This comparison does not prove that any particular pair of encounters is impossible: one jump may clear more than one obstacle, and action geometry matters.

## Remedy

Rename the observed predicate to `isFiniteAndWithinDeclaredBounds` and the bound to `minimumDeclaredOriginLeadTimeSeconds`. Document explicitly that the observation does not certify action feasibility. Keep the actual production spacing curve, all gameplay constants and its conservative opening override unchanged. Regression tests verify the real numeric floor, finite grid and the gap between a geometric origin-spacing floor and the shortest idealized jump recovery. The tests must no longer assert overall gameplay fairness from a tautological threshold.

## Outstanding required evidence

Action-specific pair/sequence feasibility over all nineteen encounter families, relevant variant timings, current hitbox/telegraph trajectories, speed sweep, real gameplay control timing and device perception remains an OPEN gameplay-acceptance gate; no invented certification, fictitious physical-device result or arbitrary retuning is introduced.
