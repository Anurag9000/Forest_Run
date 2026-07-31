# Forest Run

Native Android endless runner in Kotlin using a custom `SurfaceView` game loop. Forest Run aims to be a handcrafted, personality-rich cottagecore journey rather than a minimal score chaser.

## Product Vision

The player begins beneath a willow, runs through five atmospheric biomes, collects Seeds, enters Bloom, practices mercy toward forest creatures, rests after failure, and returns to a persistent Garden. Repeated encounters change dialogue, sanctuary details, relationship history, and the emotional shape of later runs.

**Core loop:** willow ritual → run → soft failure/rest → Garden return → run again

## Current Status

**Feature-rich alpha with the primary correctness-remediation history consolidated on `main`.** The repository has a permanent application identity, current Android target, immutable exact-SHA host/emulator CI, broad invariant coverage, an obfuscated/resource-shrunk release bundle, versioned atomic ghost persistence, cutout-safe essential UI, persistent feedback controls, and a physical performance-evidence harness.

It is not yet a release candidate because representative physical-device acceptance, measured performance thresholds, signed-artifact installation, final visual approval, and store/policy work remain.

The canonical `main` branch includes:

- responsive tap/hold jumping and swipe-down arbitration;
- Bloom as an orthogonal power state, preserving airborne physics;
- one authoritative Bloom timer and exclusive conversion rewards;
- one terminal outcome per entity with deterministic collision priority;
- pure collision queries and selected-outcome side effects;
- allocation-free expanded mercy probes preserving custom safe-window geometry;
- all-entity clean-pass, debug-isolation, and persistence integration coverage;
- outcome-earned relationship Trust and Bond progression;
- collectible Seed Orbs staged ahead of the player;
- distance-based encounter spacing that remains stable as speed changes;
- live Eagle targeting and aligned flora/tree collision geometry;
- bounded gameplay input routing and interruption-safe render-thread shutdown;
- Garden lifecycle, local-day, particle, layout, and currency corrections;
- bounded/wrapped presentation queues and cached game-over composition;
- finite, capped Menu, HUD, and Rest presentation clocks;
- strict runtime asset checks and hardened audio/music lifecycle handling;
- 30 Hz, twenty-minute ghost capture with atomic off-thread persistence;
- ghost binary format v2 with magic/version headers, stable state codes, and legacy-file reads;
- one aspect-preserving safe-content transform for menu, Garden, HUD, debug, and rest UI;
- persistent reduced-motion, audio, and haptic settings enforced at manager boundaries;
- versioned SharedPreferences repair with future-schema compatibility storage;
- allocation-free frame timing capture and a physical-device JSON profiling harness;
- final application ID `com.anurag9000.forestrun`;
- API 36 host/release validation and API 35 connected validation on exact candidate SHAs.

## Development Workflow

`main` is the only active branch and the sole source of repository truth.

- Routine work is committed directly to `main`; no development branches or pull requests are created.
- Each commit must be coherent and include the relevant code, tests, configuration, specifications, and documentation.
- Existing history is preserved. Do not force-push, rewrite, squash away, or otherwise replace published history.
- Read the exact current blob before replacing a file and use optimistic-lock SHAs for repository writes.
- Add focused regression coverage for each corrected invariant.
- Permanent validation workflows are read-only and must never modify or push source.
- Historical closed or merged pull-request pages may remain in GitHub, but they are not active development surfaces and have no surviving source branches.

## Implemented System Surface

- custom `SurfaceView` render loop and frame-time-based simulation;
- player run, jump, duck, fall, land, stumble, rest, and Bloom presentation;
- 19 entity classes across flora, trees, birds, and animals;
- five-biome cycle: Meadow, Orchard, Ancient Grove, Dusk Canyon, Night Forest;
- score, distance, Seeds, Bloom, mercy, pacifist route, and run-summary systems;
- persistent Garden, plants, wardrobe, relationships, forest mood, return moments, and story fragments;
- ghost replay, particles, camera feedback, dialogue, flavour text, audio, and haptics;
- deterministic encounter scenarios, store-support scripts, and physical profiling tools.

## Automated Evidence

Permanent read-only CI checks out and records the exact event SHA, then runs:

- debug, release, unit-test, and instrumentation Kotlin compilation;
- the complete JVM/Robolectric invariant suite;
- debug and release lint;
- debug application and instrumentation APK assembly;
- minified/resource-shrunk unsigned AAB construction;
- effective R8 application-class renaming verification;
- API 35 connected behavioural tests.

A successful host job proves compilation, JVM/Robolectric correctness, lint, packaging, R8, and source immutability for that exact SHA. Connected-emulator evidence is tracked separately because runner boot or ADB failures can occur before application tests start. Neither automated result substitutes for representative physical-device acceptance.

The large hardware-capture and performance-profile tests are compiled but intentionally excluded from ordinary emulator CI. They must be run on representative physical devices.

## Remaining Release Blockers

- run deterministic scenarios and ordinary play on representative physical devices;
- capture and review frame-time, allocation/GC, memory, I/O, audio-thread, thermal, and long-run evidence;
- establish evidence-based performance thresholds and repair any material hotspots found;
- validate touch latency, transformed safe-content readability, feedback settings, audio, haptics, lifecycle recovery, and density behavior on phones/tablets/cutouts/unusual aspects;
- provide real signing credentials and smoke-test the signed, minified artifact;
- install through an internal store track and verify the store delivery path;
- capture, curate, and manually approve final store screenshots and metadata;
- visually verify artwork and animation frame counts, including the Wolf sheet;
- revalidate current store-policy, privacy, data-safety, content-rating, and submission requirements;
- decide whether the remaining procedural scenic layers and fixed-landscape policy are final art/product choices.

See [`docs/RELEASE.md`](docs/RELEASE.md) for the evidence-backed exit checklist.

## Documentation

| File | Contents |
|---|---|
| [`docs/GAME_DESIGN.md`](docs/GAME_DESIGN.md) | Product vision, runtime-honest entity mechanics, session lifecycle, and design rules |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Current runtime structure, ownership, persistence, CI, and architectural debt |
| [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md) | Physical-device profiling protocol, report collection, and threshold procedure |
| [`docs/RELEASE.md`](docs/RELEASE.md) | Correctness, validation, packaging, hardware, signing, and store checklist |

## Build and Test

CI runs on Java 21 while Android source remains compiled to Java 17 bytecode. Android API 36 must be installed.

```bash
bash gradlew compileDebugKotlin compileReleaseKotlin compileDebugAndroidTestKotlin
bash gradlew testDebugUnitTest
bash gradlew lintDebug lintRelease
bash gradlew assembleDebug assembleDebugAndroidTest
bash gradlew bundleRelease
bash gradlew connectedDebugAndroidTest   # requires an emulator/device
```

Physical performance evidence on one authorized device:

```bash
bash scripts/collect_performance_profiles.sh
```

Expected build outputs:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Instrumentation APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- Release bundle: `app/build/outputs/bundle/release/app-release.aab`
- R8 mapping: `app/build/outputs/mapping/release/mapping.txt`

## Canonical Runtime Direction

- **Application ID:** `com.anurag9000.forestrun`
- **Canonical branch:** `main`
- **Biomes:** five runtime biomes
- **Bloom target:** eight Seeds and a six-second active window
- **Input intent:** tap for a short jump, hold for a higher jump, swipe down to duck
- **Encounter invariant:** exactly one terminal outcome per entity
- **Failure flow:** run → rest summary → fade → Garden → next run
- **Ghost format:** v2 stable state codes, with read compatibility for legacy ordinal files
- **Manifest orientation:** fixed landscape pending final device/product acceptance
- **Release signing:** external Gradle properties or environment variables; credentials are never committed
