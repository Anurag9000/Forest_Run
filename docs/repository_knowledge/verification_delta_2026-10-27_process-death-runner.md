# Process-death runner verification delta — 2026-10-27

## Scope and provenance

This supplement reconciles authenticated API 35 workflow logs against the prior exact-main audit. The tested remote candidate was `7870bc678fcc3412a45a44eaf820ac2685111899`; the local working tree adds a not-yet-published runner correction and regression assertion. The original user checkout remains untouched.

## Findings

- Run `40480503602`, job `122111792697`, on `d8afcd8ea8703043157abc1e08c3b9c6b2cfcb53`: ordinary API 35 connected tests passed 28/28. The separate process-death `prepare` invocation and cleanup both failed with `Invalid userId -2`. The test body, force-stop, and restore stage were not reached (EVID-090).
- Run `40482368151`, job `122117493531`, checked old commit `7870bc678fcc3412a45a44eaf820ac2685111899`: host/release/lint/package passed; ordinary API 35 connected tests again passed 28/28; prepare and cleanup again failed with `Invalid userId -2` (EVID-092). This confirms the old-candidate failure and does not test the fix.
- Root cause: direct `adb shell am instrument -w -r` omitted a concrete Android user. On this API 35 emulator the implicit user value resolves to sentinel `-2`, which `am instrument` rejects.
- Local correction: `scripts/run_connected_validation.sh` passes `--user current`; the runner contract test asserts this option. `PYTHONPATH=. python3 -m unittest test_connected_validation_runner` passed 13 tests, including shell syntax validation. `python3 -m unittest discover -s scripts -p 'test_*.py'` passed 1,388 tests with zero failures/errors in 18.271 seconds (EVID-091).

## Current disposition

The first local alias correction failed on exact SHA 4845 (EVID-093). A numeric foreground-user lookup is now source-addressed locally and its host contracts pass (EVID-094); it has not yet been published or connected-tested. `TASK-A-036` remains partially completed until an exact-SHA API 35 run demonstrates process-death prepare, confirmed absence of the target process after force-stop, and successful fresh-process restore. `TASK-C-008` and dependent `TASK-C-011` remain partial. Even a passing hosted emulator run will not establish physical-device/OEM durability.

The earlier matrix and dated verification reports remain historical snapshots; use this supplement, `EVID-090`–`EVID-094`, and the current execution dashboard for the latest status. No Android test workflow was launched from the local machine. The workflow evidence cited above came from already-triggered GitHub runs.


### Exact 4845 candidate disproves the `current` alias correction

Run `40484661395` checked exact SHA `4845d9f38e5ebb67c0d33d59709ae31c71a2d26d`. The host compile/test/lint/packaging job passed. API35 ordinary connected instrumentation again passed 28/28, but direct instrumentation with `--user current` still failed before the process-death test body: the emulator reported `Invalid userId -2` in both prepare and cleanup (EVID-093). The platform advertises the alias in help, but this `am instrument` path resolves it to the rejected sentinel. Thus the first correction does not work and no force-stop or restoration evidence was produced.

The current local source now obtains the concrete foreground user with `adb shell am get-current-user`, rejects failed/empty/non-numeric output, and invokes `am instrument --user <numeric-id>`. The host regression test asserts query, numeric validation, and use of the resolved ID. `bash -n` and the focused 13-test suite passed; the full scripts suite passed 1,388/1,388 with zero failures/errors in 18.387 seconds (EVID-094). This numeric lookup implementation remains unpushed and has not been exercised by hosted API35. The next candidate must establish prepare, process absence after force-stop, and verify before TASK-A-036/TASK-C-008 can close.


### Exact eb326dd0 candidate confirms numeric user lookup and reveals extra parsing failure

The exact-SHA workflow `40486741810` checked `eb326dd0a2c0535c7773a78f07e1487f23426475`. Host/release/lint/package passed; ordinary API35 connected tests again passed 28/28. The direct process-death invocation progressed beyond user selection and then failed with `No instrumentation found for: forestRunProcessDeathStage=prepare`; cleanup reported the same parser problem. Neither test body nor force-stop/restore ran (EVID-095).

Android's instrumentation option contract is `-e <NAME> <VALUE>`; the runner constructed each `-e key=value` as one shell argument. The local fix now passes the option, name, and value separately (for both `class` and `forestRunProcessDeathStage`) and the regression test asserts that form. The focused host suite passes 13 tests. The full Python tooling suite then passed 1,388/1,388 with zero failures/errors in 18.276 seconds (EVID-096). The next exact-candidate API35 run must show the prepare test body, target-process absence after force-stop, and verify-stage success.


### Exact 323320b5 candidate identifies instrumentation component mismatch

Run `40488634560` on exact SHA `323320b56137e99211c3955223040f69537947d3` passed host checks and all 28 ordinary API35 connected tests. Numeric user resolution and separate extra pairs advanced to launch, where Android returned `Unable to find instrumentation info for ComponentInfo{com.anurag9000.forestrun.debug.test/androidx.test.runner.AndroidJUnitRunner}` (EVID-097). The prepare test, force-stop, and restore did not execute. This establishes that the hard-coded component string does not match the instrumentation package registered by the installed test APK.

The current local runner queries `pm list instrumentation`, selects exactly one `androidx.test.runner.AndroidJUnitRunner` whose target is the debug application, rejects zero or ambiguous matches, and invokes the resolved component. The focused 13-test contract and full scripts suite pass (1,388 tests, zero failures/errors in 17.976 seconds; EVID-098). The next exact API35 workflow must confirm component discovery and proceed through prepare, force-stop/no-PID, and verify.


### Exact facd8c06 candidate finds the AndroidTest APK was no longer installed

Run `40490608390` checked `facd8c06b351a46bd3fa6a58354b41c72847c402`. Host gates and the ordinary API35 connected suite passed. The post-Gradle `pm list instrumentation` query found zero `AndroidJUnitRunner` entries for the debug target and the runner failed closed (EVID-099); direct process-death test execution did not start.

The local driver now checks the candidate output paths and reinstalls both `app-debug.apk` and `app-debug-androidTest.apk` with `adb install -r` before runner discovery. It then filters by target package and fails if the result is missing or ambiguous. The focused runner contract and full Python tooling suite pass: 13 focused tests and 1,388 full tests, zero failures/errors in 17.860 seconds (EVID-100). The next exact API35 run must confirm both installs, one registered runner, prepare success, no target PID after force-stop, and fresh-process verify.


### Exact b9a79b23 candidate closes hosted process-death runner path

GitHub Actions run [40492450144](https://github.com/Anurag9000/Forest_Run/actions/runs/40492450144) checked exact SHA `b9a79b23a0cb855ce80c783e94e4b1b44eb0b94b`. The host/release/lint/package job succeeded, including source contracts, Python tooling checks, Gradle debug unit tests, debug/release lint, APK and AndroidTest APK assembly, R8/release bundle, package inspection, and source immutability. Package inspection found no native libraries; it does not replace a runtime check on a 16 KB page-size device.

The API35 job passed all 28 ordinary connected tests. The corrected runner then reinstalled the candidate application and AndroidTest APKs successfully, discovered the registered instrumentation component, and ran `durableGameStateSurvivesHostVerifiedProcessDeath` in two stages. Prepare passed (1 test); the host force-stopped the target and confirmed no running process; a fresh instrumentation invocation passed restore (1 test). The connected evidence artifact is `12729931477`; the host artifact is `12729190779` (EVID-101).

This closes `TASK-A-036` and `GAP-008` for the hosted runner path and completes the task-scoped hosted-emulator persistence check in `TASK-C-008`. It does not establish physical-device/OEM behavior or power-loss durability. `TASK-C-011` remains partial only for the targeted integration reconciliation across completed focused tasks and residual requirements; reuse the successful exact-candidate suites rather than rerunning unchanged checks. The original dirty checkout and `.training_control/` state remain preserved.
