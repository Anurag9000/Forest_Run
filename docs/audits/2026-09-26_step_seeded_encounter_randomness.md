# Forest Run — Per-step authored encounter randomness (2026-09-26)

## Source-to-runtime finding

`EncounterDirector` emits authored entity types, offsets and variants in deterministic order, but the concrete Bamboo, Chickadee Group and Dog still used process-global `Random`. Bamboo selected a random gap height, Chickadees sampled their initial/current targets and later independent target changes, and Dog sampled buddy duration (plus default buddy mode chance in the factory). Replaying an identical authored scenario could therefore produce different geometry, safe lanes, flight paths or companion duration even when its source fingerprint and input trace matched. This is especially relevant to `BAMBOO_GAP`, `CHICKADEE_SWERVE`, `DOG_BUDDY` and family showcases.

## Ownership change

`EncounterDirector` publishes an explicit step index and a versioned stable integer seed derived from the scenario's stable name and authored step index. `EntityManager` creates a separate seeded Kotlin `Random` for each directive and passes it through `EntityFactory`. Bamboo, Chickadee Group and Dog use their instance RNG for all authored random choices, including later Chickadee altitude targets. Ordinary spawns and callers retain `Random.Default`; different authored steps do not affect each other's random streams. No scenario timing, type, offset, probability distribution or variant was changed.

A director JVM test checks indices, distinct per-step seeds, replay equality, frame-partition independence and invalid-index rejection. A separate real entity/factory/manager integration test covers reproducible Bamboo gap geometry, Chickadee altitude trajectories and buddy duration. This is deterministic **stochastic initialisation given an identical run/update sequence**; it does not claim identical rendered frames under arbitrary timing partitions or hardware.

The existing scenario/input SHA contracts remain unchanged, because authored scenario definition and input schedule did not change. The RNG algorithm is separately versioned by a named salt and this audit; changes to that policy require deliberate revalidation. Actual physical feel remains an external acceptance gate.
