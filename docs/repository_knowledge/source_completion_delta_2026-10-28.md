# Forest Run source-completion delta — 2026-10-28

## Repository identity and scope

- Checkout: `main`, `HEAD` and fetched `origin/main` both `946130c19878f99f1c9ee21506fb89e1ab940525`.
- The working tree contains pre-existing source/test/documentation changes and generated state that were preserved across the fast-forward. Nothing was reset, cleaned, or discarded. `.coverage`, `.training_control/`, and `artifacts/` remain untracked and are not release evidence or source changes.
- Host verification ran in `/var/tmp/forest-run-main-20261028-check`, a non-destructive copy of the same authored source snapshot. A checksum-aware dry-run compared that copy to the current checkout while excluding Git/Gradle/build/test outputs and showed no differing authored files; only directory metadata differed. Build outputs and test reports are retained in that temporary copy.
- The current changes are an uncommitted working-tree overlay on the stated baseline, not a frozen/published candidate. The clean `origin/main` workflow result described below applies only to the remote base commit.

## Source delta and static disposition

- **Wolf atlas/runtime mismatch:** the authored source atlas is a 4×2 layout with eight poses. The importer exports eight horizontal cells to `app/src/main/assets/sprites/animals/wolf_8frames.png`; SHA-256 is `bd9f60a4a85cec937b0df2c7709d2962ece8deda1cab246c71f77d4b2ec19225`. `AssetPaths.Animals.WOLF`, `SpriteManager`, importer output and tests now agree on eight frames. The previous four-frame conclusion is superseded. Static review found the path and frame count consistent; no claim of human animation approval or provenance is made.
- **Lifecycle/input delta:** `GameView` cancels active gesture/duck ownership when paused or when its Surface is destroyed, and gates gameplay input on lifecycle and Surface readiness. Focused Kotlin/source-contract regressions were added. An independent static review found no confirmed regression in the changed region; actual device behavior remains dependent on connected instrumentation and physical review.
- **Release docs:** `release/google-play/README.md` names `bash scripts/prepare_main_release.sh` as the supported operator entrypoint and describes `prepare_play_release.py` only as an internal helper. Its contract test guards operator-facing release documentation.
- **Python compatibility:** `verify_release_source_assets.py` handles Python runtimes without `ast.TypeAlias`; a regression test covers that compatibility path.
- **Training applicability:** tests guard the no-trainable-surface interpretation against ordinary game/release identifiers. No ML functionality or fake training jobs were added. The implementation is classified N/A to game training.
- The existing `release/google-play/BUILD_SUMMARY.md` is absent from current `main`; the README identifies summary output as generated candidate evidence. No stale checked-in approval summary was added.

## Verification evidence on the current source snapshot

- Full Python scripts suite:
  `PYTHONPATH=scripts TMPDIR=/var/tmp/forest-run-python-tmp-current20261028 COVERAGE_FILE=/var/tmp/forest-run-python-current20261028.coverage PYTHONDONTWRITEBYTECODE=1 python3.14 -m unittest discover -s scripts -p 'test_*.py'`
  **1,395 passed; exit 0; 18.379 seconds.**
- Android host suite/build in the copied snapshot: 1,303 JVM/Robolectric tests, 0 failures, 0 errors, 0 skipped. Debug and release compilation, instrumentation compilation, APK assembly, debug/release lint, and release AAB packaging completed across the recorded Gradle invocations. `lintRelease bundleRelease` explicitly reported `BUILD SUCCESSFUL` (Gradle 8.13, 51 tasks, 34 seconds); the resulting bundle is `app/build/outputs/bundle/release/app-release.aab` (6,353,528 bytes) and contains `base/assets/sprites/animals/wolf_8frames.png`. The debug APK and instrumentation APK were also produced. A separate sequential `lintDebug` rerun explicitly reported `BUILD SUCCESSFUL` (29 tasks; 7 executed, 22 up-to-date; 1m14s).
- Connected instrumentation has **not passed** on this local overlay. Two attempts ended with process exit 143 before test results: the first coincided with concurrent Gradle invocations and the second stopped during `compileDebugKotlin`; the AVD process/device then disappeared. No connected JUnit XML was produced. This is not evidence of a product-test assertion failure, but it is also not a passing connected test. The local disposable clone used API 36.1, whereas the permanent hosted workflow uses API 35.
- Exact remote `origin/main` API35 workflow run `40495156970` passed on commit `946130c19878f99f1c9ee21506fb89e1ab940525` (host/release/lint/package and API35 connected jobs). That is remote-base evidence only; it does not validate the uncommitted local overlay.
- An earlier unfiltered local emulator attempt on a different source copy had 38 tests: 32 passed and six failures associated with physical-profile sampling and low-frame-rate input latency on SwiftShader. The physical-only performance thresholds were not weakened. That older result is retained as environment-specific evidence, not used as current-candidate validation.

## Remaining source and acceptance work

- Obtain an exact-overlay connected result through a stable API35 runner or repository CI after a reviewed candidate exists; the current local emulator attempts produced no tests. Keep any runtime report candidate- and revision-bound.
- Reconcile the live inventory/symbol records and master map with the Wolf path and this verification delta. Dated 2026-10-27 audit artifacts remain historical and are not rewritten.
- The broad source-completion prompt remains open until every applicable feature/bug requirement has an evidence-backed disposition and the remaining source-addressable work is closed. This delta does not assert that status.
- Physical device/performance acceptance, candidate-bound store screenshots, human gameplay/accessibility/creative approval, asset provenance/licensing, production signing, Play delivery, privacy/security/store governance and accountable release approval remain external or device-dependent gates. No evidence was fabricated.

## Provenance and artifacts

The source tree and diff remain in the canonical checkout; verification outputs are retained under `/var/tmp/forest-run-main-20261028-check`, `/var/tmp/forest-run-python-current20261028.coverage`, `/var/tmp/forest-run-connected-current20261028.log`, and `/var/tmp/forest-run-connected-current-retry2-20261028.log`. The connected logs end before instrumentation results. No commit or push is represented by this report.
