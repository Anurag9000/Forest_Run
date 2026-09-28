# Forest Run — Ordinary random encounter action-recovery admission (2026-09-28)

## Reproduced source gap

The authored fairness contract requires world/action feasibility, post-landing reaction and no impossible sequences. Production random cadence previously checked only accumulated world distance against `SpawnPacing.requiredGapPx`. At the late-game floor this can admit a second encounter while the previous persistent encounter is still unresolved or while the Player is still jumping, ducking or recovering from STUMBLE. The repository's own fairness audits already treated origin-gap arithmetic as insufficient proof.

## Correction

Random spawning now requires all three existing conditions (guided-opening lock released, production origin gap reached, normal mode) plus live action admission:
1. Player is RUNNING or LANDING, both states that can accept a new jump/duck choice.
2. No active persistence-owning encounter remains PENDING.

Blocked frames keep accumulating the existing distance counter; no tuning constant or second spacing curve is introduced. As soon as the prior encounter resolves and the real Player reaches a ready state, the next random encounter may stage from the existing offscreen spawn point. Deterministic/debug entities are nonpersistent and their run modes already disable random spawns, so authored overlap scenarios remain unchanged.

Robolectric integration drives the production EntityManager, accumulates more than the real spawn gap behind a pending Cactus, verifies no second spawn, resolves it, holds spawning through a real Player jump, then proves one new pending encounter is admitted on landing. Separate checks cover DUCKING/STUMBLE recovery and nonpersistent/resolved entities.

## Boundary

This closes ordinary random overlap/action-recovery admission in source. It does not prove every authored deterministic scenario, dynamic telegraph, compact-device perception, or human fairness judgment; those retain their own tests and physical acceptance requirements.
