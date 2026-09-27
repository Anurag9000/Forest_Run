# Forest Run — Eagle escape completion after collision arbitration (2026-09-27)

## Reproduced source path and contract

The master encounter lifecycle requires a single terminal outcome per encounter and the Eagle's authored lock-on/dive to have a real escape opportunity. `Eagle.update` deactivates a bird when the completed dive goes below ground or leaves either side. `EntityManager.update` previously removed that inactive Eagle immediately. Because `resolvePassedEntities` only considers active encounter bounds whose right edge has passed the Player's left edge, a primarily vertical dive could exit below ground while still ahead horizontally and disappear with `EncounterOutcome.PENDING`. No clean-pass/mercy/Bloom conversion, encounter record or unique Eagle feedback could follow. Granting reward during `update` would instead violate HIT > STUMBLE > MERCY arbitration later in the same frame.

## Fix

Expose a narrow read-only `Eagle.hasCompletedAttackEscape` predicate: inactive after locked dive and a real horizontal viewport entry. When such an unresolved bird exits, `EntityManager` removes it from render/collision-active entities but queues it until the normal `checkCollisions` pass phase. Live direct collisions arbitrate first. When no direct winner blocks progression, this escape resolves through the *same* exclusive completed-pass function as x-plane passage: Bloom conversion wins when active, otherwise provisional near-contact becomes MERCY, otherwise CLEAN_PASS with the existing Eagle unique action. The queue is consumed once and cleared on run reset. A pre-entry staged Eagle is never credited as an escape.

Three Robolectric tests use a real Eagle lock and flight to prove a vertical escape is no longer silently lost, verify Bloom exclusivity, and show a same-frame direct HIT takes priority with run reset discarding the deferred award. The tests use a controlled safe Player hitbox to isolate escape geometry; they do not replace real-player feel/trajectory acceptance on devices.

## Boundaries

This change does not alter the Eagle's authored velocity, target mark, lock duration, drawn corridor or sprites. It does not establish swept collision detection for extremely fast movement across thin geometry, or certify physical-device reaction fairness. Exact-head Android CI is required for executable closure.
