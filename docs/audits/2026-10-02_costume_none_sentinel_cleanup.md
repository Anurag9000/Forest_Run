# Forest Run — Preserve Costume NONE sentinel cleanup under monotonic unlock merging (2026-10-02)

The monotonic unlock-set correction made costume writes union-safe, but an existing stored `NONE` sentinel would then survive every merge. `CostumeManager.refreshUnlocks` deliberately treats `NONE` as non-unlocked state and previously rewrote the set without it, so preserving the legacy sentinel would regress that cleanup behavior.

The canonical merge now sanitizes both incoming and already-stored costume sets by excluding `CostumeStyle.NONE`, then writes whenever either a new unlock is added or the legacy sentinel must be removed. A Robolectric persistence test seeds raw `NONE + FOREST_SCARF`, adds `BLOOM_RIBBON`, and requires both the typed loader and raw preference set to contain only the two real unlocks.
