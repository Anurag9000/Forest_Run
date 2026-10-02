# Forest Run — Atomic Return Moment state transforms (2026-10-02)

## Defect

`ReturnMomentState` combines last-active time, last rendered Garden greeting day, and rough-run streak. `recordRunOutcome` and `acknowledgeGardenMomentShown` both performed load → copy one subset of fields → save the entire state. If persistence and Garden presentation overlapped, the later stale whole-state write could erase either the newly incremented rough streak or the already-rendered greeting day.

## Correction

Add a locked `SaveManager.updateReturnMomentState` transform primitive that reads, transforms and writes the tuple under the shared reentrant progression lock. Route run-outcome recording and Garden rendered-acknowledgement through it. Keep `saveReturnMomentState` as an exact-state API for recovery, but serialize its write as well. Centralize state sanitization in private read/write helpers.

A repeated two-worker Robolectric regression races a rough run outcome against a Garden greeting acknowledgement and requires both independent effects to survive. No greeting selection rules, local-day calculation or rough-run classification thresholds change.
