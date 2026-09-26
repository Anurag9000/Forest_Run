# Forest Run — Mercy means completed safe passage (2026-09-26)

## Defect traced to game canon and live source

The authored contract defines MERCY as a player passing dangerously close *without touching*, exactly once and without conflicting with HIT/STUMBLE. The prior `EntityManager.checkCollisions` turned the first padded `MERCY_MISS` probe into a terminal `EncounterOutcome.MERCY`, marked the entity as passed and awarded a Heart, even when the entity was still directly ahead. A later real HIT or STUMBLE from that same entity was then skipped because collision queries only visit PENDING entities. This accidentally made an approach into hazard immunity and also mislabeled unresolved encounters as complete.

## Remediation

A mercy-band probe now marks `Entity.observedMercyContact` provisionally and has no reward/persistence/presentation side effect. The manager still arbitrates actual same-frame HIT before STUMBLE and leaves losers pending. After an encounter's complete live bounds are behind the player's real hitbox, a previously observed close approach resolves once as MERCY; otherwise it receives the existing CLEAN_PASS outcome. If a direct hit or stumble occurs first, it supersedes the provisional marker. If Bloom converts the encounter, Bloom exclusivity wins and no ordinary mercy Heart is awarded. Multiple encounters crossing in one frame each receive their own final outcome and Heart; the existing single-frame UI interface presents the first completed mercy feedback. Neither the precise per-family collision geometries nor the canonical outcomes change.

## Regression and remaining evidence

The existing priority permutation tests are updated to distinguish a provisional mercy overlap from an actual safe pass. New Robolectric lifecycle tests cover near-then-HIT, near-then-STUMBLE, repeated near-then-safe-pass, provisional near-then-Bloom, and simultaneous completed passes with no duplicate awards. Exact-head CI is the execution authority. Human feel, accessibility, sprite geometry at real framerates and physical-device capture remain external.
