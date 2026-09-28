# Forest Run — Random encounter spawn lead uses sampled Player action feasibility (2026-09-28)

## Reproduced compact/high-speed gap

Ordinary random admission had been strengthened to wait for the previous persistent encounter to resolve and for the Player to return to RUNNING/LANDING. The spawn origin, however, remained fixed at `screenWidth + 120`. On a compact 640px landscape at the supported 2,000px/s ceiling, that fixed offscreen origin is much closer to the Player than on a 1,920px surface. A newly created entity is also updated later in the same `EntityManager.update`, with the fastest immediately approaching ordinary family (Hedgehog at 1.15× world scroll) consuming as much as 115px in the legal 50ms recovery frame before collision sampling. Origin-to-origin `SpawnPacing.requiredGapPx` therefore did not by itself prove the source specification's world-space/action-feasibility requirement for the *new* encounter.

## Correction

Keep the existing origin-spacing curve and prior-resolution gate. Add an independent random-spawn action lead derived from the existing `EncounterActionFeasibility` model and the real sampled Player constants. The envelope budgets the source-side 75ms decision assumption, 80ms safety margin, sampled full-jump time to the 495px apex, and one maximum admitted frame at the current fastest immediate ordinary-encounter approach multiplier (1.15×) for same-tick entity motion. The random spawn origin is now the farther of the historical `screenWidth + 120` location and `player.hitbox.right + actionLead`. This principally changes compact/high-speed staging; wide surfaces that already provide more lead retain their existing origin.

The encounter selection bag, biome pool, random family choice, origin-to-origin pacing curve, authored debug scenarios and entity-specific telegraphs remain unchanged. Unit tests verify the max-speed lead still passes the sampled full-apex feasibility model after subtracting the same-frame motion budget. A compact 640x360 integration test drives real max-speed run state until one random encounter is created and checks its post-update live bounds against that same action envelope.

## Boundary

This is a conservative source-side action envelope, not measured human reaction time and not human fairness approval. Family-specific visual telegraph/readability, physical-device control comfort and subjective late-game pacing remain acceptance work.
