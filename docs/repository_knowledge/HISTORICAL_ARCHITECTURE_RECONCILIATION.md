# Historical architecture reconciliation

**Snapshot:** `eeccc677107f69039dbf71053c18a9038d4b5ff6` plus current dirty source inspected 2026-10-27. See evidence records `EVID-019`–`EVID-021` and finding `FIND-006`.

| IDs | Aug 6 status | Later disposition | Current source evidence |
|---|---|---|---|
| `GOAL-008` / `REQ-011` / `CAP-009` | Collision dispatcher open | Aug 9 §3.1 closes typed exhaustive dispatcher | `CollisionOutcomeDispatcher.dispatch`; GameView consumes typed result then uses session event |
| `GOAL-009` / `REQ-012` / `CAP-010` | Private live-effect adapters open | Aug 9 §3.2 closes shared adapter | `LiveCollisionEffects` maps both coordinator ports to callbacks |
| `GOAL-010` / `REQ-013` / `CAP-011` | Run-session sequencing open | Aug 9 §3.3 closes planner/coordinator/state publication path | Planner, coordinator and `GameView.applyRunSessionEvent` present |
| `GOAL-011` / `REQ-014` / `CAP-012` | Persistence facade open as architecture | Aug 9 §3.4 closes application facade; separate durability domains remain | `ApplicationPersistenceFacade` methods/factory and current consumers |
| `GOAL-012` / `REQ-015` / `CAP-013` | Structural catalogue open/optional | Aug 9 §3.5 closes canonical 19-family structural catalogue | `EncounterFamilyCatalogue`; dynamic properties remain in entity/SpriteManager authorities |
| `GOAL-013` / `REQ-016` / `CAP-014` | Player recovery UI optional/open | Aug 9 §3.6 closes bounded recovery UI | Controller revalidates; dialog wires safe retry and confirmed discard through MainActivity |
| `GOAL-014` / `REQ-017` | Broader data-driven authoring optional | No later evidence makes it mandatory | Distinct from the structural catalogue; not selected and no task is created |

Sep 20 closure states source/repository closure at `7757b40b7e87b3d33412f9a070c068e92f62ccf2`; Sep 27 is a source-specific delta at `505d3679dc8d55b2ccb8413b6a8e3d1bde5886e1`. These historical validations are not claims about the current runtime.

No code, test, build, emulator, device or release action was performed. Current-source review supports static implementation/integration only.
