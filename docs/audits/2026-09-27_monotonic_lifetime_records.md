# Forest Run — Monotonic long-horizon score and distance records (2026-09-27)

## Source finding

`SaveManager.saveHighScore` and `saveBestDistance` previously wrote caller-supplied absolute values without comparing the persisted record. A stale run could overwrite a higher score or farther distance with a lower value. Non-finite distance input was normalized to zero and could erase an achieved distance. These are best-achievement records presented across runs and in the Garden/Journal, not mutable currency.

## Change

Record writes read, compare and conditionally apply under the existing namespace/Garden monitor; only strictly higher valid achievements are stored. Negative persisted scores and non-finite/negative stored distances are normalized on read. Invalid incoming distances are ignored. `GameStateManager.save` rejoins the canonical high-score maximum so a long-lived in-memory owner also observes a newer record. Seed/Bloom, Garden costs, ghost serialization, score earning, and save schema are unchanged.

Robolectric tests exercise descending/out-of-order saves, corrupt legacy values, malformed distance inputs, and two GameStateManager owners saving different scores. Exact-head GitHub Actions execution is required before claiming tests pass.

## Acceptance boundary

An in-process record maximum does not prove real-device feel, multi-device synchronization, creative approval, production signing, or Play delivery.
