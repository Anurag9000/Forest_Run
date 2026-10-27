# Next actions

Current evidence and task state are in `MASTER_EXECUTION_LEDGER.json`; use `EVID-072` for the full local test/build results, `EVID-074` for the bounded source re-audit, `EVID-075` for focused concurrency checks, and `EVID-076`–`EVID-087` for exact-main focused tests, asset visual review and hosted Android CI. `TASK_DEPENDENCIES.json` contains prerequisites. Reuse prior static audits and do not repeat them absent a source/evidence delta.

1. **Completed source delta:** `TASK-A-035` adds the scheduled `TAP_JUMP` at 1.80 seconds to `OWL_DIVE`, with Kotlin and cross-layer Python/shell regression contracts. Full suites and local lint/APK builds pass on the merged candidate.
2. **Blocked device proof:** `TASK-C-013` must capture and inspect `07-owl.png` on an authorized Android device; `adb` is currently unavailable. No screenshot has been fabricated.
3. **Targeted integration reconciliation:** `TASK-A-036`, `GAP-008`, and `TASK-C-008` are closed for the tested hosted API35 scope by EVID-101. `TASK-C-011` is complete: exact-source delta and requirement/evidence cross-check found no residual integration mismatch (EVID-102).
4. **External closure:** retain `TASK-C-013` for candidate-bound OWL_DIVE capture; `TASK-B-001`–`TASK-B-002` for physical performance measurement; `TASK-D-001`–`TASK-D-007` for human/device/security/privacy/licensing/signing/Play gates (including asset concerns under D-002/D-003); and `TASK-E-001` for final candidate-bound go/no-go after those gates.
5. **Completed connected candidate:** exact `b9a79b23a0cb855ce80c783e94e4b1b44eb0b94b` run 40492450144 succeeded, including APK reinstalls, ordinary API35 tests, process-death prepare, no-PID confirmation, fresh-process restore, and artifact/source checks (EVID-101). Do not repeat unchanged connected tests.
6. **Source provenance:** verification used the detached `origin/main` base plus reviewed source changes, retaining the newer upstream no-training authority and tests. The original dirty checkout and its differing local test file remain preserved and were not overlaid.

The original static investigation's read-only restrictions are historical; later user authorization enabled source completion and applicable tests. External release, device capture, store upload and human approvals remain separate gates.
