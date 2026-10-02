# Forest Run — Serialize relationship-stage snapshots with progression events (2026-10-02)

## Defect

Relationship stage is a persisted projection of four mutable counters: encounters, clean passes, spares and hits. Positive outcomes can raise the stage while hits can reduce earned score and legitimately lower it. `refreshStage` previously read those counters and then wrote the cached stage outside the shared progression lock. Concurrent event updates could therefore publish a stage computed from a torn or stale counter snapshot after a newer event had already completed. `stageFor` trusts the saved value, so the drift could persist.

## Correction

Run the complete counter snapshot → stage computation → stage save → milestone unlock sequence under the existing reentrant progression lock. Counter loads and monotonic milestone-unlock writes are safe when nested on that JVM monitor. No relationship thresholds or narrative tuning change.

A Robolectric concurrency regression repeatedly races four Wolf spares against four hits after recognition setup, then requires the cached public stage to equal a fresh canonical recomputation. The test also propagates worker failures/timeouts rather than silently accepting incomplete races.
