# Forest Run — Unconditional single-body swept severity coverage (2026-09-28)

## Source-derived gap

A legal 50 ms max-speed recovery frame displaces scroll-bound entities 100 px. The airborne Player's narrow collision body can be just over 41 px wide; combined with a thin primary entity core, both endpoint rectangles can be disjoint while the bodies intersect between samples. The existing owner had a correct simultaneous swept-overlap solver but admitted only Cactus, Lily and Hedgehog. Directly inspected `onCollision` methods confirm that Hyacinth's *primary* core, Eucalyptus, Duck and Cat have unconditional HIT, and Fox and Wolf primary cores unconditional STUMBLE. A provisional near-contact on one of those species therefore could incorrectly be finalized as MERCY after a real missed interframe body overlap.

## Correction and evidence

Extend the existing `EntityManager.sweptNarrowCoreResult` eligibility switch to the six additional, unconditionally solid primary bodies. Keep the exact existing same-time rectangle solver, sampled end-frame query and HIT/STUMBLE-over-MERCY arbitration. Hyacinth's separate nonlethal brush remains separate and unswept; multi-part trees/flocks/Orchid windows stay excluded; Dog already owns its hazardous body and projectile sweeps; staged Owl/Eagle are excluded. No physical geometry, difficulty tuning or trigger/presentation/reward amounts are changed.

A manager-level Robolectric regression uses real `EntityFactory` instances of all nine eligible families with an airborne-width Player, legal 100px core displacement, actual distinct endpoints and intervening same-time overlap. It checks exact family severity, one final outcome, zero invented mercy/clean-pass reward and no accidental requirement for another family or creative asset. Exact-head CI owns executable verification.

This only closes unconditional primary-core tunneling. The 19-family multipart collision and physical action-sequence acceptance remain distinct, and this commit does not pretend to certify all possible trajectories or source art.
