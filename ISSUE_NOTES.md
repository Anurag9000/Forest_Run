# Issue Notes

Reviewed against the current `com.anurag9000.forestrun` sources on 2026-10-03. The previous contents described a different checkout (`com.yourname.forest_run` under `Projects/ToFix`) and must not be treated as current findings.

## Previously reported findings checked against this checkout

| Historical report | Current evidence | Status |
| --- | --- | --- |
| Pooled entities retain stale state | `EntityManager.spawn()` constructs each entity through `EntityFactory.create()` and appends it to `activeEntities`; no entity reuse pool exists in the current manager. | Not present in current implementation. |
| `singleTask` debug launch ignores replacement intents | `MainActivity.onNewIntent()` stores and dispatches the new intent through the readiness gate. | Addressed in current implementation. |
| `addBonus()` applies a persistent multiplier contrary to its contract | The current API documents its duration semantics; `update()` decrements positive timers and keeps a zero-duration boost for the rest of the run. | Historical contract mismatch no longer applies. |
| Cat despawn contains `x > x + 200f` | Current `Cat.update()` checks `x > waveStartX + 200f` while waving. | Reported expression is not present. |

The earlier links and source paths referred to another project and were removed. No current defect is confirmed by this historical list. Continue recording new findings here with a current source path, reproduction/evidence, impact, and regression coverage.

## Current audit state

- Python script suite: 870 tests passed after adding PNG scanline/filter and transactional screenshot capture publication coverage.
- Follow-up transaction rollback audit: 874 Python tests pass. Fault-injection tests now confirm Play delivery, release governance, and human acceptance compilers retain unrecovered backups if rollback itself fails; the earlier destination is restored and the remaining backup path is reported.
- Follow-up output-path audit: 877 Python tests pass. The three evidence compilers now reject output and summary symlinks before path resolution; regressions verify a same-directory symlink cannot redirect publication over its target.
- Full compiler-chain follow-up: 881 Python tests pass. The same symlink redirection was fixed in installed identity matrix and device acceptance; failed rollback now retains their backups. All five `scripts/compile_*.py` evidence publishers were scanned and covered for these failure modes.
- Transaction retry follow-up: 885 Python tests pass. Screenshot curation now recovers or preserves stale publication backups instead of deleting them on retry. The canonical release wrapper retains and reports quarantined summary backups if restoration fails; a shell-level fault injection covers this path.
- Graphics publication follow-up: 888 Python tests pass. Store graphics generation now recovers a previous directory backup instead of deleting it on retry, fails closed when output and backup both exist, and refuses an output symlink.
- Managed directory validation follow-up: 890 Python tests pass. Screenshot curation and graphics publication reject regular files at staging/final/backup directory paths without moving or deleting them.
- Fresh Kotlin verification: `JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest --rerun-tasks` succeeded with all 30 tasks executed. Summed 241 XML reports: 1,288 tests, 0 failures, 0 errors, and 0 skipped.
- Coverage-driven input hardening: 897 Python tests pass. Reproduced and fixed raw screenshot symlink following; curation now no-follow reads bounded bytes and binds image decoding to the bytes hashed. Coverage tests include real store-graphics rendering.
- Screenshot gate continuation: 900 Python tests pass. Tests now exercise raw image size, orientation, blankness, dark-bar, corruption, and in-read mutation gates. Measured line coverage is 82% curation, 81% graphics generation, and 80% screenshot publishing; remaining untested branches stay in audit scope.
- Asset-importer audit: 906 Python tests pass. Fixed 3-row/6-row grid slicing that discarded source remainder pixels; importer branch tests cover 93% of its statements and a temporary integration run generated all 31 nontransparent outputs.
- Curation publication identity: 908 Python tests pass. Curator stages exact validated PNG/sidecar bytes and rejects sidecar changes between parse and staging; fault injection proves mutations cannot substitute the published content.
- JVM/Robolectric suite: freshly executed by `./gradlew testDebugUnitTest --rerun-tasks`; Gradle reported success.
- Android connected instrumentation: 30 tests passed on `Medium_Phone_API_36.1`, API 36; 0 failures and 0 skips.
- Correct ordinary connected run: 20 tests passed with `notAnnotation=androidx.test.filters.LargeTest`; no hardware-profile/capture classes were included. A preceding filtered run was OOM-interrupted, then passed after reducing concurrent Gradle memory use.
- `lintDebug` and `lintRelease`: passed; reports each contain 84 warnings and no fatal/error findings.
- `assembleDebug` and `assembleRelease`: passed; release R8/resource shrinking ran.
- Live device acceptance, physical devices, and the curated screenshot capture gate are still separate and unverified.
- The curated screenshot verifier was run against the actual release path and correctly failed closed because `release/google-play/screenshots/raw/` is absent; no capture data was fabricated.
