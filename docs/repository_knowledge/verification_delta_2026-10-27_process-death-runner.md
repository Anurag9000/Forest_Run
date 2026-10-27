# Process-death runner verification delta — 2026-10-27

## Scope and provenance

This supplement reconciles authenticated API 35 workflow logs against the prior exact-main audit. The tested remote candidate was `7870bc678fcc3412a45a44eaf820ac2685111899`; the local working tree adds a not-yet-published runner correction and regression assertion. The original user checkout remains untouched.

## Findings

- Run `40480503602`, job `122111792697`, on `d8afcd8ea8703043157abc1e08c3b9c6b2cfcb53`: ordinary API 35 connected tests passed 28/28. The separate process-death `prepare` invocation and cleanup both failed with `Invalid userId -2`. The test body, force-stop, and restore stage were not reached (EVID-090).
- Run `40482368151`, job `122117493531`, checked old commit `7870bc678fcc3412a45a44eaf820ac2685111899`: host/release/lint/package passed; ordinary API 35 connected tests again passed 28/28; prepare and cleanup again failed with `Invalid userId -2` (EVID-092). This confirms the old-candidate failure and does not test the fix.
- Root cause: direct `adb shell am instrument -w -r` omitted a concrete Android user. On this API 35 emulator the implicit user value resolves to sentinel `-2`, which `am instrument` rejects.
- Local correction: `scripts/run_connected_validation.sh` passes `--user current`; the runner contract test asserts this option. `PYTHONPATH=. python3 -m unittest test_connected_validation_runner` passed 13 tests, including shell syntax validation. `python3 -m unittest discover -s scripts -p 'test_*.py'` passed 1,388 tests with zero failures/errors in 18.271 seconds (EVID-091).

## Current disposition

The runner defect is source-addressed locally. This is not yet connected verification of the correction. `TASK-A-036` remains partially completed until the correction is published and an exact-SHA API 35 run demonstrates process-death prepare, confirmed absence of the target process after force-stop, and successful fresh-process restore. `TASK-C-008` and dependent `TASK-C-011` remain partial. Even a passing hosted emulator run will not establish physical-device/OEM durability.

The earlier matrix and dated verification reports remain historical snapshots; use this supplement, `EVID-090`–`EVID-092`, and the current execution dashboard for the latest status. No Android test workflow was launched from the local machine. The workflow evidence cited above came from already-triggered GitHub runs.
