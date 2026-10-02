# Forest Run — Monotonic concurrent history-unlock persistence (2026-10-02)

## Defect

Persistent history marks are irreversible unlocks, but `saveUnlockedHistoryMarks` replaced the entire SharedPreferences string set with the caller's snapshot. `PersistentMemoryManager.refreshHistoryUnlockState` performs a load/derive/save cycle after independent encounter events. Two concurrent refreshes could each discover a different valid mark and then overwrite one another, leaving counters correct while silently losing one permanent history unlock.

## Correction

Treat the storage contract as monotonic: acquire the existing reentrant progression lock, copy the current canonical set, union the caller's marks, and publish only if the union changed. This preserves the previous single-writer behavior while preventing stale whole-set replacement from removing another event's unlock.

A two-worker Robolectric regression repeatedly publishes disjoint permanent unlock sets and requires the final canonical set to contain their exact union. This is in-process serialization; no multi-process writer is claimed.
