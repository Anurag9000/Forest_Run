# Forest Run

`Forest Run` is a native Android endless runner written in Kotlin with a custom `SurfaceView` game loop. The project targets Android Studio / Gradle Android builds and does not use Compose, React Native, Flutter, or a backend service.

## Current State

- Native Android app package: `com.yourname.forest_run`
- Rendering: `SurfaceView` + dedicated game thread
- Orientation: landscape
- Visual asset source of truth: [Final_Assets (2)](/home/anurag-basistha/Projects/TODO/Forest_Run/Final_Assets%20(2))
- Runtime sprite sheets under `app/src/main/assets/sprites/` are generated from that pack by `scripts/import_final_assets.py`
- Min SDK / Target SDK: 24 / 34
- Main flow: menu -> run -> game over -> restart
- Meta flow: menu -> garden -> back to menu
- Build status: `compileDebugKotlin`, `testDebugUnitTest`, `assembleDebug`, and `assembleDebugAndroidTest` all pass
- Device status: `connectedDebugAndroidTest` executed successfully on a Vivo 1933 (Android 11)

## Verified Features

- Touch controls for tap-to-jump, hold-to-extend jump, and swipe-down duck.
- Hardware-verified menu flow on Vivo 1933: launch -> tap to stand -> tap to run -> gameplay loop advances.
- Hardware-verified jump input on Vivo 1933 after entering gameplay.
- Hardware-verified biome progression checkpoints across the full five-biome cycle on Vivo 1933.
- Hardware-verified bloom activation sync on Vivo 1933.
- Hardware-verified bloom invincibility preventing immediate death on Vivo 1933.
- Hardware-verified collision -> game over -> restart cycle on Vivo 1933.
- Hardware-verified garden open -> unlock -> persist -> back-to-menu flow on Vivo 1933.
- Hardware-verified live update/draw pass with all 19 entity types spawned on Vivo 1933.
- Player state machine with running, jump start, jump, apex, fall, landing, duck, stumble, bloom, and rest.
- Endless spawning for 19 entity types across flora, trees, birds, and animals.
- Five biome cycle with live color blending and biome-specific spawn pools.
- HUD for score, distance, seeds, bloom meter, and mercy hearts.
- Bloom system: 8 seeds activates a 6-second invincibility state with player/audio sync.
- Seed collection and lifetime seed persistence.
- Garden screen with persistent plant unlock progression.
- Ghost run save/load based on best recorded distance.
- Audio and haptics managers for jump, land, hit, mercy, bloom, and music transitions.
- Wolf howl and dog bark SFX cues now fire from their live entity state machines.

## Repo Cleanup Applied

- Removed stale checked-in APK artifact: `Forest_Run_Final.apk`
- Removed unused root-level legacy `assets/` tree
- Removed obsolete competing asset archive `Final_Assets`
- Removed obsolete local sprite generator `sprite_builder.py`
- Removed empty `.gitkeep` placeholders from populated source folders
- Replaced placeholder example tests with real unit/Robolectric/instrumentation smoke coverage

## Build And Test

Use Android Studio or the Gradle wrapper from the repo root.

```bash
bash gradlew testDebugUnitTest
bash gradlew assembleDebug
bash gradlew assembleDebugAndroidTest
bash gradlew connectedDebugAndroidTest
```

Generated artifacts:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Android test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

Ghost persistence note:

- Ghost save/load is covered by host-side persistence tests.
- A connected-device ghost persistence assertion remains intentionally skipped because it is not yet giving a trustworthy hardware signal.

## Project Layout

```text
app/src/main/java/com/yourname/forest_run/
  MainActivity.kt
  engine/      core loop, state, audio, save, camera, parallax
  entities/    player plus flora, trees, birds, animals
  systems/     particles, ghost replay, seed orbs
  ui/          menu, garden, HUD, flavor text, game over
  utils/       math and bitmap helpers
```

See the files in `docs/` for the verified architecture and feature inventory.
