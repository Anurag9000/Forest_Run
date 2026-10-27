# Authorized verification delta — 2026-10-27

## Scope and source identity

The user clarified that source completion is authorized. The previously deferred verification queue was therefore advanced for this bounded pass. The original checkout was `main` at `eeccc677107f69039dbf71053c18a9038d4b5ff6`, with 166 pre-existing dirty entries recorded in `file_inventory.json` and `DELTA_REVIEW.md` (`WT-D17D3FBCF2CB3B267FE9`). `origin/main` was `21efd764a5c9f7773a70ac50f2569153e01f914b`, 13 commits ahead. Verification of the deliverable used a detached worktree at that remote revision plus the reviewed tracked delta and 24 untracked authored source/test/resource files. The current upstream no-training authority and its regression test were retained; the dirty local replacement of that test was not overlaid because it would discard newer upstream coverage. Results below apply to that merged candidate tree, not to the original local `HEAD` or the original dirty test file.

## Checks run

| Check | Result | Evidence limits |
|---|---|---|
| `COVERAGE_FILE=/tmp/forest-run-merged-main-coverage python3 -m unittest discover -s scripts -p 'test_*.py'` | **Passed:** 1,387 tests in 17.334 s. | Full Python tooling suite on the merged candidate, including upstream no-training source/tests and the reviewed delta. A duplicate ZIP-member fixture emitted a warning; the suite still passed. |
| `ANDROID_HOME=$ANDROID_HOME JAVA_HOME=/opt/android-studio/jbr JAVA_TOOL_OPTIONS='-XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=512m' ./gradlew --no-daemon --max-workers=2 testDebugUnitTest --rerun-tasks lintDebug lintRelease assembleDebug assembleRelease` | **Passed:** 1,297 JVM tests; 0 failures, 0 errors, 0 skipped; 242 XML reports. Debug/release lint and APK assembly passed. Gradle completed 106 actionable tasks. | Java 21/JBR 21.0.9, Android SDK 36. C2 was disabled after the unmodified JBR C2 compiler crashed during the first run. APK assembly is not a signed or store-approved release candidate. |
| GitHub Android validation workflow `321858924`, REST run history | Latest visible run succeeded for `21efd764a5c9f7773a70ac50f2569153e01f914b`, run `37622724286`, created 2026-10-07. | Historical hosted CI evidence for the remote commit; not a fresh run for the dirty local worktree. [Run record](https://github.com/Anurag9000/Forest_Run/actions/runs/37622724286). |

## Failed attempts and environment diagnosis

- `JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest --rerun-tasks` compiled sources but the test executor JVM crashed with SIGSEGV in `Node::uncast` on `C2 CompilerThread0`, compiling Robolectric's `QueuedWork.waitToFinish`. This was a JVM process crash, not an assertion failure. Crash logs created by this attempt were removed after recording the signature.
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon testDebugUnitTest --rerun-tasks` ran 629 tests but reported 140 setup failures because Robolectric Android SDK 36 requires Java 21. These were environment compatibility failures; they are superseded by the successful Java 21 run above.
- The first aggregate lint/build retry using Java 21 with C2 disabled but default code-cache sizing failed in lint analysis and D8 with `Out of space in CodeCache`. Increasing `ReservedCodeCacheSize` to 512 MiB and limiting Gradle to two workers produced the successful aggregate result above.

## Source completion

Static scenario review found that the Play capture manifest maps `07-owl.png` to `OWL_DIVE`, while `Owl.updatePlayerInteraction` enters alert/dive only when the player is jumping and the capture launcher supplied no input. `DebugScenarioScript.stepsFor(OWL_DIVE)` now dispatches `TAP_JUMP` at 1.80 seconds, before the capture's 2.1-second settle interval ends. A Kotlin test checks the action boundary and trace event, the independent Python scenario parser checks the exact authored step, and the screenshot shell contract binds the `07-owl` capture to that scripted scenario. This closes the static input-path gap; actual captured appearance remains unverified without an Android device.

## Not run / remaining evidence

- `adb` is not installed (`adb: command not found`), so connected instrumentation tests and emulator/device workflows were not run.
- No physical-device acceptance, UI/TalkBack review, live performance measurement, screenshot capture, Play upload, signing, policy/security/privacy approval, or accountable release go/no-go was performed.
- No training or inference applies to this Android game. No external release or publication action was taken.
- The OWL_DIVE source/test change is included in the merged candidate and was covered by the full suites/build above. The original dirty worktree remains untouched. No device runtime, release preparation, signing, external service, training, or inference operation was performed.

## Ledger interpretation

The full Python and JVM suites, lint, and APK assembly provide fresh evidence for source-level regression coverage. They do not prove every acceptance gate complete. `TASK-C-007` through `TASK-C-011` are recorded as partially completed at this evidence level; `TASK-C-012` remains deferred because no deterministic concurrency-specific run was separately performed. `TASK-C-013` tracks visible OWL_DIVE capture verification and is blocked by unavailable `adb`/device access. Phase D external release gates remain open. `TASK-E-001` remains deferred pending connected/device evidence and accountable external release evidence.


## Publication and hosted CI status

The reviewed source-completion candidate was committed as `df705c65c7f0a7ef03669a0c6767de5f381b418a` and pushed directly to `origin/main`. A post-push `ls-remote` and fetch both observed that revision on `refs/heads/main`. This commit is based on `21efd764a5c9f7773a70ac50f2569153e01f914b`; its exact source payload is the tested candidate described above.

The push-triggered `Estate local training-control certificate` run [40467805058](https://github.com/Anurag9000/Forest_Run/actions/runs/40467805058) and `Training-control applicability audit` run [40467804987](https://github.com/Anurag9000/Forest_Run/actions/runs/40467804987) completed successfully. Android validation [40467805038](https://github.com/Anurag9000/Forest_Run/actions/runs/40467805038) was still in progress at the compile/test/lint/assembly/release-hardening step when observed. EVID-073 records this publication and CI snapshot.
