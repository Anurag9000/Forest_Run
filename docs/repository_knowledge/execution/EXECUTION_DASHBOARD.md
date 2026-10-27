# Forest Run execution dashboard

**Snapshot:** 2026-10-27. Canonical local `main` and `origin/main` are both `7870bc678fcc3412a45a44eaf820ac2685111899`. Source completion is at `d8afcd8ea8703043157abc1e08c3b9c6b2cfcb53`; `7870bc67` is the documentation reconciliation commit. The safe clone has one untracked generated `.training_control/coverage_report.json`, preserved and excluded from publication. The original local checkout at `eeccc677107f69039dbf71053c18a9038d4b5ff6` and its 170 pre-existing dirty paths remain preserved.

**Published current commit:** `7870bc678fcc3412a45a44eaf820ac2685111899` is on `origin/main`. The API35 rerun for that old SHA completed with host/release/lint/package success; ordinary connected tests passed 28/28, but process-death prepare and cleanup failed before the test body with `Invalid userId -2` (EVID-092). The d8 candidate showed the same root cause (EVID-090). The local `--user current` correction passes host tooling tests (EVID-091) but has not yet been published or run on the emulator.

**Latest source audit:** clean candidate `d1f31d473f5ca9303706164b6cc817703e0e7c7c` was re-audited across gameplay, scenarios, persistence, accessibility/audio/haptics, assets, release documentation and CI wiring; the exact-main follow-up found no source changes or new defect. See `source_completion_reconciliation_2026-10-27.md` / EVID-074 and `verification_delta_2026-10-27_exact-main.md` / EVID-076–EVID-087. Exact-main Android run `40471923390` completed successfully: host/release/lint/package and API 35 connected validation passed. This is hosted emulator evidence, not physical-device or human acceptance.

**Current phase:** Broader source-completion mandate from the newly read task specification. Existing deep audits and implementation history are being reused; parallel delta reviews cover lifecycle/recovery, creative assets/scenarios, and training-control/release applicability. The named OWL_DIVE store capture now receives a scheduled jump; visible screenshot state remains blocked pending a device.

## Project state

- Goals: 15; requirements: 19; capabilities: 16.
- Algorithm/method families: 4; principal execution paths: 3.
- Inventory: 1,007 records (951 fully statically inspected, 17 partially inspected, 36 documented, 3 excluded); 6,137 declaration records across 256 production paths.
- Findings: 18; established gaps: 8 (OWL_DIVE source gap resolved; screenshot appearance pending; API35 runner selector corrected locally but awaiting exact-candidate connected verification).
- Tasks: 59 total; 40 completed, 3 partially completed, 11 deferred, 5 canceled/superseded.
- Python suite: 1,388 passed. JVM/Robolectric suite: 1,297 passed, 0 failures/errors/skips. Debug/release lint and APK assembly passed on the merged candidate; see `verification_delta_2026-10-27.md` / `EVID-072`.
- Exact-main focused test run: 113 evidence-tooling Python tests and 129 selected JVM/UI tests plus 23 Player/frame-bound tests passed with 0 failures/errors/skips (`EVID-076`/`EVID-079`). C-009 and the automated/API35 scope of C-010 are completed; C-007 is complete at authored unit/property scope; C-008 remains partial for process-kill/restore durability. The exact d8 API35 failure is now diagnosed: both d8 and old 7870 runs passed 28 ordinary tests then failed before process-death prepare with `Invalid userId -2` (EVID-090/EVID-092). Local `--user current` correction passes 13 focused and 1,388 full Python tooling tests (EVID-091); exact connected verification of the corrected source is pending. C-008 remains partial.
- Focused C-012 checks on exact d1: 54 Python stable-file-reader tests and 29 targeted JVM audio/thread tests passed with 0 failures/errors/skips; hosted API 35 connected lifecycle tests passed (`EVID-075`). Exact-main hosted API 35 connected instrumentation passed (`EVID-078`). Local `adb` and a physical device are unavailable, so `OWL_DIVE` image-state and physical-device acceptance remain open. Human, security/privacy/licensing, signing, Play delivery, and accountable release gates remain external or unverified.
- All 29 runtime sprite sheets were visually reviewed; identical bird asset pairs and quality concerns are evidence for human creative/licensing review, not a confirmed source defect (`EVID-077`).
- No training/inference applies to this Android game. The exact-head hosted Android workflow is recorded above; no screenshot capture, store upload, or external approval was performed.
- The current bounded source audits remain static; hosted workflow evidence is revision-bounded above. The exact-7870 connected rerun and all physical-device, human, legal, store, and external gates remain separate.

## Next actions

1. When an authorized device and `adb` are available, run `TASK-C-013` against the exact candidate and inspect the candidate-bound `07-owl.png`.
2. Publish the locally tested `--user current` runner correction and regression assertion; inspect the new exact-SHA API35 logs for prepare, post-force-stop process absence, and verify success. Keep `TASK-C-008`/`C-011` partial until those results are known.
3. Complete performance and accountable external release tasks before `TASK-E-001` go/no-go.
4. Preserve source identity, state history, and the distinction between the isolated tested candidate and the original dirty checkout.

The machine-readable source of truth is `MASTER_EXECUTION_LEDGER.json`; task dependencies are in `TASK_DEPENDENCIES.json`; run evidence is in `EVIDENCE_INDEX.json`.
