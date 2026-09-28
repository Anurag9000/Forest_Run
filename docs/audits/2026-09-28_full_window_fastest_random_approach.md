# Forest Run — Random spawn lead uses fastest approach for the entire reaction window (2026-09-28)

## Defect

The new compact-surface random-spawn admission computed a full sampled jump reaction duration, but multiplied that duration by ordinary world scroll speed. Hedgehog is the fastest ordinary family that begins closing immediately at 1.15x scroll speed. The previous formula applied 1.15x only to the extra same-frame update allowance, not to gesture decision, safety margin, or sampled rise time. At the supported 2,000 px/s ceiling this under-staged the fastest family by more than two hundred pixels before geometry offsets, so an origin advertised as cross-family action-safe was not actually action-safe for the whole Hedgehog approach.

## Correction

Convert the *entire* decision + safety + sampled full-rise + one admitted creation-frame duration using the fastest immediate approach speed (1.15x the bounded world speed). The formula remains finite and speed bounded at the world-speed input boundary; the entity-relative approach may legitimately exceed the world ceiling.

The SpawnPacing regression now evaluates the remaining post-creation lead using 1.15x approach speed. A Robolectric integration test stages an actual Hedgehog on a compact 640x360 surface at max world speed, advances the same creation frame through EntityManager, then verifies the remaining real encounter bound still admits the sampled 495px full-jump reaction with the authored decision/safety budget.

No encounter frequency curve, Player physics, Hedgehog speed, or visual timing is retuned.
