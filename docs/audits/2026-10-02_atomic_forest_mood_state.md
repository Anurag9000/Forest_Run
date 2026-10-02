# Forest Run — Atomic Forest Mood run accounting (2026-10-02)

## Defect

`ForestMoodSystem.recordRun` derived seven persisted fields from a previously loaded `ForestMoodState` and then overwrote the full state. Two completed runs persisted concurrently could both read the same `totalRuns` and per-mood counts, then each publish one successor. The result could lose an entire completed run and one mood bucket even though both terminal outcomes were otherwise committed.

## Correction

Add a locked `SaveManager.updateForestMoodState` transform and route `recordRun` through it. Exact-state save/load used by recovery is also serialized and centralized through sanitized read/write helpers. Saturating count and streak behavior is unchanged.

A repeated two-worker Robolectric regression races one Gentle run against one Fearful run from an empty state and requires totalRuns=2, gentleRuns=1 and fearfulRuns=1 with a one-run current-mood streak. This addresses in-process persistence only.
