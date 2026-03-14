# Android Setup

## Tooling

- Android Studio with Android SDK 34
- Java 17 runtime
- Gradle wrapper included in repo

## Open In Android Studio

1. Open the repo root.
2. Let Gradle sync.
3. Use the `app` run configuration.
4. Run on a landscape-capable emulator or physical device.

## CLI Commands

```bash
bash gradlew compileDebugKotlin
bash gradlew testDebugUnitTest
bash gradlew assembleDebug
bash gradlew assembleDebugAndroidTest
```

## Output Paths

- App APK: `app/build/outputs/apk/debug/app-debug.apk`
- Test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

## Assets Used By The App

Runtime assets live under:

```text
app/src/main/assets/
  fonts/
  sprites/
```

The old root-level `assets/` directory was removed because the Android build does not read from it.

## Notes

- The app is a native Kotlin Android project only.
- No backend service, web frontend, or Compose module exists in this repo.
- Instrumentation tests compile, but running them requires an attached emulator/device.
