# Forest Run — Terminal Rest high-score snapshot truth (2026-09-27)

## Source-derived bug

After long-horizon high-score writes became monotonic, a stale run could still set `isNewHighScore=true` when earning 200 points while another `GameStateManager` saved a higher 900-point record. `TerminalHitOutcomeCoordinator.complete` builds its Rest preview before `RunResetManager.triggerDeath` calls `GameStateManager.save`. Thus a stale preview might claim a new record even though the canonical stored achievement correctly remains 900.

## Correction

For a persistence-authorized terminal preview, reconcile the local high score with the canonical `SaveManager` record before constructing `RunSummary`; only mark a new high if the run's own score is at least the reconciled best. `GameStateManager.save` also clears a superseded local new-best marker when rejoining a greater canonical record. Deterministic/nonpersistent runs preserve their isolated local score display and cannot publish it to durable preferences. Three Robolectric tests cover a stale lower run, a local nonpersistent debug preview, and a genuinely new unsaved record.

The preview reflects durable state at construction time, not future concurrent runs or multi-device synchronization. The existing monotonic writer remains the source of truth for durable records.
