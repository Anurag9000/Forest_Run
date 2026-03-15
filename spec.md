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
- Runtime sprite sheets now come only from `Final_Assets (2)` through `scripts/import_final_assets.py`
- Entity and menu/garden draw ratios now follow imported sprite frame aspect ratios instead of legacy hardcoded assumptions
- Placeholder example tests replaced with meaningful coverage
- Stale root assets, obsolete competing asset archive, obsolete sprite generator, and checked-in APK removed

## Primary Validation Commands

```bash
bash gradlew testDebugUnitTest
bash gradlew assembleDebug
bash gradlew assembleDebugAndroidTest
bash gradlew connectedDebugAndroidTest
```

## Current Visual Asset Reality

- `Final_Assets (2)` is the checked-in source asset pack.
- Runtime sheets in `app/src/main/assets/sprites/` are imported from that pack.
- Character, flora, tree, bird, animal, and VFX sheets are all generated from that source pack rather than from a second competing asset archive.

## Current Device Verification Reality

- Connected instrumentation was executed on a Vivo 1933 (Android 11).
- Verified on hardware: launch, menu-to-run flow, gameplay loop advancement, jump input, full biome-cycle checkpoints, bloom activation sync, collision-driven game over/restart, garden unlock persistence flow, and live update coverage with all 19 entity types spawned.
- Not yet fully hardware-verified: every individual entity behavior variant over long manual play sessions, ghost replay persistence on-device, and all audio/haptic perceptual quality.
