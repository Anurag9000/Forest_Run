# Forest Run — Atomic derived progression counters (2026-10-02)

## Defect

Forest Run's long-horizon derived counters—encounters, spares, hits, clean passes, kindness/tender streaks, route-tier totals and biome friendship—share `SaveManager.incrementInt`. The primitive previously performed an unlocked SharedPreferences read → increment → `apply()`. SharedPreferences makes each individual call thread-safe, but does not make that multi-call read-modify-write atomic. Two concurrent logical events could read the same value and both publish the same successor, silently losing one event.

The usual GameView path is predominantly single-threaded, but these counters are also consumed and mutated by recovery/lifecycle/persistence owners. Correctness should belong to the storage primitive rather than to an assumption about today's caller scheduling.

## Correction

Serialize `incrementInt` on the existing reentrant `gardenWriteLock`, whose documented purpose is the shared currency/progression mutation domain. The derived-counter ceiling and SharedPreferences representation are unchanged. Reentrancy preserves callers already inside that progression lock.

A Robolectric concurrency regression releases eight workers together, performs 1,000 total encounter increments through the public SaveManager API and requires the canonical counter to equal the full logical event count.

This protects in-process mutation. It does not claim multi-process SharedPreferences transactions; Forest Run is a single-process game and no multi-process writer is part of the supported architecture.
