# Forest Run — Best run distance is independent from best-Ghost distance (2026-09-28)

## Cross-layer defect

The Journal's Run Legacy projection reads `SaveManager.loadBestDistance()`, while terminal best-distance durability historically advanced only as part of best-Ghost promotion. A legitimate persistent run with no accepted Ghost could therefore improve distance without improving the Journal's Best value.

A first attempted repair exposed an important second-order conflict: `GameView.pause()` calls `GameStateManager.save()` mid-run. If the new run-achievement writer shares the historical `best_distance` key used by `GhostPersistenceManager.bestDistanceFloor()`, pausing can raise the Ghost floor before terminal promotion. A later terminal run at the same distance would then be considered not better and its Ghost could be skipped. Run achievement and Ghost artifact eligibility are related but not the same durability fact.

## Final repair

- `best_distance` remains the historical best-Ghost promotion floor, preserving the existing receipt/manifest transaction and compatibility.
- a new `best_run_distance` preference stores the independent long-horizon run achievement;
- `GameStateManager.save()` publishes `distanceMetres` only for persistence-authorized runs;
- `SaveManager.loadBestDistance()`, used by Journal/Legacy, returns the maximum of the new run record and historical `best_distance`, so existing installations retain their prior displayed Best value without a destructive migration;
- Ghost artifact stores continue to read/write only their historical Ghost floor, so a pause/checkpoint cannot suppress a later best-Ghost promotion.

Regression coverage proves a persistent run with no Ghost advances the Journal-facing Best distance, stale saves cannot lower it, nonpersistent runs cannot publish it, and a run checkpoint can exceed the Ghost floor without changing that floor.

## Boundary

This separates two process-local SharedPreferences facts; it does not claim cloud synchronization. Ghost validity/recoverability remains independently governed by the receipt/manifest/AtomicFile pipeline, and the Journal-facing Best remains monotonic across both the legacy Ghost-backed value and the new independent run-achievement value.
