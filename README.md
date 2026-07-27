# Forest Run

Native Android endless runner in Kotlin using a custom `SurfaceView` game loop. A handcrafted, personality-rich forest journey — not a prototype.

## What It Is

A lush cottagecore endless runner with Ghibli × Stardew Valley tone. The player runs through five atmospheric biomes, collecting seeds, activating Bloom power states, practicing mercy on the creatures she meets, growing a personal garden, and slowly earning the forest's trust across many sessions.

**Core loop:** run → soft failure → rest reflection → Garden return → run again

## What's Built


The broad product surface is implemented, but the project is in a **correctness-hardening beta**.
Core controls, encounter resolution, Bloom, persistence and release automation are protected by
regression tests and CI; physical-device tuning and final store acceptance remain mandatory.

## Implemented Surface

- Native `SurfaceView` runner with five biomes and nineteen entity families
- Mutually-exclusive jump/duck gesture recognition and variable-height jump physics
- Single-clock Bloom power state and one-shot encounter outcomes
- Mercy routes, persistent memory, Garden, wardrobe, ghost, audio, haptics and debug scenarios
- Android CI for unit tests and lint

## Documentation

| File | Contents |
|---|---|
| [`docs/GAME_DESIGN.md`](docs/GAME_DESIGN.md) | Vision, design pillars, session lifecycle, all systems, all 19 entities |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Runtime structure, all engine systems, constants, wiring |
| [`docs/RELEASE.md`](docs/RELEASE.md) | Build commands, device acceptance checklist, open items |

## Build & Test

```bash
bash gradlew testDebugUnitTest
bash gradlew assembleDebug
bash gradlew assembleDebugAndroidTest
bash gradlew connectedDebugAndroidTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Canonical Runtime Truth

- **5 biomes:** `MEADOW`, `ORCHARD`, `ANCIENT_GROVE`, `DUSK_CANYON`, `NIGHT_FOREST`
- **Bloom:** 8 seeds → 6 seconds invincibility
- **Input:** tap jump, hold for higher jump, swipe-down duck — gesture anywhere
- **Failure flow:** run → rest summary → fade → Garden → run
- **Package:** `com.yourname.forest_run`, Min SDK 24, Target SDK 34, Kotlin, sensorLandscape
