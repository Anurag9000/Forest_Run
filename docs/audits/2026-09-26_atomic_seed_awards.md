# Forest Run — Atomic Garden purchase and run Seed award (2026-09-26)

## Verified cross-layer race

The canonical `GameStateManager.addSeeds` previously computed the run-awarded balance by calling `SaveManager.loadLifetimeSeeds` and later `saveLifetimeSeeds`. Only the latter absolute write held SaveManager's Garden lock. `GardenPurchaseManager.purchaseNext` held a separate object monitor and committed a balance/progression transaction. A concurrent purchase between the award's read and write could therefore be overwritten by the stale award, effectively restoring spent Seeds; the reverse interleaving could drop earned Seeds. Neither a synchronized write alone nor the purchase's individual commit makes the two owners mutually serializable.

## Repair

The new `SaveManager.awardLifetimeSeeds` reads, saturating-adds and publishes the canonical balance under one shared `gardenWriteLock`; `GameStateManager` uses that operation instead of the split read/write. Canonical and legacy Garden purchases now enter that same lock before reading their balance and commit it with progression. The legacy `saveGardenProgress` already owns this reentrant lock and remains compatible. A pending legacy follow-up marker keeps its canonical balance updated when a real award arrives. No Garden cost, Bloom timer, Seed reward, menu state or external persistence schema changes.

The Robolectric concurrency regression invokes the actual `GameStateManager.addBonus` and `GardenPurchaseManager.purchaseNext` across two threads while the shared lock is held, proves neither can commit early, and checks the final 50 + 10 - 20 = 40 balance and progression. Existing Garden/Seed/recovery tests continue to cover other invariants. Verify the exact commit's JVM/connected workflow before marking automated closure.

## Boundary

This guards one application-process SharedPreferences mutation domain. It does not fabricate multi-process transactions, physical-device response-time evidence or a production release acceptance.
