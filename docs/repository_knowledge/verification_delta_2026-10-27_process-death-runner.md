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
