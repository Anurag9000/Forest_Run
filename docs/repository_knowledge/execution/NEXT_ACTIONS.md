# Next actions

Current state is in [`MASTER_EXECUTION_LEDGER.json`](../MASTER_EXECUTION_LEDGER.json), prerequisites in [`TASK_DEPENDENCIES.json`](TASK_DEPENDENCIES.json), and revision-bound evidence in [`EVIDENCE_INDEX.json`](EVIDENCE_INDEX.json). The current atlas correction and local evidence are documented in [`source_completion_delta_2026-10-29.md`](../source_completion_delta_2026-10-29.md). Reuse earlier audits and successful evidence; do not repeat unchanged work.

1. **Exact-commit source verification — `TASK-C-019` (awaiting verification).** Commit the reviewed TASK-A-050 source delta to authorized `main`, then inspect exact-SHA hosted source/Android workflow results and update evidence. Local checks are not transferable to the new SHA.
2. **Physical-device performance — `TASK-B-001`–`TASK-B-002` (deferred).** Collect approved device-profile measurements only when the required device access and candidate-specific protocol are available. Emulator evidence is not physical performance evidence.
3. **Human and external release evidence — `TASK-D-001`–`TASK-D-007` (deferred).** Obtain genuine device, human, provenance/licensing, privacy/security, signing, Play delivery and accountable governance records from their owners. Tooling may validate evidence but must not manufacture it.
4. **Residual readiness — `TASK-E-001` (deferred).** Reconcile requirement-level status and accountable go/no-go after prerequisite evidence exists or responsible owners explicitly disposition external gates.

The user reports no physical device access yet. Do not fabricate device or human evidence. The 28-scenario exact-candidate trace remains revision-bound to its existing evidence; the atlas delta does not claim every visual mechanic or scenario has physical/human acceptance.
