# Forest Run — Monotonic union semantics for all permanent unlock sets (2026-10-02)

## Systemic finding

The history-mark race was not unique. Memory pages, relationship milestones and costumes are also permanent unlocks, and each storage method replaced the entire SharedPreferences string set supplied by a caller. Their owners all follow load → add newly discovered unlock(s) → save-whole-set patterns. Concurrent independent discoveries could therefore erase one another even though both unlock conditions were genuinely achieved.

The same audit also exposed a masked test-source defect: `SaveManagerConcurrencyTest` referenced `EntityType` without importing the production enum. Earlier exact-head Android validation had failed in Python before Kotlin test compilation, so that missing import had not yet surfaced.

## Correction

All four permanent-unlock families now use the existing reentrant progression lock and merge the caller set into the canonical stored set. Loaders copy SharedPreferences string sets before mapping/returning them. Costume persistence filters the sentinel `NONE` from durable unlocks, matching `CostumeManager`'s existing runtime treatment. The concurrency test imports the real entity/costume enums and drives two independent workers that unlock disjoint memory pages, relationship milestones and costumes; the final canonical state must retain each union.

This intentionally makes these APIs monotonic. They are unlock stores, not administrative replacement stores. No gameplay unlock requirement is relaxed and no unlock is fabricated.
