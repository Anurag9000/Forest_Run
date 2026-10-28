# Forest Run source-completion delta — 2026-10-28

## Repository identity and scope

- Verified source candidate: commit `8c335aef232ee8f164847631a74d1abb7409dd86`, pushed to canonical `main` after baseline `946130c19878f99f1c9ee21506fb89e1ab940525`; the exact-SHA workflow ran on this source candidate.
- The reviewed source, test and documentation delta was committed and pushed to canonical `main`. The tracked worktree is clean. Pre-existing untracked `.coverage`, `.training_control/`, `artifacts/`, `docs/REPOSITORY_COMPLETE_KNOWLEDGE_MAP_20261027.md`, and `docs/repository_knowledge/current_worktree_delta_20261027/` remain preserved and outside the commit.
- Host tests/build outputs were produced in `/var/tmp/forest-run-main-20261028-check`, a checksum-matched copy of the authored source snapshot. Its checksum-aware comparison found no authored-file differences from the candidate.

## Source delta and static disposition

- **Wolf atlas/runtime mismatch:** the authored source atlas is a 4×2 layout with eight poses. The importer emits eight horizontal cells to `app/src/main/assets/sprites/animals/wolf_8frames.png`; SHA-256 is `bd9f60a4a85cec937b0df2c7709d2962ece8deda1cab246c71f77d4b2ec19225`. `AssetPaths.Animals.WOLF`, `SpriteManager`, importer output, tests, docs and packaged asset now agree on eight frames. The earlier four-frame conclusion is superseded. This does not establish human animation approval or provenance.
- **Lifecycle/input delta:** `GameView` cancels active gesture/duck ownership when paused or when its Surface is destroyed, and gates gameplay input on lifecycle and Surface readiness. Focused Kotlin/source-contract regressions were added. An independent static review found no confirmed regression in the changed region.
- **Release docs:** `release/google-play/README.md` names `bash scripts/prepare_main_release.sh` as the supported operator entrypoint and describes `prepare_play_release.py` only as an internal helper. Contract testing protects operator-facing release documentation.
- **Python compatibility:** `verify_release_source_assets.py` handles Python runtimes without `ast.TypeAlias`; a regression test covers the compatibility path.
- **Training applicability:** the no-trainable-surface checks guard against false interpretation of ordinary game/release identifiers. No ML functionality or fake training jobs were added; training remains N/A for this game.
- The old checked-in `release/google-play/BUILD_SUMMARY.md` is absent on current main; the release README identifies summary output as generated candidate evidence.

## Exact-candidate verification

- Full Python scripts suite on the checksum-matched source copy: **1,395 passed**, exit 0, 18.379 seconds (EVID-104).
- Android host suite/build on the same copy: **1,303 JVM/Robolectric tests**, zero failures/errors/skips; Kotlin compilation, debug/release lint, debug/instrumentation APK and release AAB packaging succeeded. `lintRelease bundleRelease` and a sequential `lintDebug` invocation explicitly reported `BUILD SUCCESSFUL` (EVID-105).
- Exact source candidate `8c335aef232ee8f164847631a74d1abb7409dd86` hosted Android workflow [40602123824](https://github.com/Anurag9000/Forest_Run/actions/runs/40602123824) completed successfully. Host/release/lint/package job `122532575053` and API35 connected job `122534456541` both passed; the connected step runs ordinary instrumentation and the repository's process-death prepare/force-stop/no-PID/restore flow. Uploaded artifacts: host artifact `12778759135` (17,810,799 bytes) and connected artifact `12779308471` (713,891 bytes).
- Exact-SHA no-trainable-surface audit [40602123869](https://github.com/Anurag9000/Forest_Run/actions/runs/40602123869) and estate certificate workflow [40602123856](https://github.com/Anurag9000/Forest_Run/actions/runs/40602123856) also completed successfully.
- Documentation-only descendant `f00f78e820ae2a158ef420d634f7c410a27ef2b3` then passed run [40604093279](https://github.com/Anurag9000/Forest_Run/actions/runs/40604093279) on exact SHA: host job `122539158093` (7m05s) and API35 connected job `122541808355` (7m31s) both succeeded. The uploaded host artifact `12780112471` is 17 MB with digest `1e7998dd14059596cb3bc4fcf51089350f386d8f44c40e38053a471ac1e71a7a`; connected artifact `12780856634` is 661 KB with digest `458808cf3482b8af9de9a77cf1a4b90b08305cb912920364bd982d92b874d`. Training applicability/certificate workflows `40604093598` and `40604093353` passed on the same tree. Application source is unchanged from `8c335aef` (EVID-111/EVID-112).
- Detailed hosted artifacts could not be downloaded from this environment: GitHub's artifact endpoint returned HTTP 401 without authenticated access. Therefore this record uses the directly observable exact-SHA job/step conclusions and artifact metadata; it does not invent test counts from those remote artifacts. The local exact-source Python and JVM counts above remain independently recorded.
- Previous local connected attempts on API36.1 SwiftShader exited 143 before assertions (EVID-106); they are historical environment failures and are superseded for the exact candidate by successful hosted API35 evidence EVID-109.

## Remaining work and limits

- `TASK-C-014` is complete for the exact source delta: candidate identity and all required hosted host/API35 connected outcomes are verified (EVID-108–EVID-112).
- Candidate-bound `OWL_DIVE` screenshot capture and visual inspection remain under `TASK-C-013`; no authorized device/adb was available locally. Physical performance/device acceptance, human gameplay/accessibility/creative approval, provenance/licensing, signing, Play delivery, privacy/security/store governance and accountable release approval remain external/device-dependent gates. They must be satisfied with real evidence.
- This delta does not claim all physical, human, legal, store or production-release acceptance is complete.

## Provenance

Verified source candidate: `8c335aef232ee8f164847631a74d1abb7409dd86`; host copy and Python coverage: `/var/tmp/forest-run-main-20261028-check` and `/var/tmp/forest-run-python-current20261028.coverage`; prior local connected logs: `/var/tmp/forest-run-connected-current20261028.log` and `/var/tmp/forest-run-connected-current-retry2-20261028.log`. Hosted run and artifact IDs are listed above and in `execution/EVIDENCE_INDEX.json`.


### Subsequent exact-head capture correction and verification (2026-10-28)

Commit `871467c2437a9949da6bf9c0119a31c405d7fa42` fixes the screenshot helper's fullscreen-prompt bounds parsing after the first hosted attempt exposed a Python syntax failure. A regression contract executes the embedded helper against valid and malformed bounds. The full Python scripts suite passed 1,398 tests locally; shell syntax and `git diff --check` passed. Exact-SHA Android run [40610098124](https://github.com/Anurag9000/Forest_Run/actions/runs/40610098124) passed host/release/lint/package and API35 connected jobs. The connected JUnit report passed 28/28 tests. The canonical screenshot workflow captured eight images and the raw-set verifier confirmed candidate `871467c…`, APK SHA-256 `0d51293fc8a544f891a9091097dfb79c23354afb65f7ed5ca6ea7c8e180a0806`, and device `emulator-5554`. The `07-owl.capture.json` sidecar identifies `OWL_DIVE` and its readiness marker; inspected `07-owl.png` visibly shows Owl alert rings (image SHA-256 `59c017ff6ea79716448789173b07bc082e91e572cb447f8e95d644b09b363895`). Downloaded artifact digest matches GitHub metadata. Training applicability and estate-certificate workflows [40610098264](https://github.com/Anurag9000/Forest_Run/actions/runs/40610098264) and [40610098198](https://github.com/Anurag9000/Forest_Run/actions/runs/40610098198) passed.

This closes `TASK-C-013` for hosted API35 candidate capture and visible-state evidence. It does not establish physical/OEM presentation, human gameplay/accessibility/creative acceptance, store curation, provenance/licensing, or production release. Those remain under the external B/D/E tasks.
