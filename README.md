# Forest Run

Native Android endless runner in Kotlin using a custom `SurfaceView` game loop. Forest Run aims to be a handcrafted, personality-rich cottagecore journey rather than a minimal score chaser.

## Product Vision

The player begins beneath a willow, runs through five atmospheric biomes, collects seeds, enters Bloom, practices mercy toward forest creatures, rests after failure, and returns to a persistent Garden. Repeated encounters are meant to change relationships, dialogue, sanctuary details, and the emotional shape of later runs.

**Core loop:** willow ritual → run → soft failure/rest → Garden return → run again

## Current Status

**Feature-rich alpha under correctness remediation.** Most intended systems exist, but existence is not the same as release readiness. Core gameplay invariants, persistence semantics, lifecycle behavior, presentation clarity, assets, performance, and hardware validation still require work.

The `agent/fix-core-gameplay-invariants` repair branch currently addresses the highest-risk defects found in the code audit:

- responsive variable-height jump behavior, including valid quick taps
- swipe-down classification before jump initiation
- Bloom as a power flag rather than a locomotion state, so airborne physics continue
- one authoritative Bloom timer and no reward-driven Bloom retrigger
- one terminal outcome per entity encounter
- collision resolution before clean-pass rewards with deterministic severity
- one mercy reward per encounter
- collectible Seed Orbs spawned ahead of the player
- disabled unsafe entity pooling until complete reset contracts exist
- debug encounters isolated from persistent relationship history
- lifetime seed balance protected from stale Garden refunds
- repeated `singleTask` debug intents and activity lifecycle handling

These repairs still require a real Android build, automated test run, and device verification before they can be considered complete.

## Implemented System Surface

- custom `SurfaceView` render loop and frame-time-based simulation
- player run, jump, duck, fall, land, stumble, rest, and Bloom presentation
- 19 entity classes across flora, trees, birds, and animals
- five-biome cycle: Meadow, Orchard, Ancient Grove, Dusk Canyon, Night Forest
- score, distance, seeds, Bloom, mercy, pacifist route, and run-summary systems
- persistent Garden, plants, wardrobe, relationships, forest mood, return moments, and story fragments
- ghost replay, particles, camera feedback, dialogue, flavor text, audio, and haptics
- deterministic debug encounter scenarios and store-support scripts

## Known Release Blockers

- complete the remaining gameplay and persistence corrections listed in [`docs/RELEASE.md`](docs/RELEASE.md)
- add and run invariant tests for entity outcomes, debug isolation, Garden lifecycle, thread shutdown, and UI state transitions
- eliminate remaining draw/update allocations and unbounded presentation queues
- validate all sprites, audio, frame dimensions, and fallback behavior with strict release checks
- replace placeholder package identity and configure release signing
- harden screenshot/store scripts and validate current Android/Play requirements
- perform real-device readability, haptic, audio, long-run, and performance testing

## Documentation

| File | Contents |
|---|---|
| [`docs/GAME_DESIGN.md`](docs/GAME_DESIGN.md) | Product vision, session lifecycle, systems, and entity intent |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Runtime structure and implementation notes; treat strong performance claims as targets until measured |
| [`docs/RELEASE.md`](docs/RELEASE.md) | Correctness, validation, packaging, and release checklist |

## Build & Test

```bash
bash gradlew testDebugUnitTest
bash gradlew assembleDebug
bash gradlew assembleDebugAndroidTest
bash gradlew connectedDebugAndroidTest
```

Expected debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Canonical Runtime Direction

- **Biomes:** five runtime biomes
- **Bloom target:** eight seeds and a six-second active window
- **Input intent:** tap for a short jump, hold for a higher jump, swipe down to duck
- **Failure flow:** run → rest summary → fade → Garden → next run
- **Current package placeholder:** `com.anurag9000.forestrun`
- **Current manifest orientation:** `landscape`

The package identity, release SDK/toolchain settings, signing, and orientation policy must be deliberately finalized before store release.
