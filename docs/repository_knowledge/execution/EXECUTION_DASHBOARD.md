# Forest Run execution dashboard

**Snapshot:** 2026-10-28. Canonical `main`/`origin/main` are at `8f9340d431f7abb84719aef0a5b1174cafb0c437` before the owned local source/test/docs delta. The delta corrects CACTUS_READ scripted jump timing and adds targeted scenario integration tests. Full local Python (1,398 tests) and Java21 Gradle compile/test/lint/package (1,320 tests) passed, EVID-129/EVID-130. Exact-candidate hosted Java21/API35 validation remains pending under TASK-C-018. Physical-device access is unavailable; human, legal, store, governance and release gates remain open. Pre-existing user/generated untracked state is preserved and excluded.

**Current phase:** Scenario delta tasks TASK-A-039–043 are locally complete with focused and full-suite evidence. TASK-C-018 is ready for exact-SHA hosted verification after commit/push. Remaining work includes the broader scenario catalogue, physical/external gates and residual readiness reconciliation.

## Project state

- Goals: 15; requirements: 20; capabilities: 16.
- Algorithm/method families: 4; principal execution paths: 3.
- Inventory: 1,016 records (972 fully statically inspected, 2 partial, 39 documented, 3 excluded); symbol inventory: 6,141 declaration records across 256 production paths, including the four WOLF source delta declarations. These counts do not imply every symbol has complete call-graph tracing. These are not a claim of new full inspection for every changed file.
- Findings: 25; gaps: 14. GAP-010 records the WOLF_CHARGE correction; GAP-011 records removal of the stale Duck SFX comment. Audio payload and wiring evidence is EVID-118/EVID-119. Physical/human/external acceptance remains open. `FIND-012` is retained as superseded historical interpretation; `FIND-020`/`GAP-009` represent the current 8-frame mapping and its exact-candidate verification.
- Tasks: 71 total: 53 completed, 2 in progress, 1 not started, 10 deferred, 5 canceled/superseded. TASK-C-016 tracks the prior 8f exact-head run (host passed; API35 remains in progress at the latest public status poll); TASK-C-017 was the EAGLE_MARK exact-delta task and TASK-C-018 now tracks the expanded local scenario delta and exact candidate. All deferred tasks are physical/external gates or final readiness reconciliation; current status is in `MASTER_EXECUTION_LEDGER.json`. Canceled/superseded records preserve prior-scope history.
- Current WOLF delta local evidence (EVID-114): Python suite 1,398 passed and JDK 21 JVM/Robolectric suite 1,307 passed, 0 failures/errors/skips; compile, debug/release lint, APK/AAB packaging succeeded. Exact source candidate 113a4eb then passed host/release/lint/package and API35 connected jobs in run 40620167730; 28/28 connected tests passed, process-death restore passed, and eight raw screenshots verified (EVID-115/EVID-116). Earlier counts remain tied to their named revisions.
- Exact-main focused test run: 113 evidence-tooling Python tests and 129 selected JVM/UI tests plus 23 Player/frame-bound tests passed with 0 failures/errors/skips (EVID-076/EVID-079). C-009 and automated/API35 scope of C-010 are completed; C-007 is complete at authored unit/property scope; C-008 and C-011 are complete at their documented hosted/API35 regression scopes (EVID-101/EVID-102). Physical/OEM behavior and external gates remain separate. Exact candidate `b9a79b23` passed APK reinstalls, runner discovery, process-death prepare, host-confirmed no-PID after force-stop, and fresh-process restoration on API35; 28 ordinary connected tests passed (EVID-101).
- Focused C-012 checks on exact d1: 54 Python stable-file-reader tests and 29 targeted JVM audio/thread tests passed with 0 failures/errors/skips; hosted API35 lifecycle tests passed (EVID-075). Exact-main hosted API35 connected instrumentation passed (EVID-078). Local `adb` and a physical device remain unavailable, so `OWL_DIVE` image-state and physical-device acceptance are open. Human, security/privacy/licensing, signing, Play delivery, and accountable release gates remain external or unverified.
- All 29 runtime sprite sheets were visually reviewed; identical bird asset pairs and quality concerns are evidence for human creative/licensing review, not a confirmed source defect (EVID-077).
- No training/inference applies to this Android game. Exact-candidate no-trainable-surface and certificate workflows passed. The hosted API35 screenshot capture is verified in EVID-113. No physical-device acceptance, store upload, or external approval is claimed.
- Earlier local API36.1 SwiftShader connected attempts ended before assertions (EVID-106); exact candidate hosted API35 success EVID-109 supersedes them for that candidate. Physical-device, human, legal, store and external gates remain separate.

## Next actions

1. Do not wait indefinitely on predecessor run `40623906250` (still `in_progress` at the latest public status check); record it as unresolved historical evidence. Review and commit the owned scenario delta, then verify the exact candidate through TASK-C-018.
2. Physical performance/device tasks `TASK-B-001`–`TASK-B-002` cannot proceed here because no physical device is available. Human/external release tasks `TASK-D-001`–`TASK-D-007` require genuine owner evidence.
3. Perform residual readiness reconciliation under `TASK-E-001` only after its prerequisites exist; do not repeat completed audits without changed code or contradictory evidence.

The machine-readable source of truth is `MASTER_EXECUTION_LEDGER.json`; task dependencies are in `TASK_DEPENDENCIES.json`; revision-bound evidence is in `EVIDENCE_INDEX.json`.


## WOLF_CHARGE source delta (committed and exact-SHA verified)

TASK-A-037 and TASK-C-015 are complete at their recorded source/local/hosted scopes. WOLF_CHARGE fingerprints eight run-local mercy hearts, scripts both jump evasions, and its integration test observes both Wolves in HOWLING, CHARGING and SPARED. Local full Python (1,398) and JVM/Robolectric (1,307) suites pass; exact-SHA host and API35 validation passed, including 28/28 connected tests, process-death restoration, and eight screenshot checks (EVID-114–EVID-116). Physical/human acceptance remains external.


The preceding exact-candidate historical summary is retained in source-completion records; the current source revision and task counts above supersede earlier dashboard snapshots. Remaining deferred tasks are physical-device performance and external/human release gates, followed by residual readiness reconciliation.


## EAGLE_MARK delta

`EagleScenarioIntegrationTest` drives the actual authored scenario and confirms both Eagle target announcements and locks. Focused test EVID-123 and full Python suite EVID-124 pass; broad local JVM command is blocked by Java 17 where Robolectric SDK36 requires Java21 (EVID-125). The exact candidate remains unverified until TASK-C-017 completes.

### Current scenario delta (2026-10-28)

- `CACTUS_READ` authored jumps were late; `DebugScenarioScript` timing is corrected and its real-path flora/tree test passes (5/5; EVID-126).
- Focused scenario integrations pass for five animal behaviors (5/5; EVID-127), Owl alert-to-dive and Chickadee seeded altitude (2/2; EVID-128), and Eagle mark/lock (1/1; EVID-123).
- Full Python (1,398) and Java21 Gradle (1,320) local suites pass (EVID-129/130); exact-candidate Java 21/API35 workflow remains pending under `TASK-C-018`; the broader 28-scenario catalogue remains only partially dispositioned. No physical device access is available.
