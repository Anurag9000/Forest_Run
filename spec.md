# Repo State Spec

## Identity

- Project type: native Android game
- Language: Kotlin
- UI/rendering: `SurfaceView`
- Build system: Gradle
- IDE target: Android Studio

## What Exists

- One Android app module
- No backend module
- No web frontend
- No Compose UI layer
- Local persistence only

## What Was Corrected In This Audit

- Menu/garden touch routing now uses real tap coordinates
- Bloom state now updates player/audio behavior correctly
- Entity pass rewards are one-shot instead of repeatable every frame
- Gameplay and garden persistence now share the same save store
- Player timing bug from double state-timer increments removed
- Placeholder example tests replaced with meaningful coverage
- Stale root assets and checked-in APK removed

## Primary Validation Commands

```bash
bash gradlew testDebugUnitTest
bash gradlew assembleDebug
bash gradlew assembleDebugAndroidTest
```
