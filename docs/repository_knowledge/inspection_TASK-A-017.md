# TASK-A-017 — Operational scripts, build and repository controls

**Snapshot:** `eeccc677107f69039dbf71053c18a9038d4b5ff6` plus dirty worktree, 2026-10-27. **Disposition:** complete static inspection of 42 assigned paths; 41 text files read in full and Gradle wrapper JAR inspected via archive metadata only. The assigned 42 paths were clean against baseline HEAD. No workflow, script, build, test, app/device action, or network operation ran.

## File scope and reuse

Exact paths are the `TASK-A-017.scope` array in `MASTER_EXECUTION_LEDGER.json` and their per-file status in `file_inventory.json`. Reused the 2026-10-03 `REPO_CODEBASE_MAP.md` on compatible baseline paths and targeted its sections 96–104, 663–673, 683, 687, 699, 717–723; also reused `AUDIT_LEDGER.md:203-208,420-438` and named device/input-latency contracts. This is static implementation evidence, not current execution evidence.

## Build and repository contract

- `.github/workflows/android-validation.yml:1-416` pins event SHA checkout and read-only contents permission, cancels superseded refs, executes host source guards, dependency inventory/SBOM, compilation/JVM/lint/package/R8/page-size checks, then a dependent API-35 emulator connected job; artifact retention is declared. This workflow definition was not invoked.
- Training control workflows use exact-SHA checkout, Python 3.11, syntax/control audit and not-applicable certification; no workflow was invoked. `training_control/forest_no_trainable_authority.py:1-460` statically constrains the app surface and emits an applicability certificate, not a training result. Historical scanner execution does not certify the current dirty tree.
- Root Gradle config declares AGP 8.13.2 and Kotlin Android 1.9.22; settings constrain repositories and include only `:app`; wrapper properties select Gradle 8.13. IDE `.idea/kotlinc.xml` says Kotlin 2.0.21 while Gradle is authoritative for builds. This is an editor/build-version discrepancy, not a demonstrated build defect.
- Wrapper scripts are standard Unix/Windows launchers. `gradle-wrapper.jar` hash is `e996d452d2645e70c01c11143ca2d3742734a28da2bf61f25c82bdc288c9e637`; payload was not inspected. Ignore rules exclude build outputs, local signing material and generated evidence.

## Operational and evidence mechanisms

The scoped scripts cover source-bound device acceptance aggregation, compilation and validation; candidate-bound input-latency/performance collection; strict JSON and stable file evidence; connected validation; origin-main checks; CI dependency inventories/SBOM; and release evidence. `stable_file_reader.py` and `strict_json.py` were already mapped; wrappers and consumers retain static contracts for exact candidate identity, bounded intake, staged/atomic publication, validation and rollback. Performance collectors have no measurements here. Device/candidate tooling requires real device evidence and exact candidate binding. These sources define mechanisms; none was executed.

`acceptance_test_support.py` is fixture-only and consumed by script test sources. `verify_origin_main.sh` has a network/Git effect and was not invoked. `run_connected_validation.sh` enforces optional origin/main and clean-tree identity then invokes connected Android tests; not invoked.

## Privacy, security, release, assets

`PRIVACY.md:1-61` declares offline/local-only behavior and identifies policy publication as a release obligation; this assignment did not independently prove app permissions/network behavior. `SECURITY.md:1-43` describes source-ready-alpha posture and unresolved reporting, signing, dependency, art and policy gates; no external channel was validated. `asset-provenance.json:1-55` marks five asset families review-required with provenance/reviewer fields blank. `CHANGELOG.md:1-55` prohibits dated release notes absent an accepted signed candidate; historical run `31297723150` at `414bf30...` does not prove current HEAD. Requirements files pin Pillow 11.3.0.

## Future obligations and limits

The no-trainable-surface statement is scoped to source controls; no current audit certificate was generated. Physical measurements, release signing/Play, human/device acceptance, creative rights, privacy/policy, external vulnerability reporting and current candidate status need their accountable evidence. These are release/external gates, not automatic code tasks. No confirmed source defect was established. Static review leaves potential implementation-fidelity questions to future authorized verification and does not claim runtime success.
