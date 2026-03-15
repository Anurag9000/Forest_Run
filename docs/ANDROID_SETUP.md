# Android Setup

## Tooling

- Android Studio
- Android SDK 34
- Java 17
- Gradle wrapper included

## Open And Run

1. Open the repo root in Android Studio.
2. Let Gradle sync.
3. Use the `app` configuration.
4. Run on a landscape-capable emulator or physical device.

## Validation Commands

```bash
bash gradlew compileDebugKotlin
bash gradlew testDebugUnitTest
bash gradlew assembleDebug
bash gradlew assembleDebugAndroidTest
bash gradlew connectedDebugAndroidTest
```

## Output Paths

- App APK: `app/build/outputs/apk/debug/app-debug.apk`
- Test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

## Asset Reality

Runtime assets live under:

```text
app/src/main/assets/
  fonts/
  sprites/
```

Source art currently comes from `Final_Assets (2)` and is imported by `scripts/import_final_assets.py`.

## Device-Truth Reminder

Because the major open issues are experiential, not just compile-time, real-device validation is mandatory for:

- entity scale and readability
- ghost clarity
- HUD legibility
- seed and Bloom visibility
- whether each creature’s personality actually lands in play

Build success alone is not enough to claim feature success for this project.
