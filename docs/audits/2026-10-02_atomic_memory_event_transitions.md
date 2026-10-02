# Forest Run — Atomic persistent-memory spare/hit transitions (2026-10-02)

## Defect

The derived-counter primitive was serialized, but one logical relationship-memory event still spanned several independent lock acquisitions. `recordSpare` incremented spared + kindness and then reset tenderness; `recordHit` incremented hits + tenderness, reset kindness and saved the last killer. A concurrent spare and hit could interleave so both kindness and tenderness ended at zero. No serial ordering of one spare and one hit can produce that state: the last event must leave its own streak positive and the opposite streak zero.

## Correction

Wrap each complete spare and hit transition in the existing reentrant progression lock. The public streak-reset primitives also acquire that lock so direct callers cannot race an increment. Existing nested increment calls remain safe because the JVM monitor is reentrant. History-unlock recomputation stays outside the transaction because it is derived observation rather than a primitive event write.

A two-worker Robolectric regression performs 200 spare and 200 hit events against one species and requires all event counts to survive and the final streak pair to match a legal serial ordering. This establishes in-process event atomicity only; Forest Run remains a single-process application.
