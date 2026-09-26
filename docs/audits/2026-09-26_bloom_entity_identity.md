# Forest Run — Bloom entity-identity correctness (2026-09-26)

## Source-to-mechanic trace

The game canon requires Bloom's proximity response to be forest-wide and individually legible rather than making unrelated creatures silently share a reaction. `EntityManager.updateBloomNearbyWorldReaction` previously stored `System.identityHashCode(entity)` in `MutableSet<Int>` and reset it at Bloom transitions and run reset. The integer is a hash, not a guaranteed unique identifier. Two distinct live entities with the same identity hash would be treated as one, and a later object that reuses a removed object's hash could also lose its proximity response within the same Bloom window.

## Remediation

The ownership boundary now uses `BloomReactionIdentityLedger<Entity>` based on `IdentityHashMap`, which compares actual object references rather than their integer hash or overridden equality. `EntityManager` checks and marks the entity reference and preserves the original Bloom transition/reset clear sites. Runtime presentation, timing, reaction radius, cue selection and the single-reaction-per-entity rule are otherwise unchanged.

The JVM regression test exercises two distinct colliding objects that are also equal under `equals`, first/duplicate admission, and clear/reuse behavior. The test targets the actual ledger used by `EntityManager`, rather than duplicating the implementation in a test.

## Verification and remaining evidence

The change is source-addressable gameplay correctness. Verify the exact resulting commit's JVM and API-35 workflows. Physical readability, Bloom restraint, art, accessibility feel and creative review still require actual device/human evidence. A passing test alone cannot establish those external gates.
