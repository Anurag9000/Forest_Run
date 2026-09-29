# Forest Run — final-frame Owl/Eagle swept contact before escape reward (2026-09-29)

## Reproduced lifecycle gap

`EntityManager.update` removes an Owl/Eagle immediately when a genuinely telegraphed dive exits vertically and places it in `completedBirdEscapes`. Collision arbitration runs afterward. Removed birds are no longer in `activeEntities`, so their final update segment was never queried for contact; `resolvePassedEntities` could then award CLEAN_PASS/MERCY/BLOOM escape resolution. At a legal 50 ms recovery frame a fast dive can have its preceding core above the Player and its final core below the surface, crossing the Player between endpoint samples.

This is distinct from the existing narrow-core sweep: staged divers were deliberately excluded there because their attack state is conditional, but by the time they enter `completedBirdEscapes` the Owl is a warned DIVING attack and the Eagle is a locked, viewport-entered completed attack.

## Correction

Before any deferred escape reward and before lower-severity live outcomes, inspect completed attack birds for an endpoint HIT or exact same-time `SweptCoreOverlap` across their retained previous/current primary cores and the Player's previous/current core. A detected HIT consumes the deferred escape, records one terminal HIT outcome and cannot later receive a clean-pass/mercy reward. Bloom remains exclusive: while Bloom is active this lethal check is skipped and the existing conversion owner handles the escaped bird.

Robolectric integration tests construct genuine Owl/Eagle attack states, drive a single admitted final segment from above the Player through the Player and beyond the vertical exit boundary, and assert HIT with zero clean passes. Existing safe-escape and Bloom tests remain intact.

## Boundary

This closes a deterministic final-frame tunneling path. It does not claim human telegraph readability, final art quality, or physical-device fairness.
