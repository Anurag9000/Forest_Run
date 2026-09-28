# Forest Run — Best distance is independent run memory, not a Ghost side effect (2026-09-28)

## Cross-layer defect

The Journal's Run Legacy projection reads `SaveManager.loadBestDistance()`, but the normal terminal path previously advanced the same `best_distance` key only inside asynchronous best-Ghost promotion. `RunOutcomePersistenceCoordinator` attempts that promotion only when the completed distance beats the Ghost floor **and** `completedGhost.isNotEmpty()`. A legitimate persistent run with no accepted Ghost therefore could improve distance without improving the Journal's Best distance. This violated the authored long-horizon-memory contract, which lists high score and best distance independently.

Simply adding another writer would have introduced a race: the namespace-bound Ghost worker previously wrote an exact distance value directly to SharedPreferences. A stale accepted Ghost transaction could then overwrite a newer synchronous run best with a smaller value.

## Repair

`GameStateManager.save()` now publishes its achieved `distanceMetres` through the existing monotonic SaveManager distance owner whenever run progress is persistent. Nonpersistent/debug runs remain isolated. SaveManager now exposes an internal namespace-bound, durable monotonic distance operation for Ghost transactions; both Ghost artifact adapters use it rather than raw exact preference writes. Consequently a Ghost write reports success when the requested floor is already met, but can never lower a newer best distance. Invalid Ghost distance candidates fail closed.

Regression coverage proves: a persistent run with no Ghost promotion advances Best distance; an older run cannot lower it; a nonpersistent run cannot publish it; and a stale namespace-bound Ghost transaction cannot roll back a newer independently published achievement.

## Boundary

This is process-local monotonic SharedPreferences persistence. It does not turn best-distance memory into cloud synchronization, nor does it make a failed Ghost artifact healthy. Ghost validity and recoverability remain independently governed by their receipt/manifest transaction.
