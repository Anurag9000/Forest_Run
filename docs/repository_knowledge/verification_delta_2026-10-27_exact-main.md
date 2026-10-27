# Exact-main verification and visual asset delta — 2026-10-27

## Snapshot and scope

This addendum records verification and visual review against clean `main` / `origin/main` at `1d1a73451ad59e088d4117216d07162628913ff8`. The branch was clean before this documentation update. This revision is documentation-only relative to `d1f31d473f5ca9303706164b6cc817703e0e7c7c`; source, test, workflow, and runtime assets did not change between those revisions. Prior exact-head hosted evidence is therefore reusable, and a fresh, focused local test selection was also run at 1d1a.

The user's source-completion authorization permits targeted test-source work. No production application source was modified. The process-death instrumentation test and connected driver described below are newly added follow-up changes. No training, inference, benchmark, Play upload, signing, or physical-device workflow was performed.

## Focused tests at exact main

Environment: Linux host, Python 3.14.4, Java 21.0.9 from `/opt/android-studio/jbr`, Android SDK 36 configured through `ANDROID_HOME` and `ANDROID_SDK_ROOT`. Exact source revision: `1d1a73451ad59e088d4117216d07162628913ff8`.

| Scope | Command / selection | Result | Limit |
|---|---|---:|---|
| Evidence-tooling safety and publication | `PYTHONPATH=scripts python3 -m unittest test_stable_file_reader test_strict_json test_verify_raw_screenshot_set test_publish_device_acceptance_aggregate test_publish_screenshot_capture_set` | 113 tests passed | Authored Python tests only; does not establish external device, filesystem, or publication-service behavior. A diagnostic JSON line was emitted by a negative test; process exit was 0. |
| Gameplay, persistence, and UI JVM/Robolectric focus | Exact Gradle selection below | 129 tests passed; 0 failures, errors, or skips across 25 XML reports | Java 21 was required for Robolectric SDK 36. The UI wildcard covers UI JVM tests, not a human TalkBack/Switch Access evaluation. |

Gradle command and full test selection (the shell had `ANDROID_HOME` and `ANDROID_SDK_ROOT` set to the installed Android SDK 36 location; that user-specific path is omitted):

```sh
JAVA_HOME=/opt/android-studio/jbr \
PATH=/opt/android-studio/jbr/bin:$PATH \
JAVA_TOOL_OPTIONS='-XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=512m' \
./gradlew --no-daemon --max-workers=2 testDebugUnitTest --rerun-tasks \
  --tests com.anurag9000.forestrun.engine.InputHandlerTest \
  --tests com.anurag9000.forestrun.engine.CollisionOutcomeDispatcherTest \
  --tests com.anurag9000.forestrun.engine.BloomEconomyTest \
  --tests com.anurag9000.forestrun.engine.BloomTimerPartitionPropertyTest \
  --tests com.anurag9000.forestrun.engine.SaveIntegrityStartupGateTest \
  --tests com.anurag9000.forestrun.engine.SaveIntegrityManagerTest \
  --tests com.anurag9000.forestrun.engine.SaveIntegrityNamespaceAdversarialTest \
  --tests com.anurag9000.forestrun.engine.ForestJournalReadOnlyTest \
  --tests 'com.anurag9000.forestrun.ui.*'
```

The first targeted Gradle attempt used the host's default Java 17 and did not execute test bodies successfully: Robolectric failed to create the SDK 36 sandbox (`Android SDK 36 requires Java 21`). It is an environment/toolchain mismatch, not an assertion failure. The rerun above used Java 21 and passed. No suite result is attributed to the failed attempt.

Together with existing evidence, exact-main coverage includes full Python and JVM suites/build/lint from EVID-072 (source payload unchanged), exact-head hosted Android workflow 40471923390 (host/release/lint/package and API 35 connected jobs all succeeded), and the focused results above. Hosted CI evidence is emulator evidence; it does not establish physical-device or human acceptance.

### Movement-bound follow-up for TASK-C-007

The initial focused selection left a concrete question about movement bounds. A targeted follow-up ran the already-authored contracts on the same exact main revision:

```sh
JAVA_HOME=/opt/android-studio/jbr \
PATH=/opt/android-studio/jbr/bin:$PATH \
ANDROID_HOME="$ANDROID_HOME" \
ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT" \
JAVA_TOOL_OPTIONS='-XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=512m' \
./gradlew --no-daemon --max-workers=2 testDebugUnitTest --rerun-tasks \
  --tests com.anurag9000.forestrun.engine.FrameInputAdmissionTest \
  --tests com.anurag9000.forestrun.entities.PlayerBoundaryTest \
  --tests com.anurag9000.forestrun.entities.PlayerPhysicsTest \
  --tests com.anurag9000.forestrun.entities.PlayerJumpVelocityTest
```

All 23 tests passed (5 frame-input admission, 11 Player boundary, 4 Player physics, and 3 jump-velocity tests; zero failures/errors/skips). These checks are independently grounded in `FRAME_INPUT_ADMISSION.md`'s 0.05-second maximum frame delta and gameplay's short-hop/full-hold contract. `PlayerBoundaryTest` checks invalid deltas leave state unchanged, oversized delta is capped, poisoned kinematics are repaired, ground override is finite, hitboxes stay foot-anchored, and duck stance remains ground-aligned. The jump tests check malformed input fallback, force bounds, and monotonic hold mapping. Together with the 27 named TASK-C-007 tests in EVID-076 and source contract review, this closes the task at its authored unit/property scope. It does not establish human fairness/readability or device gameplay acceptance, which remain under TASK-D-001/TASK-D-002 and TASK-C-013.

## Static visual asset review

All 29 runtime sprite sheets were visually inspected as decoded frame strips: five player sheets, five plant sheets, four tree sheets, ten ordinary/flying bird sheets, and five animal sheets. Static code cross-checks covered `SpriteManager.kt`, `AssetPaths.kt`, `CREATIVE_DIRECTION.md`, `GAME_DESIGN.md`, `ENCOUNTER_CATALOGUE.md`, and `ASSET_PROVENANCE.md`.

The Wolf sheet is already four frames: `wolf_4frames.png` is 1280×156, `AssetPaths.WOLF` selects it, and the loader slices four frames. The creative specification describes the four-frame sheet as present and leaves quality/readability and rights for human review. No eight-frame source change is indicated.

Every ordinary/flying bird pair is byte-identical in this snapshot:

| Pair | SHA-256 (both files) |
|---|---|
| `chickadee_4frames.png` / `chickadee_flying.png` | `0fa00161d46c8b3bbcbf3d816c0a096202205b86d87489bab9de5a156b7c9b61` |
| `duck_4frames.png` / `duck_flying.png` | `6c8784a6ca02e4f3c0ef681dbba7821902697356c47292afa26557c9c116554e` |
| `eagle_4frames.png` / `eagle_flying.png` | `e857abbd761d3481510ad46b416e07a06ad68df08e1c69a436f4f50a1121ce16` |
| `owl_4frames.png` / `owl_flying.png` | `c786b00351076d6e5ae25d8c170840198573e978beaaee2a55f96eeabb5ee25f` |
| `tit_4frames.png` / `tit_flying.png` | `011afae97578c4cfe4e097a6c5681f07679c83a92d77c487d538398d485b26de` |

The current creative direction requires distinct silhouettes among the five bird species; it does not clearly require a unique ordinary-versus-flight pose for each species. Identical filename pairs therefore establish duplicated assets, not a source-code defect or an unambiguous violated requirement. Preserve this as a clarification/review item under `TASK-D-003`.

Some player and creature frames appear fragmented or have detached pixels, notably parts of the jump/duck strips and Wolf, Dog, Hedgehog, Eagle, and Owl. Plants, trees, Cat, and Fox appeared coherent in this review. These are art-quality risks for the human creative review under `TASK-D-002`/`TASK-D-003`; image inspection does not establish creator rights. `ASSET_PROVENANCE.md` continues to require rights/provenance review. No application-code change is supported by these observations.

## Narrow gameplay follow-up

The bounded follow-up on encounter family and fairness coverage confirmed 19 implementation identities in `EncounterFamilyCatalogue.kt` and their factory routing in `EntityFactory.kt`. There are 28 authored scenarios: five scripted and 23 deliberately manual. Script configuration establishes deterministic input and staging, not rendered readability, perceptual fairness, outcome quality, or complete sequence behavior. `OWL_DIVE` schedules a jump at 1.80 seconds, satisfying the source-level precondition identified for Owl alert/dive. No new requirement-backed source defect was found. Human/device fairness and the actual candidate-bound OWL screenshot remain unverified.

## Remaining evidence boundaries

- Do not treat the focused tests as a replacement for full exact-main suite evidence; EVID-072 and the hosted exact-main workflow cover those separate gates.
- Do not treat source asset presence, image hashes, or static contact sheets as approval of visual quality, accessibility, creator rights, or licensing.
- `TASK-C-013` still requires a candidate-bound device capture and image inspection. No local `adb`/authorized device was available for this pass.
- Physical performance, human gameplay/accessibility, external policy/privacy/security, production signing, Play delivery, and accountable release approval remain assigned to existing B/D/E phase records.

See EVID-076 and EVID-079 for focused exact-main tests and EVID-077 for the asset visual/static source review. See EVID-074 for gameplay/persistence/release source review and exact d1 hosted evidence, and EVID-075 for scoped concurrency evidence.

## TASK-C-008 process-death verification candidate (compile-only)

To close the previously identified process-kill/relaunch evidence gap, this worktree adds a dedicated `@LargeTest` instrumentation test at `app/src/androidTest/java/com/anurag9000/forestrun/ProcessDeathPersistenceInstrumentedTest.kt` and a bounded two-stage driver in `scripts/run_connected_validation.sh`. The `prepare` stage clears namespaced test state, writes high-score and maximum garden-progress values, and synchronously flushes the SharedPreferences file. The host driver then force-stops only the debug target package, verifies the device remains connected and that `pidof` reports no target process, and starts a fresh instrumentation invocation. The `verify` stage launches `MainActivity` and checks both persisted values; success/failure cleanup clears the test state. The existing ordinary connected suite continues excluding `@LargeTest`.

Static checks passed: `bash -n scripts/run_connected_validation.sh` and `git diff --check`. The host driver captures raw `am instrument -w -r` output and requires one passing per-test status plus the successful session terminator; this is necessary because AOSP reports test and session results separately and its normal command path exits zero ([AOSP Instrument.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/cmds/am/src/com/android/commands/am/Instrument.java), [Android command-line instrumentation guide](https://developer.android.com/studio/test/command-line)). The test APK compiled successfully with:

```sh
ANDROID_HOME="$ANDROID_HOME" \
ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT" \
./gradlew --no-daemon --max-workers=2 assembleDebugAndroidTest
```

The first build attempt omitted SDK environment variables and stopped before compilation with “SDK location not found”; the retry used the already-installed SDK and succeeded. **The connected process-death test has not run.** No local `adb`/device is available in this environment, and the new candidate has not been published to the hosted runner. TASK-C-008 and dependent TASK-C-011 therefore remain partial. Do not claim process-death durability from compilation. The connected candidate run requires an explicitly authorized publication path and an available connected runner; physical/OEM recovery behavior remains outside this emulator check.


### Host-runner correction after first candidate publication

The first published candidate `fc737302e294f510d41dd107b2662d07269599e5` failed `test_connected_validation_runner.ConnectedValidationRunnerContractTest.test_runner_preserves_connected_test_failures`: the contract rejects literal `|| true` in the connected command tail. The API 35 connected job was skipped, so that run provides no process-death evidence. The process-query logic now captures a remote `pidof` exit marker and distinguishes the expected no-process result from a query/transport failure. The instrumentation helper uses raw output and validates per-test and session result codes. The focused runner contract suite then passed **12/12** (`PYTHONPATH=scripts python3 -m unittest test_connected_validation_runner`); `bash -n` and `git diff --check` passed. This correction awaits a new hosted candidate run. TASK-C-008 remains partial. See EVID-082.


### API 35 connected failure and PID marker fix

Corrected candidate `d3ccbe517273238ad5de38ced6f85ed97bb6caa0` passed its host/release/lint/package job, but its API 35 connected job failed in `Run API 35 connected validation` with shell exit code 1. The public check annotations were generic and the unauthenticated detailed log endpoint was unavailable, so the precise failing line is not proven. Static source review did find that the host shell expanded `$?` before sending the pidof command to Android; this can make the remote exit marker report a false success. The host command now escapes `$` so the device shell records `pidof`'s actual result. The focused runner contract passed 12/12 and the full Python host script suite passed 1,387/1,387 (18.0 seconds; EVID-083/EVID-087). This corrected quoting is awaiting a new API 35 connected run. TASK-C-008 remains partial; the failed run is not durability evidence (EVID-086).

### Exact d8 candidate result: connected durability remains unresolved

Candidate `d8afcd8ea8703043157abc1e08c3b9c6b2cfcb53` completed hosted run `40480503602`. Host job `122109307395` passed its candidate checkout, source contract, full host tooling, SDK, compile/test/lint/assemble/release-hardening, packaged alignment, source-immutability, and artifact-upload steps. The separate API 35 job `122111792697` provisioned its emulator successfully, but `Run API 35 connected validation` failed with the public annotation `The process '/usr/bin/sh' failed with exit code 1`; its artifact-upload step succeeded. Unauthenticated public views did not expose detailed logs or artifact contents. Thus the exact failing command/stage is unresolved; this does not establish whether failure occurred in ordinary connected instrumentation, process setup, force-stop observation, or restored-state assertions. The run is recorded as failure evidence only (EVID-089), and TASK-C-008/TASK-C-011 remain partial. No additional source change is justified without the failing command's evidence.
