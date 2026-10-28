# Next actions

Current state is in [`MASTER_EXECUTION_LEDGER.json`](../MASTER_EXECUTION_LEDGER.json), prerequisites in [`TASK_DEPENDENCIES.json`](TASK_DEPENDENCIES.json), and revision-bound evidence in [`EVIDENCE_INDEX.json`](EVIDENCE_INDEX.json). The current candidate and exact-SHA results are recorded in [`source_completion_delta_2026-10-28.md`](../source_completion_delta_2026-10-28.md). Reuse earlier audits and successful evidence; do not repeat unchanged work.

1. **Physical-device performance — `TASK-B-001`–`TASK-B-002` (deferred).** Collect approved device-profile measurements, then analyze only measured profiles. Emulator results are not physical performance evidence.
2. **Human and external release evidence — `TASK-D-001`–`TASK-D-007` (deferred).** Obtain genuine device, human, provenance/licensing, privacy/security, signing, Play delivery and accountable governance records from their owners. Tooling may validate evidence but must not manufacture it.
3. **Residual readiness — `TASK-E-001` (deferred).** Reconcile requirement-level status and accountable go/no-go after prerequisite evidence exists or responsible owners explicitly disposition external gates.

`TASK-C-013` is complete for its exact-candidate hosted API35 screenshot scope (EVID-113): run `40610098124` passed host and connected jobs; 28 connected tests passed; all eight captures passed the raw-set verifier; `07-owl.png` visibly shows the Owl alert state and its sidecar binds the candidate, APK, device and scenario. This is not physical-device or human creative acceptance.

`TASK-C-014` is complete for source-delta verification: its 871467c2 source candidate passed exact-SHA host/release/lint/package and API35 connected jobs. The subsequent screenshot-helper correction was verified on `871467c2437a9949da6bf9c0119a31c405d7fa42`; 28 connected tests and eight screenshot captures were verified. Training-applicability and certificate workflows `40610098264` and `40610098198` passed (EVID-113).

The original static investigation's read-only restriction was superseded by the user's explicit source-completion and verification authorization. Historical local connected exit-143 attempts (EVID-106) remain recorded but are superseded for this exact candidate by successful hosted API35 evidence. Source tests and hosted emulator checks do not establish physical, human, legal, store or production-release acceptance.



Prior scenario source/test delta is at `e1e8501451d17996469fc82973076ed0d1fb9d22`; current accessibility provider correction and regression are committed at `bef4ab354a013892dd0d0289ec3616a20ef0d2d4`; integrated Python and Java21 full validation pass (EVID-140–142). Exact-SHA results from earlier candidates do not cover the new accessibility source delta. Exact-SHA Android host and API35 connected jobs passed; applicability and certificate runs passed (EVID-153). This candidate predates the new ghost assertion, so fresh exact-SHA verification is still required. The new uncommitted test delta requires a later exact-SHA run. TASK-C-018 remains open until terminal results are inspected. Prior audio evidence remains EVID-118/EVID-119. Physical-device access is unavailable, so B-001/B-002 remain blocked; D-001..D-007 require genuine human/external owner evidence; E-001 remains dependent on those gates and exact-candidate closure.


4. **Prior scenario delta — `TASK-A-039`–`TASK-A-045` locally complete.** It remains separately revision-bound by EVID-123, EVID-126–130, EVID-134–136. Its full authored scenario catalogue is still only partially dispositioned. The broader REQ-020 scenario matrix remains partial; do not infer complete mechanics coverage.

### Current scenario delta (2026-10-28)

- `CACTUS_READ` authored jumps were late; `DebugScenarioScript` timing is corrected and its real-path flora/tree test passes (5/5; EVID-126).
- Focused scenario integrations pass for five animal behaviors (5/5; EVID-127), Owl alert-to-dive and Chickadee seeded altitude (2/2; EVID-128), and Eagle mark/lock (1/1; EVID-123).
- Full Python (1,398) and Java21 Gradle (1,328) local suites pass (EVID-141/142); the bef4ab35 exact-SHA host passed while API35 connected job remained in progress at EVID-153. The broader 28-scenario catalogue remains only partially dispositioned. No physical device access is available.


5. **Accessibility subtree search — `TASK-A-046` locally complete.** Regression test and full local Python/Java21 Gradle gates pass (EVID-140–142). The committed host and API35 connected jobs passed at EVID-153; physical TalkBack review remains externally pending.

6. **Production ghost capture assertion — `TASK-A-047` locally complete.** AndroidTest source compiles and full local gates pass (EVID-147/148). Commit the focused test and synchronized docs; run exact-SHA API35 connected validation. Human/device visual acceptance remains separate.
