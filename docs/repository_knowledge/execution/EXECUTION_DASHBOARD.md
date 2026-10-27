# Forest Run execution dashboard

**Snapshot:** 2026-10-28. The current checkout is `main` at `946130c19878f99f1c9ee21506fb89e1ab940525`, equal to fetched `origin/main`, with the preserved source overlay still uncommitted. `.coverage`, `.training_control/`, `artifacts/`, and prior untracked docs remain preserved and are excluded from the source batch unless explicitly reviewed. Current overlay status and evidence are in EVID-103–EVID-107 and `source_completion_delta_2026-10-28.md`.

**Published base commit:** `946130c19878f99f1c9ee21506fb89e1ab940525` is on `origin/main`; exact hosted workflow `40495156970` passed host/release/lint/package and API35 connected validation. This applies only to the clean remote base. Earlier process-death evidence for `b9a79b23` remains revision-bounded (EVID-101).

**Latest source audit:** clean candidate `d1f31d473f5ca9303706164b6cc817703e0e7c7c` was re-audited across gameplay, scenarios, persistence, accessibility/audio/haptics, assets, release documentation and CI wiring; the exact-main follow-up found no source changes or new defect. See `source_completion_reconciliation_2026-10-27.md` / EVID-074 and `verification_delta_2026-10-27_exact-main.md` / EVID-076–EVID-087. Exact-main Android run `40471923390` completed successfully: host/release/lint/package and API 35 connected validation passed. This is hosted emulator evidence, not physical-device or human acceptance.

**Current phase:** Reconcile and verify the newly surfaced source overlay. Wolf's eight-frame atlas/runtime correction, GameView lifecycle/input guard, Python compatibility and release/training applicability regression contracts have current host evidence. Local connected attempts terminated before assertions; `TASK-C-014` remains partial pending exact-candidate API35 hosted verification. Existing deep audits are reused. `TASK-C-013` and physical/human/external release tasks remain deferred.

## Project state

- Goals: 15; requirements: 19; capabilities: 16.
- Algorithm/method families: 4; principal execution paths: 3.
- Inventory: 1,009 records after the 2026-10-28 delta (951 fully statically inspected, 17 partially inspected, 38 documented, 3 excluded); 6,137 declaration records across 256 production paths at the earlier declaration-inventory snapshot. These are not a claim of new full inspection for every changed file.
- Findings: 20; 9 gap records, including the current resolved Wolf source-mapping correction (candidate verification pending), resolved documentation/hosted-runner gaps, and unresolved screenshot/device/performance/external acceptance items. `FIND-012` is retained as a superseded historical conclusion; `FIND-020`/`GAP-009` describe current Wolf state.
- Tasks: 60 total; 43 completed, 11 deferred, 5 canceled/superseded, 1 partially completed (`TASK-C-014`). Canceled/superseded records preserve prior-scope history.
- Current overlay Python suite: 1,395 passed. JVM/Robolectric suite: 1,303 passed, 0 failures/errors/skips. Debug/release lint and APK/AAB packaging completed; see `source_completion_delta_2026-10-28.md` / `EVID-104`–`EVID-105`.
- Exact-main focused test run: 113 evidence-tooling Python tests and 129 selected JVM/UI tests plus 23 Player/frame-bound tests passed with 0 failures/errors/skips (`EVID-076`/`EVID-079`). C-009 and the automated/API35 scope of C-010 are completed; C-007 is complete at authored unit/property scope; C-008 and C-011 are complete at their documented hosted/API35 regression scopes (EVID-101/EVID-102); physical/OEM behavior and external gates remain separate. Older candidates exposed successive runner invocation and install-state defects before process-death execution (EVID-090–EVID-100). Exact candidate `b9a79b23` passed APK reinstalls, registered-runner discovery, process-death prepare, host-confirmed no-PID after force-stop, and fresh-process restoration on API35; 28 ordinary connected tests passed (EVID-101). C-008 is complete for hosted-emulator scope; physical/OEM verification remains separate.
- Focused C-012 checks on exact d1: 54 Python stable-file-reader tests and 29 targeted JVM audio/thread tests passed with 0 failures/errors/skips; hosted API 35 connected lifecycle tests passed (`EVID-075`). Exact-main hosted API 35 connected instrumentation passed (`EVID-078`). Local `adb` and a physical device are unavailable, so `OWL_DIVE` image-state and physical-device acceptance remain open. Human, security/privacy/licensing, signing, Play delivery, and accountable release gates remain external or unverified.
- All 29 runtime sprite sheets were visually reviewed; identical bird asset pairs and quality concerns are evidence for human creative/licensing review, not a confirmed source defect (`EVID-077`).
- No training/inference applies to this Android game. The exact-head hosted Android workflow is recorded above; no screenshot capture, store upload, or external approval was performed.
- The bounded source audits remain static; hosted workflow evidence is revision-bounded. Exact b9a79b23 hosted evidence demonstrates the corrected runner. The exact-4845, eb326dd0, 323320b5 and facd8c06 failures are retained as historical evidence for successive candidate defects. The current local API36.1 connected attempts ended before assertions (EVID-106); no current-overlay connected pass is claimed. Physical-device, human, legal, store, and external gates remain separate.

## Next actions

1. Finalize current ledger/docs and commit only the reviewed intended source overlay to `main`, preserving generated/user artifacts.
2. Run/inspect exact-SHA hosted host and API35 connected workflow for that committed source candidate; update `TASK-C-014` from actual run evidence, not local exit 143.
3. When an authorized device and `adb` are available, run `TASK-C-013` against the exact candidate and inspect candidate-bound `07-owl.png`.
4. Complete physical performance and external release tasks before `TASK-E-001` go/no-go.
5. Preserve source identity, state history, and the distinction between the isolated tested candidate and earlier dirty-checkout snapshots.

The machine-readable source of truth is `MASTER_EXECUTION_LEDGER.json`; task dependencies are in `TASK_DEPENDENCIES.json`; run evidence is in `EVIDENCE_INDEX.json`.
