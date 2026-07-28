# Forest Run

Native Android endless runner in Kotlin using a custom `SurfaceView` game loop. Forest Run aims to be a handcrafted, personality-rich cottagecore journey rather than a minimal score chaser.

## Product Vision

The player begins beneath a willow, runs through five atmospheric biomes, collects seeds, enters Bloom, practices mercy toward forest creatures, rests after failure, and returns to a persistent Garden. Repeated encounters change dialogue, sanctuary details, relationship history, and the emotional shape of later runs.

**Core loop:** willow ritual → run → soft failure/rest → Garden return → run again

## Current Status

**Feature-rich alpha with the primary correctness-remediation pass implemented and automatically validated.** The repair branch now has a production application identity, current Android target, strict debug/release CI, broad invariant coverage, an actually obfuscated/resource-shrunk release bundle, atomic off-thread ghost persistence, and cutout-safe essential UI. It is not yet a release candidate because connected-device, physical-hardware, performance, signed-upload, settings/accessibility, and store-acceptance work remains.

The `agent/fix-core-gameplay-invariants` branch includes:

- responsive tap/hold jumping and swipe-down arbitration
- Bloom as an orthogonal power state, preserving airborne physics
- one authoritative Bloom timer and exclusive conversion rewards
- one terminal outcome per entity with deterministic collision priority
- pure collision queries and selected-outcome side effects
- all-entity clean-pass, debug-isolation, and persistence integration coverage
- outcome-earned relationship Trust and Bond progression
- collectible Seed Orbs staged ahead of the player
- distance-based encounter spacing that remains stable as speed changes
- live Eagle targeting and aligned flora/tree collision geometry
- bounded gameplay input routing and interruption-safe render-thread shutdown
- Garden lifecycle, local-day, particle, layout, and currency corrections
- bounded/wrapped presentation queues and cached game-over composition
- strict runtime asset checks and hardened audio/music lifecycle handling
- atomic, corruption-checked ghost saves away from the render thread
- one aspect-preserving safe-content transform for menu, Garden, HUD, debug, and rest UI
- final application ID `com.anurag9000.forestrun`
- API 36 debug and release validation, including a genuinely obfuscated unsigned AAB

## Implemented System Surface

- custom `SurfaceView` render loop and frame-time-based simulation
- player run, jump, duck, fall, land, stumble, rest, and Bloom presentation
- 19 entity classes across flora, trees, birds, and animals
- five-biome cycle: Meadow, Orchard, Ancient Grove, Dusk Canyon, Night Forest
- score, distance, seeds, Bloom, mercy, pacifist route, and run-summary systems
- persistent Garden, plants, wardrobe, relationships, forest mood, return moments, and story fragments
- ghost replay, particles, camera feedback, dialogue, flavor text, audio, and haptics
- deterministic encounter scenarios and store-support scripts

## Remaining Release Blockers

- run connected instrumentation tests and deterministic scenarios on an emulator and representative physical devices
- validate touch latency, transformed safe-content readability, audio, haptics, lifecycle recovery, and long-run stability on hardware
- profile frame time, allocations, memory, I/O, audio threads, and sustained-play behavior
- validate density behavior and add reduced-motion plus user-facing audio/haptic settings
- provide real signing credentials and smoke-test the signed, minified artifact
- capture, curate, and manually approve final store screenshots and metadata
- verify broader save migration/corruption recovery and current store-policy requirements
- decide whether the remaining procedural scenic layers and fixed-landscape policy are final art/product choices

See [`docs/RELEASE.md`](docs/RELEASE.md) for the evidence-backed checklist.

## Documentation

| File | Contents |
|---|---|
| [`docs/GAME_DESIGN.md`](docs/GAME_DESIGN.md) | Product vision, session lifecycle, systems, and entity intent |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Runtime structure and implementation notes; treat unmeasured performance claims as targets |
| [`docs/RELEASE.md`](docs/RELEASE.md) | Correctness, validation, packaging, hardware, and store checklist |

## Build & Test

CI runs on Java 21 while the Android source remains compiled to Java 17 bytecode. Android API 36 must be installed.

```bash
bash gradlew compileDebugKotlin compileReleaseKotlin
bash gradlew testDebugUnitTest
bash gradlew lintDebug lintRelease
bash gradlew assembleDebug assembleDebugAndroidTest
bash gradlew bundleRelease
bash gradlew connectedDebugAndroidTest   # requires an emulator/device
```

Expected outputs:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Instrumentation APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- Release bundle: `app/build/outputs/bundle/release/app-release.aab`
- R8 mapping: `app/build/outputs/mapping/release/mapping.txt`

## Canonical Runtime Direction

- **Application ID:** `com.anurag9000.forestrun`
- **Biomes:** five runtime biomes
- **Bloom target:** eight seeds and a six-second active window
- **Input intent:** tap for a short jump, hold for a higher jump, swipe down to duck
- **Failure flow:** run → rest summary → fade → Garden → next run
- **Manifest orientation:** fixed landscape pending final device/product acceptance
- **Release signing:** external Gradle properties or environment variables; credentials are never committed
