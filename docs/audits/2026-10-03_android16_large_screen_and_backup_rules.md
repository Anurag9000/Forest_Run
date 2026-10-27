# Android 16 large-screen behavior and backup rules (2026-10-03)

## Findings

The app targets API 36 and fixes both activities to landscape. On a 600dp+ Android 16 emulator, the default orientation override rendered the game in portrait and caused menu copy and art to overlap. The manifest now identifies the app as a game and opts out temporarily from restricted-resizability enforcement while the custom Canvas interface remains non-adaptive. The rebuilt app displayed legible landscape game content letterboxed in the portrait emulator. The activity still did not rotate that emulator's physical display; this is not evidence of behavior across tablets or physical devices. Android documents the opt-out as temporary and unavailable at target API 37. See [Android 16 orientation and resizability behavior](https://developer.android.com/about/versions/16/behavior-changes-16).

The manifest already disabled backup, but Android documents OEM device-to-device transfer exceptions for that flag. The modern cloud/device-transfer and legacy rules now explicitly exclude all nine supported storage domains, including device-protected storage; see Android's [Auto Backup rule syntax and domain list](https://developer.android.com/identity/data/autobackup). Robolectric verifies every domain in every configured section. The release AAB includes both backup XML resources.

## Regression and build evidence

- JVM/Robolectric: 1,268 tests passed.
- Connected instrumentation: all 28 tests passed on API 36 at 720x1280 and 120 dpi (smallest width 600dp). The API 36 manifest-property test passed, along with hardware profile/report cases.
- `assembleDebug`, `assembleRelease`, `bundleRelease`, `lintDebug`, and `lintRelease` passed.
- The release AAB retains both backup-rule resources and all 15 raw Ogg resources, which are resolved dynamically at runtime.
- `git diff --check` passed.

The first full connected run exposed a test assumption: Bamboo is intentionally not spawned when the actual game surface cannot fit its complete reachable gap. The instrumentation assertion now derives whether Bamboo belongs in the expected roster from `EntityFactory.canStageBamboo(surfaceHeight)`. A targeted rerun passed, followed by the complete 28-test pass.

## Limits

The emulator run is not physical-device performance or product acceptance evidence. A separate earlier full connected attempt started zero tests after host memory pressure killed the emulator. Stale read-only emulator copy-on-write files had also exhausted the user's `/tmp` quota and blocked Conscrypt extraction; removing those inactive generated overlays restored JVM test execution. The repository-wide 835-test Python suite passed after these Android-only edits. Remaining lint warnings are tracked in Gradle reports and are not all confirmed defects.
